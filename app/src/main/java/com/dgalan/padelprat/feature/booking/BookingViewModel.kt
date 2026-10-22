package com.dgalan.padelprat.feature.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.booking.BOOKING_DURATIONS
import com.dgalan.padelprat.data.booking.CourtSlots
import com.dgalan.padelprat.data.booking.DayPart
import com.dgalan.padelprat.data.booking.DayTimetable
import com.dgalan.padelprat.data.booking.SlotCalculator
import com.dgalan.padelprat.data.booking.SlotState
import com.dgalan.padelprat.data.booking.TimetableRepository
import com.dgalan.padelprat.data.booking.TimetableResult
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

data class BookingUiState(
    val days: List<LocalDate>,
    val selectedDay: LocalDate,
    val dayPart: DayPart,
    /** Minutos que se quieren reservar: solo se ofrecen los huecos donde caben. */
    val durationMinutes: Int = DEFAULT_DURATION,
    val content: BookingContent = BookingContent.Loading,
    val selectedSlot: SelectedSlot? = null
)

/** Lo habitual es reservar un partido de hora y media. */
private const val DEFAULT_DURATION = 90

@HiltViewModel
class BookingViewModel @Inject constructor(private val repository: TimetableRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    /** Parrillas ya cargadas en esta sesión de la pantalla, para cambiar de día sin esperar. */
    private val timetables = mutableMapOf<LocalDate, DayTimetable>()
    private var loadJob: Job? = null

    init {
        load(_uiState.value.selectedDay)
    }

    fun onDaySelect(day: LocalDate) {
        if (day == _uiState.value.selectedDay) return
        _uiState.update { it.copy(selectedDay = day, selectedSlot = null) }
        val cached = timetables[day]
        if (cached != null) showTimetable(cached) else load(day)
    }

    fun onDayPartSelect(dayPart: DayPart) {
        _uiState.update { it.copy(dayPart = dayPart, selectedSlot = null) }
        timetables[_uiState.value.selectedDay]?.let(::showTimetable)
    }

    fun onDurationSelect(minutes: Int) {
        _uiState.update { it.copy(durationMinutes = minutes, selectedSlot = null) }
        timetables[_uiState.value.selectedDay]?.let(::showTimetable)
    }

    fun onSlotClick(courtId: Int, start: Instant) {
        val slot = SelectedSlot(courtId, start)
        _uiState.update { it.copy(selectedSlot = if (it.selectedSlot == slot) null else slot) }
    }

    /** Refrescar es volver a cargar: se ven las siluetas otra vez y, si falla, el error. */
    fun onRefresh() {
        val day = _uiState.value.selectedDay
        timetables.remove(day)
        _uiState.update { it.copy(selectedSlot = null) }
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
