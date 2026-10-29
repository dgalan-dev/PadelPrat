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
import com.dgalan.padelprat.data.booking.TimeSlots
import com.dgalan.padelprat.data.booking.TimetableRepository
import com.dgalan.padelprat.data.booking.TimetableResult
import com.dgalan.padelprat.data.profile.ProfileRepository
import com.dgalan.padelprat.data.reservations.ReservationsChangeNotifier
import com.dgalan.padelprat.data.reservations.ReservationsRepository
import com.dgalan.padelprat.data.scheduled.MAX_SCHEDULED_COURTS
import com.dgalan.padelprat.data.scheduled.MAX_SCHEDULE_DAYS_AHEAD
import com.dgalan.padelprat.data.scheduled.ScheduledBooking
import com.dgalan.padelprat.data.scheduled.ScheduledBookingRepository
import com.dgalan.padelprat.data.scheduled.ScheduledCourt
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
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

    /**
     * Pistas con algún hueco libre o propio en la franja elegida; vacía si no queda ninguno. [times] son los mismos
     * huecos agrupados por hora, para la vista [BookingView.Times].
     */
    data class Loaded(val courts: List<CourtSlots>, val times: List<TimeSlots>) : BookingContent

    /** Día aún no abierto en la web: en lugar de la parrilla, el formulario para programar la reserva. */
    data class Schedule(val form: ScheduleForm) : BookingContent
}

/** Aviso de un solo uso que se muestra abajo. */
sealed interface BookingMessage {
    /** Programada; se intentará reservar en [opensAt]. */
    data class Scheduled(val opensAt: Instant) : BookingMessage
}

/** Cómo se enseñan los huecos de un día abierto: tarjeta por pista o tarjeta por hora. */
enum class BookingView {
    /** La de por defecto: lo habitual es buscar a qué hora se puede jugar, más que en qué pista. */
    Times,
    Courts
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

    /** La web no deja reservar y da el motivo, que se muestra tal cual (en [ReservationStatus.Failed.message]). */
    Rejected,
    Unknown
}

sealed interface ReservationStatus {
    data object Idle : ReservationStatus

    data object Booking : ReservationStatus

    data class Success(val total: String?) : ReservationStatus

    data class Failed(val error: ReservationError, val message: String? = null) : ReservationStatus
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
    val view: BookingView = BookingView.Times,
    val content: BookingContent = BookingContent.Loading,
    val sheet: ReservationSheet? = null,
    /** Para el saludo; `null` hasta que llegan los datos del perfil. */
    val firstName: String? = null,
    /** Ya tiene el máximo de reservas activas que permite el club: se avisa y no se deja confirmar otra. */
    val reservationLimitReached: Boolean = false,
    /** Último día que ya se puede reservar; los siguientes de [days] solo se pueden programar. */
    val lastOpenDay: LocalDate,
    val message: BookingMessage? = null
) {
    fun isLocked(day: LocalDate): Boolean = day > lastOpenDay
}

/** Lo habitual es reservar un partido de hora y media. */
private const val DEFAULT_DURATION = 90

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val reservationRepository: ReservationRepository,
    changeNotifier: ReservationsChangeNotifier,
    profileRepository: ProfileRepository,
    private val reservationsRepository: ReservationsRepository,
    private val scheduledRepository: ScheduledBookingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    /** Parrillas ya cargadas en esta sesión de la pantalla, para cambiar de día sin esperar. */
    private val timetables = mutableMapOf<LocalDate, DayTimetable>()
    private var loadJob: Job? = null

    init {
        load(_uiState.value.selectedDay)
        viewModelScope.launch {
            profileRepository.profile.collect { profile -> _uiState.update { it.copy(firstName = profile?.firstName) } }
        }
        // Perfil también los pide; si ya están, no hace falta otra petición
        if (profileRepository.profile.value == null) viewModelScope.launch { profileRepository.refresh() }
        viewModelScope.launch {
            reservationsRepository.activeCount.collect { count ->
                _uiState.update { it.copy(reservationLimitReached = (count ?: 0) >= Syltek.MAX_ACTIVE_RESERVATIONS) }
            }
        }
        if (reservationsRepository.activeCount.value == null) {
            viewModelScope.launch { reservationsRepository.refreshActiveCount() }
        }
        // Si las reservas cambian (p. ej. se anula una en Mis reservas), las parrillas guardadas ya no valen. Con la
        // hoja abierta no se recarga: al cerrarla ya se recarga si hace falta.
        viewModelScope.launch {
            changeNotifier.changes.collect {
                // Al reservar o anular cambia también cuántas reservas activas hay
                launch { reservationsRepository.refreshActiveCount() }
                timetables.clear()
                if (_uiState.value.sheet == null) showSelectedDay()
            }
        }
    }

    fun onDaySelect(day: LocalDate) {
        if (day == _uiState.value.selectedDay) return
        _uiState.update { it.copy(selectedDay = day) }
        showSelectedDay()
    }

    fun onDayPartSelect(dayPart: DayPart) {
        _uiState.update { it.copy(dayPart = dayPart) }
        refreshShownDay()
    }

    fun onDurationSelect(minutes: Int) {
        _uiState.update { it.copy(durationMinutes = minutes) }
        refreshShownDay()
    }

    /** Solo cambia cómo se ve: no hace falta recalcular nada. */
    fun onViewSelect(view: BookingView) {
        _uiState.update { it.copy(view = view) }
    }

    /** Día abierto: su parrilla. Día cerrado: el formulario de programar, con la parrilla de una semana antes. */
    private fun showSelectedDay() {
        val day = _uiState.value.selectedDay
        if (_uiState.value.isLocked(day)) {
            val template = day.minusDays(Syltek.BOOKING_OPENS_DAYS_BEFORE)
            val cached = timetables[template]
            if (cached != null) showScheduleForm(day, cached) else load(template, showAs = day)
        } else {
            val cached = timetables[day]
            if (cached != null) showTimetable(cached) else load(day)
        }
    }

    /** Tras cambiar franja o duración: se recalcula lo que se ve sin volver a pedirlo. */
    private fun refreshShownDay() {
        val state = _uiState.value
        val day = state.selectedDay
        if (state.isLocked(day)) {
            timetables[day.minusDays(Syltek.BOOKING_OPENS_DAYS_BEFORE)]?.let { showScheduleForm(day, it) }
        } else {
            timetables[day]?.let(::showTimetable)
        }
    }

    fun onScheduleTimeSelect(time: LocalTime) {
        updateScheduleForm { it.copy(selectedTime = if (it.selectedTime == time) null else time) }
    }

    /** Añade la pista al final de la lista de preferencia o la quita si ya estaba. */
    fun onScheduleCourtToggle(courtId: Int) {
        updateScheduleForm { form ->
            when {
                courtId in form.selectedCourts -> form.copy(selectedCourts = form.selectedCourts - courtId)

                form.selectedCourts.size < MAX_SCHEDULED_COURTS -> form.copy(
                    selectedCourts =
                        form.selectedCourts + courtId
                )

                else -> form
            }
        }
    }

    fun onScheduleSave() {
        val form = (_uiState.value.content as? BookingContent.Schedule)?.form ?: return
        val time = form.selectedTime ?: return
        if (!form.canSave) return
        updateScheduleForm { it.copy(isSaving = true) }
        viewModelScope.launch {
            val courts = form.selectedCourts.mapNotNull { id ->
                form.courts.firstOrNull { it.id == id }?.let { ScheduledCourt(it.id, it.name, it.sponsor) }
            }
            val booking = ScheduledBooking(
                id = scheduledRepository.newId(),
                date = form.day,
                start = time,
                durationMinutes = _uiState.value.durationMinutes,
                courts = courts
            )
            scheduledRepository.add(booking)
            updateScheduleForm { it.copy(isSaving = false, selectedTime = null, selectedCourts = emptyList()) }
            _uiState.update { it.copy(message = BookingMessage.Scheduled(booking.opensAt)) }
        }
    }

    fun onMessageShow() {
        _uiState.update { it.copy(message = null) }
    }

    private fun updateScheduleForm(transform: (ScheduleForm) -> ScheduleForm) {
        _uiState.update { state ->
            val content = state.content as? BookingContent.Schedule ?: return@update state
            state.copy(content = BookingContent.Schedule(transform(content.form)))
        }
    }

    private fun showScheduleForm(day: LocalDate, template: DayTimetable) {
        _uiState.update { state ->
            if (state.selectedDay != day) return@update state
            val previous = (state.content as? BookingContent.Schedule)?.form
            val opensAt = day.minusDays(Syltek.BOOKING_OPENS_DAYS_BEFORE).atTime(Syltek.BOOKING_OPENS_AT)
                .atZone(Syltek.ZONE).toInstant()
            val form = ScheduleForm.from(day, opensAt, template, state.dayPart, state.durationMinutes, previous)
            state.copy(content = BookingContent.Schedule(form))
        }
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
        val state = _uiState.value
        val sheet = state.sheet ?: return
        if (sheet.status == ReservationStatus.Booking || sheet.status is ReservationStatus.Success) return
        if (state.reservationLimitReached) return
        updateSheet { it.copy(status = ReservationStatus.Booking) }

        viewModelScope.launch {
            val status = when (
                val result = reservationRepository.book(sheet.court.id, sheet.slot.start, sheet.durationMinutes)
            ) {
                is ReservationResult.Success -> ReservationStatus.Success(result.total)

                ReservationResult.SlotUnavailable, ReservationResult.SlotRefused ->
                    ReservationStatus.Failed(ReservationError.SlotUnavailable)

                ReservationResult.NetworkError -> ReservationStatus.Failed(ReservationError.Network)

                ReservationResult.Uncertain -> ReservationStatus.Failed(ReservationError.Uncertain)

                ReservationResult.Incomplete -> ReservationStatus.Failed(ReservationError.Incomplete)

                is ReservationResult.Rejected -> ReservationStatus.Failed(ReservationError.Rejected, result.message)

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

            // Ni sin conexión ni con un rechazo del club ha cambiado nada en la parrilla
            is ReservationStatus.Failed ->
                status.error != ReservationError.Network && status.error != ReservationError.Rejected

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
        timetables.remove(day.minusDays(Syltek.BOOKING_OPENS_DAYS_BEFORE))
        showSelectedDay()
    }

    fun onRetry() = showSelectedDay()

    /** Carga la parrilla de [day]. Si [showAs] es un día cerrado, se usa como plantilla de su formulario. */
    private fun load(day: LocalDate, showAs: LocalDate = day) {
        loadJob?.cancel()
        _uiState.update { it.copy(content = BookingContent.Loading) }
        loadJob = viewModelScope.launch {
            when (val result = repository.timetable(day)) {
                is TimetableResult.Success -> {
                    timetables[day] = result.timetable
                    updateDays(result.timetable)
                    if (showAs == day) showTimetable(result.timetable) else showScheduleForm(showAs, result.timetable)
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
                .map { court ->
                    court.copy(
                        slots = court.slots.filter { DayPart.of(it.time) == state.dayPart },
                        mine = court.mine.filter { DayPart.of(it.time) == state.dayPart }
                    )
                }
                .filter { court -> court.slots.any { it.state == SlotState.Free } || court.mine.isNotEmpty() }
            state.copy(content = BookingContent.Loaded(courts, SlotCalculator.byTime(courts)))
        }
    }

    private fun showError(error: BookingError) {
        _uiState.update { it.copy(content = BookingContent.Error(error)) }
    }

    /**
     * Los días van de hoy hasta [MAX_SCHEDULE_DAYS_AHEAD]: los abiertos (hasta el último que marca el servidor) se
     * reservan y los siguientes se programan.
     */
    private fun updateDays(timetable: DayTimetable) {
        val today = timetable.now.atZone(Syltek.ZONE).toLocalDate()
        val lastOpenDay = timetable.maxAvailability.atZone(Syltek.ZONE).toLocalDate()
        _uiState.update { it.copy(days = daysFrom(today), lastOpenDay = lastOpenDay) }
    }

    private companion object {
        fun daysFrom(today: LocalDate): List<LocalDate> = (0..MAX_SCHEDULE_DAYS_AHEAD).map(today::plusDays)

        /**
         * Siempre se abre en la tarde, que es cuando más se juega. Hasta la primera respuesta del servidor, el último
         * día abierto se estima con la regla del club (a partir de las 9:00 se abre el día de dentro de 7 días).
         */
        fun initialState(): BookingUiState {
            val now = ZonedDateTime.now(Syltek.ZONE)
            val today = now.toLocalDate()
            val opened = !now.toLocalTime().isBefore(Syltek.BOOKING_OPENS_AT)
            return BookingUiState(
                days = daysFrom(today),
                selectedDay = today,
                dayPart = DayPart.Afternoon,
                lastOpenDay = today.plusDays(
                    if (opened) {
                        Syltek.BOOKING_OPENS_DAYS_BEFORE
                    } else {
                        Syltek.BOOKING_OPENS_DAYS_BEFORE -
                            1
                    }
                )
            )
        }
    }
}
