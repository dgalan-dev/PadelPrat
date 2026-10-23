package com.dgalan.padelprat.feature.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.booking.BOOKING_DURATIONS
import com.dgalan.padelprat.data.booking.Court
import com.dgalan.padelprat.data.booking.CourtSlots
import com.dgalan.padelprat.data.booking.DayPart
import com.dgalan.padelprat.data.booking.DayTimetable
import com.dgalan.padelprat.data.booking.ReservationRepository
import com.dgalan.padelprat.data.booking.ReservationResult
import com.dgalan.padelprat.data.booking.Slot
import com.dgalan.padelprat.data.booking.SlotCalculator
import com.dgalan.padelprat.data.booking.SlotState
import com.dgalan.padelprat.data.booking.TimetableRepository
import com.dgalan.padelprat.data.booking.TimetableResult
import com.dgalan.padelprat.data.reservations.ReservationsChangeNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BookingError { Network, Unknown }

sealed interface BookingContent {
    data object Loading : BookingContent

    data class Error(val error: BookingError) : BookingContent

    /** Pistas con algún hueco libre o propio en la franja elegida; vacía si no queda ninguno. */
    data class Loaded(val courts: List<CourtSlots>) : BookingContent
}

/** Hueco elegido por el usuario. */
data class SelectedSlot(val courtId: Int, val start: Instant)

enum class ReservationError {
    /** Otro socio lo ha cogido o ya no cabe la duración. */
    SlotUnavailable,

    /** Sin conexión antes de confirmar: no se ha reservado nada. */
    Network,

    /** Se cortó al confirmar: no se sabe si se ha reservado. No se ofrece reintentar. */
    Uncertain,

    /** La web pide un paso más (p. ej. elegir cómo pagar) que la app aún no hace: la reserva no está hecha. */
    Incomplete,
    Unknown
}

sealed interface ReservationStatus {
    data object Idle : ReservationStatus

    data object Booking : ReservationStatus

    data class Success(val total: String?) : ReservationStatus

    data class Failed(val error: ReservationError) : ReservationStatus
}

/** Hoja de reserva abierta sobre un hueco libre. Abrirla no bloquea nada: solo se reserva al confirmar. */
data class ReservationSheet(
    val court: Court,
    val slot: Slot,
    val durationMinutes: Int,
    val status: ReservationStatus = ReservationStatus.Idle
) {
    val selectedSlot: SelectedSlot get() = SelectedSlot(court.id, slot.start)
}

data class BookingUiState(
    val days: List<LocalDate>,
    val selectedDay: LocalDate,
    val dayPart: DayPart,
    /** Minutos que se quieren reservar: solo se ofrecen los huecos donde caben. */
    val durationMinutes: Int = DEFAULT_DURATION,
    val content: BookingContent = BookingContent.Loading,
    val sheet: ReservationSheet? = null
)

/** Lo habitual es reservar un partido de hora y media. */
private const val DEFAULT_DURATION = 90

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val reservationRepository: ReservationRepository,
    changeNotifier: ReservationsChangeNotifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    /** Parrillas ya cargadas en esta sesión de la pantalla, para cambiar de día sin esperar. */
    private val timetables = mutableMapOf<LocalDate, DayTimetable>()
    private var loadJob: Job? = null

    init {
        load(_uiState.value.selectedDay)
        // Si las reservas cambian (p. ej. se anula una en Mis reservas), las parrillas guardadas ya no valen. Con la
        // hoja abierta no se recarga: al cerrarla ya se recarga si hace falta.
        viewModelScope.launch {
            changeNotifier.changes.collect {
                timetables.clear()
                if (_uiState.value.sheet == null) load(_uiState.value.selectedDay)
            }
        }
    }

    fun onDaySelect(day: LocalDate) {
        if (day == _uiState.value.selectedDay) return
        _uiState.update { it.copy(selectedDay = day) }
        val cached = timetables[day]
        if (cached != null) showTimetable(cached) else load(day)
    }

    fun onDayPartSelect(dayPart: DayPart) {
        _uiState.update { it.copy(dayPart = dayPart) }
        timetables[_uiState.value.selectedDay]?.let(::showTimetable)
    }

    fun onDurationSelect(minutes: Int) {
        _uiState.update { it.copy(durationMinutes = minutes) }
        timetables[_uiState.value.selectedDay]?.let(::showTimetable)
    }

    fun onSlotClick(courtId: Int, start: Instant) {
        _uiState.update { state ->
            val courtSlots = (state.content as? BookingContent.Loaded)?.courts?.firstOrNull { it.court.id == courtId }
            val slot = courtSlots?.slots?.firstOrNull { it.start == start && it.state == SlotState.Free }
            if (courtSlots == null || slot == null) {
                state
            } else {
                state.copy(sheet = ReservationSheet(courtSlots.court, slot, state.durationMinutes))
            }
        }
    }

    /** Aquí sí se reserva de verdad: pre-reserva y confirmación seguidas. Nunca se reintenta solo. */
    fun onConfirmClick() {
        val sheet = _uiState.value.sheet ?: return
        if (sheet.status == ReservationStatus.Booking || sheet.status is ReservationStatus.Success) return
        updateSheet { it.copy(status = ReservationStatus.Booking) }

        viewModelScope.launch {
            val status = when (
                val result = reservationRepository.book(sheet.court.id, sheet.slot.start, sheet.durationMinutes)
            ) {
                is ReservationResult.Success -> ReservationStatus.Success(result.total)

                ReservationResult.SlotUnavailable -> ReservationStatus.Failed(ReservationError.SlotUnavailable)

                ReservationResult.NetworkError -> ReservationStatus.Failed(ReservationError.Network)

                ReservationResult.Uncertain -> ReservationStatus.Failed(ReservationError.Uncertain)

                ReservationResult.Incomplete -> ReservationStatus.Failed(ReservationError.Incomplete)

                ReservationResult.UnknownError -> ReservationStatus.Failed(ReservationError.Unknown)

                // La app ya está volviendo al login
                ReservationResult.SessionExpired -> null
            }
            if (status == null) _uiState.update { it.copy(sheet = null) } else updateSheet { it.copy(status = status) }
        }
    }

    /**
     * Cerrar la hoja. Mientras se reserva no se puede. Si la parrilla ha podido cambiar (reserva hecha, hueco ya
     * cogido o resultado dudoso), se recarga el día para ver el estado real.
     */
    fun onSheetDismiss() {
        val sheet = _uiState.value.sheet ?: return
        if (sheet.status == ReservationStatus.Booking) return
        _uiState.update { it.copy(sheet = null) }
        val gridMayHaveChanged = when (val status = sheet.status) {
            is ReservationStatus.Success -> true
            is ReservationStatus.Failed -> status.error != ReservationError.Network
            else -> false
        }
        if (gridMayHaveChanged) onRefresh()
    }

    private fun updateSheet(transform: (ReservationSheet) -> ReservationSheet) {
        _uiState.update { state -> state.copy(sheet = state.sheet?.let(transform)) }
    }

    /** Refrescar es volver a cargar: se ven las siluetas otra vez y, si falla, el error. */
    fun onRefresh() {
        val day = _uiState.value.selectedDay
        timetables.remove(day)
        load(day)
    }

    fun onRetry() {
        load(_uiState.value.selectedDay)
    }

    private fun load(day: LocalDate) {
        loadJob?.cancel()
        _uiState.update { it.copy(content = BookingContent.Loading) }
        loadJob = viewModelScope.launch {
            when (val result = repository.timetable(day)) {
                is TimetableResult.Success -> {
                    timetables[day] = result.timetable
                    updateDays(result.timetable)
                    showTimetable(result.timetable)
                }

                TimetableResult.NetworkError -> showError(BookingError.Network)

                TimetableResult.UnknownError -> showError(BookingError.Unknown)

                // La app ya está volviendo al login
                TimetableResult.SessionExpired -> Unit
            }
        }
    }

    private fun showTimetable(timetable: DayTimetable) {
        _uiState.update { state ->
            if (timetable.date != state.selectedDay) return@update state
            val courts = SlotCalculator.slots(timetable, state.durationMinutes)
                .map { court -> court.copy(slots = court.slots.filter { DayPart.of(it.time) == state.dayPart }) }
                .filter { court -> court.slots.any { it.state == SlotState.Free || it.state == SlotState.Mine } }
            state.copy(content = BookingContent.Loaded(courts))
        }
    }

    private fun showError(error: BookingError) {
        _uiState.update { it.copy(content = BookingContent.Error(error)) }
    }

    /** Los días que se pueden reservar van de hoy hasta el límite que marca el servidor. */
    private fun updateDays(timetable: DayTimetable) {
        val today = timetable.now.atZone(Syltek.ZONE).toLocalDate()
        val lastDay = timetable.maxAvailability.atZone(Syltek.ZONE).toLocalDate()
        val days = generateSequence(today) { it.plusDays(1) }.takeWhile { it <= lastDay }.toList()
        if (days.isNotEmpty()) _uiState.update { it.copy(days = days) }
    }

    private companion object {
        /** Antes de la primera respuesta se muestran 8 días, lo habitual (hoy y los 7 siguientes). */
        const val DEFAULT_DAYS = 8L

        /** Siempre se abre en la tarde, que es cuando más se juega. */
        fun initialState(): BookingUiState {
            val today = LocalDate.now(Syltek.ZONE)
            return BookingUiState(
                days = (0 until DEFAULT_DAYS).map(today::plusDays),
                selectedDay = today,
                dayPart = DayPart.Afternoon
            )
        }
    }
}
