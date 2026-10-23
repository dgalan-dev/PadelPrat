package com.dgalan.padelprat.feature.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgalan.padelprat.data.reservations.CancelResult
import com.dgalan.padelprat.data.reservations.Reservation
import com.dgalan.padelprat.data.reservations.ReservationStatus
import com.dgalan.padelprat.data.reservations.ReservationsChangeNotifier
import com.dgalan.padelprat.data.reservations.ReservationsRepository
import com.dgalan.padelprat.data.reservations.ReservationsResult
import com.dgalan.padelprat.data.scheduled.ScheduledBooking
import com.dgalan.padelprat.data.scheduled.ScheduledBookingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReservationsTab { Upcoming, Past, Cancelled }

enum class ReservationsError { Network, Unknown }

sealed interface ReservationsContent {
    data object Loading : ReservationsContent

    data class Error(val error: ReservationsError) : ReservationsContent

    /** Las reservas repartidas en los tres apartados, ya ordenadas. */
    data class Loaded(val upcoming: List<Reservation>, val past: List<Reservation>, val cancelled: List<Reservation>) :
        ReservationsContent
}

/** Resultado de anular o de cancelar una programación, que se muestra en un aviso. */
enum class CancelMessage { Cancelled, NotCancelled, Uncertain, ScheduleCancelled }

/** Diálogo de confirmación de anular. Mientras [inProgress], no se puede cerrar. */
data class CancelDialog(val reservation: Reservation, val inProgress: Boolean = false)

data class ReservationsUiState(
    val tab: ReservationsTab = ReservationsTab.Upcoming,
    val content: ReservationsContent = ReservationsContent.Loading,
    val cancelDialog: CancelDialog? = null,
    val message: CancelMessage? = null,
    /** Reservas programadas que aún no se han intentado; se muestran arriba de las próximas. */
    val scheduled: List<ScheduledBooking> = emptyList()
)

@HiltViewModel
class ReservationsViewModel @Inject constructor(
    private val repository: ReservationsRepository,
    changeNotifier: ReservationsChangeNotifier,
    private val scheduledRepository: ScheduledBookingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReservationsUiState())
    val uiState: StateFlow<ReservationsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
        // Al reservar desde la parrilla o al anular aquí, se recarga solo
        viewModelScope.launch { changeNotifier.changes.collect { load() } }
        viewModelScope.launch {
            scheduledRepository.scheduled.collect { scheduled -> _uiState.update { it.copy(scheduled = scheduled) } }
        }
    }

    fun onTabSelect(tab: ReservationsTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun onRefresh() = load()

    fun onRetry() = load()

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

    fun onMessageShow() {
        _uiState.update { it.copy(message = null) }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(content = ReservationsContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = when (val result = repository.reservations()) {
                is ReservationsResult.Success -> result.reservations.toContent()

                ReservationsResult.NetworkError -> ReservationsContent.Error(ReservationsError.Network)

                ReservationsResult.UnknownError -> ReservationsContent.Error(ReservationsError.Unknown)

                // La app ya está volviendo al login
                ReservationsResult.SessionExpired -> return@launch
            }
            _uiState.update { it.copy(content = content) }
        }
    }

    /**
     * Próximas: del bloque de próximas de la web, de la más cercana a la más lejana. Pasadas: del histórico, de la más
     * reciente a la más antigua. Anuladas: todas las anuladas, de cualquiera de los dos bloques, también de la más
     * reciente a la más antigua.
     */
    private fun List<Reservation>.toContent(): ReservationsContent.Loaded {
        val (cancelled, active) = partition { it.status == ReservationStatus.Cancelled }
        return ReservationsContent.Loaded(
            upcoming = active.filter { it.upcoming }.sortedBy { it.start },
            past = active.filterNot { it.upcoming }.sortedByDescending { it.start },
            cancelled = cancelled.sortedByDescending { it.start }
        )
    }
}
