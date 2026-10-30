package com.dgalan.padelprat.feature.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.friends.FriendReservations
import com.dgalan.padelprat.data.friends.SharedReservationsRepository
import com.dgalan.padelprat.data.reservations.CancelResult
import com.dgalan.padelprat.data.reservations.Reservation
import com.dgalan.padelprat.data.reservations.ReservationStatus
import com.dgalan.padelprat.data.reservations.ReservationsChangeNotifier
import com.dgalan.padelprat.data.reservations.ReservationsRepository
import com.dgalan.padelprat.data.reservations.ReservationsResult
import com.dgalan.padelprat.data.scheduled.ScheduledBooking
import com.dgalan.padelprat.data.scheduled.ScheduledBookingAlarms
import com.dgalan.padelprat.data.scheduled.ScheduledBookingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReservationsTab { Upcoming, Past, Cancelled }

enum class ReservationsError { Network, Unknown, WebChanged }

/** Qué reservas se ven en Próximas cuando hay reservas compartidas con el amigo. */
enum class OwnerFilter { All, Mine, Friend }

sealed interface ReservationsContent {
    data object Loading : ReservationsContent

    data class Error(val error: ReservationsError) : ReservationsContent

    /**
     * Las reservas repartidas en los tres apartados, ya ordenadas. Con [savedAt], son la copia guardada (sin
     * conexión) de esa hora, y no se pueden anular.
     */
    data class Loaded(
        val upcoming: List<Reservation>,
        val past: List<Reservation>,
        val cancelled: List<Reservation>,
        val savedAt: Instant? = null
    ) : ReservationsContent
}

/** Resultado de anular o de cancelar una programación, que se muestra en un aviso. */
enum class CancelMessage { Cancelled, NotCancelled, Uncertain, ScheduleCancelled, ScheduleTestQueued }

private const val TEST_DELAY_SECONDS = 15L

/** Diálogo de confirmación de anular. Mientras [inProgress], no se puede cerrar. */
data class CancelDialog(val reservation: Reservation, val inProgress: Boolean = false)

data class ReservationsUiState(
    val tab: ReservationsTab = ReservationsTab.Upcoming,
    val content: ReservationsContent = ReservationsContent.Loading,
    val cancelDialog: CancelDialog? = null,
    val message: CancelMessage? = null,
    /** Reservas programadas que aún no se han intentado; se muestran arriba de las próximas. */
    val scheduled: List<ScheduledBooking> = emptyList(),
    /** Las próximas del amigo; `null` si las reservas compartidas no están activadas (o no se han podido leer). */
    val friend: FriendReservations? = null,
    val ownerFilter: OwnerFilter = OwnerFilter.All
)

@HiltViewModel
class ReservationsViewModel @Inject constructor(
    private val repository: ReservationsRepository,
    changeNotifier: ReservationsChangeNotifier,
    private val scheduledRepository: ScheduledBookingRepository,
    private val scheduledAlarms: ScheduledBookingAlarms,
    private val sharedReservations: SharedReservationsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReservationsUiState())
    val uiState: StateFlow<ReservationsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var friendJob: Job? = null

    init {
        load()
        observeFriend()
        // Al reservar desde la parrilla o al anular aquí, se recarga solo
        viewModelScope.launch { changeNotifier.changes.collect { load() } }
        viewModelScope.launch {
            scheduledRepository.scheduled.collect { scheduled -> _uiState.update { it.copy(scheduled = scheduled) } }
        }
    }

    fun onTabSelect(tab: ReservationsTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun onOwnerFilterSelect(filter: OwnerFilter) {
        _uiState.update { it.copy(ownerFilter = filter) }
    }

    fun onRefresh() {
        load()
        observeFriend()
    }

    fun onRetry() = onRefresh()

    /** Se vuelve a empezar al refrescar, por si entretanto se ha activado o se ha dado el alta en la consola. */
    private fun observeFriend() {
        friendJob?.cancel()
        friendJob = viewModelScope.launch {
            sharedReservations.friendReservations.collect { friend ->
                _uiState.update { state ->
                    state.copy(
                        friend = friend,
                        ownerFilter = if (friend == null) OwnerFilter.All else state.ownerFilter
                    )
                }
            }
        }
    }

    fun onCancelClick(reservation: Reservation) {
        _uiState.update { it.copy(cancelDialog = CancelDialog(reservation)) }
    }

    fun onCancelDismiss() {
        _uiState.update { state ->
            if (state.cancelDialog?.inProgress ==
                true
            ) {
                state
            } else {
                state.copy(cancelDialog = null)
            }
        }
    }

    /** Anula de verdad: una sola petición, sin reintentos, y el resultado se comprueba en el listado. */
    fun onCancelConfirm() {
        val dialog = _uiState.value.cancelDialog ?: return
        if (dialog.inProgress) return
        _uiState.update { it.copy(cancelDialog = dialog.copy(inProgress = true)) }

        viewModelScope.launch {
            val message = when (repository.cancel(dialog.reservation.id)) {
                CancelResult.Cancelled -> CancelMessage.Cancelled

                CancelResult.NotCancelled -> CancelMessage.NotCancelled

                CancelResult.Uncertain -> CancelMessage.Uncertain

                // La app ya está volviendo al login
                CancelResult.SessionExpired -> null
            }
            // El listado se recarga solo con el aviso de cambios del repositorio
            _uiState.update { it.copy(cancelDialog = null, message = message) }
        }
    }

    /** Quita la programación: todavía no hay reserva, así que no hay nada que anular en la web. */
    fun onScheduleCancel(id: String) {
        viewModelScope.launch {
            scheduledRepository.remove(id)
            _uiState.update { it.copy(message = CancelMessage.ScheduleCancelled) }
        }
    }

    /**
     * Solo en debug: lanza la programación en unos segundos en modo de prueba (con el día abierto de la semana
     * anterior y sin llegar a reservar), para probar la alarma, el servicio y la notificación.
     */
    fun onScheduleTest(id: String) {
        val booking = _uiState.value.scheduled.firstOrNull { it.id == id } ?: return
        scheduledAlarms.scheduleDryRun(booking, Instant.now().plusSeconds(TEST_DELAY_SECONDS))
        _uiState.update { it.copy(message = CancelMessage.ScheduleTestQueued) }
    }

    fun onMessageShow() {
        _uiState.update { it.copy(message = null) }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(content = ReservationsContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = when (val result = repository.reservations()) {
                is ReservationsResult.Success -> result.reservations.toContent()

                is ReservationsResult.NetworkError ->
                    result.cached?.let { cached ->
                        cached.reservations.map { it.copy(canCancel = false) }.toContent()
                            .copy(savedAt = cached.savedAt)
                    } ?: ReservationsContent.Error(ReservationsError.Network)

                ReservationsResult.UnknownError -> ReservationsContent.Error(ReservationsError.Unknown)

                ReservationsResult.FormatChanged -> ReservationsContent.Error(ReservationsError.WebChanged)

                // La app ya está volviendo al login
                ReservationsResult.SessionExpired -> return@launch
            }
            _uiState.update { it.copy(content = content) }
        }
    }

    /**
     * Próximas: del bloque de próximas de la web, de la más cercana a la más lejana. Pasadas: del histórico, de la más
     * reciente a la más antigua. Anuladas: todas las anuladas, de cualquiera de los dos bloques, también de la más
     * reciente a la más antigua. Las próximas que ya han terminado (en una copia antigua) van a Pasadas.
     */
    private fun List<Reservation>.toContent(): ReservationsContent.Loaded {
        val now = LocalDateTime.now(Syltek.ZONE)
        val (cancelled, active) = partition { it.status == ReservationStatus.Cancelled }
        val (upcoming, past) = active.partition { it.upcoming && it.end > now }
        return ReservationsContent.Loaded(
            upcoming = upcoming.sortedBy { it.start },
            past = past.sortedByDescending { it.start },
            cancelled = cancelled.sortedByDescending { it.start }
        )
    }
}
