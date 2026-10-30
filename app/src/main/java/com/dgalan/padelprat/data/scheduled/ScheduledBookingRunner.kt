package com.dgalan.padelprat.data.scheduled

import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.auth.AuthRepository
import com.dgalan.padelprat.data.booking.DayTimetable
import com.dgalan.padelprat.data.booking.ReservationRepository
import com.dgalan.padelprat.data.booking.ReservationResult
import com.dgalan.padelprat.data.booking.SlotCalculator
import com.dgalan.padelprat.data.booking.SlotState
import com.dgalan.padelprat.data.booking.TimetableRepository
import com.dgalan.padelprat.data.booking.TimetableResult
import com.dgalan.padelprat.data.reservations.ReservationsRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Resultado de intentar una reserva programada, para la notificación. */
sealed interface ScheduledRunResult {
    /**
     * Reservada a [start] (o, en modo de prueba, la pista que se habría reservado). [chosen] es `false` si no era una
     * de las elegidas: ninguna de ellas estaba libre y se ha cogido otra. [start] es la hora alternativa si a la
     * principal no había ninguna pista.
     */
    data class Booked(
        val court: ScheduledCourt,
        val start: LocalTime,
        val dryRun: Boolean,
        val chosen: Boolean = true
    ) : ScheduledRunResult

    /**
     * No se ha podido reservar ninguna pista a ninguna de las horas: [times] dice qué pasó en cada una, por orden.
     * [timetableReadAt] es cuándo se leyó la parrilla tras la apertura, en el reloj del club.
     */
    data class NoCourtFree(val times: List<TimeAttempts>, val timetableReadAt: Instant) : ScheduledRunResult

    /** Ya había el máximo de reservas activas del club. */
    data object LimitReached : ScheduledRunResult

    /** No hay sesión: hay que volver a entrar en la app. */
    data object LoggedOut : ScheduledRunResult

    /** El día no se abrió en los dos minutos siguientes a la hora prevista. */
    data object NotOpened : ScheduledRunResult

    /** Se cortó al confirmar o la web pidió un paso más: hay que comprobarlo en Mis reservas. */
    data object Uncertain : ScheduledRunResult

    /** No se pudo intentar a tiempo (el móvil estaba apagado) y el partido ya ha empezado. */
    data object Missed : ScheduledRunResult

    /** Sin conexión o la web respondió algo inesperado. */
    data object Failed : ScheduledRunResult
}

/**
 * Qué pasó a una de las horas: [chosen], con cada una de las pistas elegidas, por orden, y [others], con las demás que
 * llegaron a salir libres.
 */
data class TimeAttempts(val start: LocalTime, val chosen: List<CourtAttempt>, val others: List<CourtAttempt>)

/**
 * Qué pasó con una pista que no se ha podido reservar. [tries] es cuántas pre-reservas se lanzaron; [firstAt] y [at],
 * cuándo respondió la web a la primera y a la última, en el reloj del club (`null` si no se llegó a intentar).
 */
data class CourtAttempt(
    val court: ScheduledCourt,
    val outcome: CourtOutcome,
    val at: Instant? = null,
    val tries: Int = 0,
    val firstAt: Instant? = null
)

sealed interface CourtOutcome {
    /** En la primera parrilla tras la apertura ya no salía libre (ocupada o sin tiempo para la duración). */
    data object BusyAtOpening : CourtOutcome

    /** Salía libre, pero luego dejó de estarlo (otro socio se adelantó) o ya no cabía la duración al pre-reservarla. */
    data object Taken : CourtOutcome

    /**
     * La web rechazó sin motivo todas las pre-reservas hasta [ScheduledBookingRunner.RETRY_WINDOW] y la pista seguía
     * libre: lo más probable es que aún no dejara reservar.
     */
    data object Refused : CourtOutcome

    /** La web no dejó reservarla y dio este motivo. */
    data class Rejected(val message: String) : CourtOutcome
}

/** Resultado y, si el reloj del móvil estaba desajustado más de [CLOCK_WARNING], cuánto. */
data class ScheduledRun(val result: ScheduledRunResult, val clockOffset: Duration?)

/**
 * Intenta una reserva programada en la apertura de su día (ver docs/plan-reserva-programada.md):
 * 1. comprueba la sesión y el límite de reservas del club;
 * 2. mide el desfase entre el reloj del móvil y el del club con una consulta;
 * 3. espera hasta [POLL_EARLY] antes de la apertura según el reloj del club;
 * 4. consulta el día hasta que la web lo abre, es decir, hasta que trae la parrilla y su límite de reserva
 *    (`nowUtcMaxAvailability`) llega a la hora pedida (como mucho [OPEN_TIMEOUT]): cada [FAST_POLL_INTERVAL] hasta
 *    [FAST_POLL_WINDOW] después de la apertura y luego cada [POLL_INTERVAL];
 * 5. reserva la primera pista libre a esa hora, por orden de preferencia, con el flujo normal de la app; si ninguna de
 *    las elegidas lo está, cualquier otra, en el orden de la parrilla; y si no hay ninguna, lo mismo a la hora
 *    alternativa, si la hay;
 * 6. si la web rechaza las pre-reservas sin motivo (p. ej. porque aún no deja reservar, aunque la parrilla ya salga),
 *    vuelve a leer la parrilla y lo intenta otra vez con las que sigan libres, durante [RETRY_WINDOW]: cada
 *    [FAST_POLL_INTERVAL] los primeros [FAST_POLL_WINDOW] y luego cada [POLL_INTERVAL].
 *
 * Nunca hace más de una reserva y no reintenta si el resultado es dudoso, para no duplicarla. Solo se reintenta la
 * pre-reserva rechazada, que no deja nada bloqueado.
 *
 * En modo de prueba ([dryRun]) no espera a la apertura: usa el mismo día de la semana anterior, que ya está abierto,
 * y para justo antes de la pre-reserva. Sirve para probar todo el camino sin reservar nada.
 */
@Singleton
class ScheduledBookingRunner @Inject constructor(
    private val authRepository: AuthRepository,
    private val reservationsRepository: ReservationsRepository,
    private val timetableRepository: TimetableRepository,
    private val reservationRepository: ReservationRepository
) {

    suspend fun run(booking: ScheduledBooking, dryRun: Boolean, onBookingStart: suspend () -> Unit = {}): ScheduledRun {
        if (!authRepository.isLoggedIn.first()) return ScheduledRun(ScheduledRunResult.LoggedOut, null)

        reservationsRepository.refreshActiveCount()
        if (!dryRun && (reservationsRepository.activeCount.value ?: 0) >= Syltek.MAX_ACTIVE_RESERVATIONS) {
            return ScheduledRun(ScheduledRunResult.LimitReached, null)
        }

        val offset = measureClockOffset()
        val warning = offset?.takeIf { abs(it.seconds) >= CLOCK_WARNING.seconds }
        val targetDay = if (dryRun) booking.date.minusDays(Syltek.BOOKING_OPENS_DAYS_BEFORE) else booking.date

        // La apertura según el reloj del móvil, corregido con el desfase medido
        val opensAtLocal = booking.opensAt.minus(offset ?: Duration.ZERO)
        if (!dryRun) {
            // Se empieza a consultar un poco antes: `nowUtc` viene sin milisegundos y el desfase puede quedarse corto
            // hasta un segundo. Un día aún cerrado responde sin parrilla, así que consultar antes no reserva nada
            val wait = Duration.between(Instant.now(), opensAtLocal.minus(POLL_EARLY))
            if (!wait.isNegative) delay(wait.toMillis())
        }

        // Inicio de cada hora en el día que se consulta (el de la semana anterior en modo de prueba)
        val slotStarts = booking.starts.associateWith { targetDay.atTime(it).atZone(Syltek.ZONE).toInstant() }
        val fastUntil = if (dryRun) Instant.MIN else opensAtLocal.plus(FAST_POLL_WINDOW)
        var timetable = waitUntilOpen(targetDay, slotStarts.values.max(), fastUntil)
            ?: return ScheduledRun(ScheduledRunResult.NotOpened, warning)
        val timetableReadAt = clubNow(offset)
        // Los reintentos cuentan desde que el día sale abierto, también si se lanza tarde
        val fastRetryUntil = Instant.now().plus(FAST_POLL_WINDOW)
        val retryUntil = Instant.now().plus(RETRY_WINDOW)

        val chosenIds = booking.courts.map { it.id }.toSet()
        // Por hora y pista, en el orden en que se intentan: primero la hora y, dentro de ella, la preferencia
        val tracks = LinkedHashMap<Pair<LocalTime, Int>, CourtTrack>()
        var firstRead = true
        var started = false

        while (true) {
            val slots = SlotCalculator.slots(timetable, booking.durationMinutes)
            // Si no sale ninguna de las elegidas, las demás pistas, en el orden de la parrilla
            val others = slots
                .map { it.court }
                .filter { it.id !in chosenIds }
                .map { ScheduledCourt(it.id, it.name, it.sponsor) }
            val free = mutableListOf<CourtTrack>()
            for ((time, slotStart) in slotStarts) {
                for (court in booking.courts + others) {
                    val track = tracks.getOrPut(time to court.id) { CourtTrack(court, time) }
                    val courtSlots = slots.firstOrNull { it.court.id == court.id }
                    val slot = courtSlots?.slots?.firstOrNull { it.start == slotStart }
                    when {
                        slot?.state == SlotState.Free -> if (track.final == null) free += track
                        firstRead -> track.busyAtOpening = true
                        !track.busyAtOpening -> track.taken = true
                    }
                }
            }
            firstRead = false
            if (free.isEmpty()) break

            for (track in free) {
                val chosen = track.court.id in chosenIds
                if (dryRun) {
                    return ScheduledRun(
                        ScheduledRunResult.Booked(track.court, track.time, dryRun = true, chosen),
                        warning
                    )
                }
                // A partir de aquí puede quedar hecha una reserva: se apunta por si el servicio muere a medias
                if (!started) {
                    started = true
                    onBookingStart()
                }

                val start = booking.date.atTime(track.time).atZone(Syltek.ZONE).toInstant()
                val result = reservationRepository.book(track.court.id, start, booking.durationMinutes)
                track.tried(clubNow(offset))
                when (result) {
                    is ReservationResult.Success ->
                        return ScheduledRun(
                            ScheduledRunResult.Booked(track.court, track.time, dryRun = false, chosen),
                            warning
                        )

                    // Ocupada o aún sin abrir: la siguiente parrilla lo dirá, y si sigue libre se vuelve a intentar
                    ReservationResult.SlotRefused -> Unit

                    // La pre-reserva entró, pero ya no cabe la duración: no se vuelve a intentar
                    ReservationResult.SlotUnavailable -> track.final = CourtOutcome.Taken

                    is ReservationResult.Rejected -> track.final = CourtOutcome.Rejected(result.message)

                    // Se cortó al confirmar: se mira en Mis reservas si está hecha, pero solo para el aviso. Si no
                    // aparece no se prueba otra pista, porque el listado puede tardar en mostrarla y se reservaría
                    // dos veces
                    ReservationResult.Uncertain -> return ScheduledRun(
                        if (appearsBooked(booking, track.court, track.time)) {
                            ScheduledRunResult.Booked(track.court, track.time, dryRun = false, chosen)
                        } else {
                            ScheduledRunResult.Uncertain
                        },
                        warning
                    )

                    // La web pidió un paso más: no se comprueba en el listado, donde podría salir a medias
                    ReservationResult.Incomplete -> return ScheduledRun(ScheduledRunResult.Uncertain, warning)

                    ReservationResult.SessionExpired -> return ScheduledRun(ScheduledRunResult.LoggedOut, warning)

                    ReservationResult.NetworkError, ReservationResult.UnknownError ->
                        return ScheduledRun(ScheduledRunResult.Failed, warning)
                }
            }

            if (Instant.now() >= retryUntil) break
            delay((if (Instant.now() < fastRetryUntil) FAST_POLL_INTERVAL else POLL_INTERVAL).toMillis())
            timetable = when (val result = timetableRepository.timetable(targetDay)) {
                // Una parrilla sin pistas no dice nada de las que había: se sigue con la última
                is TimetableResult.Success -> result.timetable.takeIf { it.hasSchedule } ?: timetable

                TimetableResult.SessionExpired -> return ScheduledRun(ScheduledRunResult.LoggedOut, warning)

                // Sin conexión un momento: se vuelve a probar con la última parrilla
                else -> timetable
            }
        }

        val times = booking.starts.map { time ->
            val (chosen, others) = tracks.values
                .filter { it.time == time }
                .map { it.attempt() }
                .partition { it.court.id in chosenIds }
            TimeAttempts(time, chosen, others.filter { it.outcome != CourtOutcome.BusyAtOpening })
        }
        return ScheduledRun(ScheduledRunResult.NoCourtFree(times, timetableReadAt), warning)
    }

    /** Lo que se sabe de una pista durante el intento, para el aviso. */
    private class CourtTrack(val court: ScheduledCourt, val time: LocalTime) {
        var busyAtOpening = false

        /** Salía libre y en una parrilla posterior ya no. */
        var taken = false

        /** Motivo por el que no se vuelve a intentar. */
        var final: CourtOutcome? = null
        private var tries = 0
        private var firstAt: Instant? = null
        private var lastAt: Instant? = null

        fun tried(at: Instant) {
            busyAtOpening = false
            tries++
            if (firstAt == null) firstAt = at
            lastAt = at
        }

        fun attempt(): CourtAttempt {
            val outcome = when {
                busyAtOpening -> CourtOutcome.BusyAtOpening
                else -> final ?: if (taken) CourtOutcome.Taken else CourtOutcome.Refused
            }
            return CourtAttempt(court, outcome, lastAt, tries, firstAt)
        }
    }

    /**
     * Si una reserva dudosa sale hecha en Mis reservas, a su hora, con su duración y en la pista que se estaba
     * reservando. Solo lee. Lo mira [VERIFY_ATTEMPTS] veces, por si la conexión tarda en volver.
     */
    private suspend fun appearsBooked(booking: ScheduledBooking, court: ScheduledCourt, time: LocalTime): Boolean {
        val start = booking.date.atTime(time)
        repeat(VERIFY_ATTEMPTS) {
            delay(VERIFY_DELAY.toMillis())
            val booked = reservationsRepository.bookedCourt(start, booking.durationMinutes)
            if (booked != null) return booked.name.equals(court.name, ignoreCase = true)
        }
        return false
    }

    /**
     * Diferencia entre el reloj del club y el del móvil (positiva si el móvil va atrasado), tomando el punto medio de
     * la petición. `null` si no se ha podido medir; entonces se usa el reloj del móvil tal cual.
     */
    private suspend fun measureClockOffset(): Duration? {
        val before = Instant.now()
        val result = timetableRepository.timetable(LocalDate.now(Syltek.ZONE))
        val after = Instant.now()
        val serverNow = (result as? TimetableResult.Success)?.timetable?.now ?: return null
        val localMiddle = before.plusMillis(Duration.between(before, after).toMillis() / 2)
        return Duration.between(localMiddle, serverNow)
    }

    /** La hora del club con milisegundos: la del móvil corregida con el desfase medido. */
    private fun clubNow(offset: Duration?): Instant = Instant.now().plus(offset ?: Duration.ZERO)

    /**
     * Consulta el día hasta que la web trae su parrilla y deja reservar a [start], como mucho [OPEN_TIMEOUT]: más a
     * menudo hasta [fastUntil] (hora del móvil) y luego cada [POLL_INTERVAL]. Las consultas van de una en una, nunca
     * en paralelo.
     */
    private suspend fun waitUntilOpen(day: LocalDate, start: Instant, fastUntil: Instant): DayTimetable? {
        val deadline = Instant.now().plus(OPEN_TIMEOUT)
        while (Instant.now() < deadline) {
            // Un día aún cerrado responde sin parrilla (FormatChanged); sin conexión también se sigue intentando. Con
            // la parrilla, el límite de reserva tiene que llegar a la hora pedida: si no, sus huecos se descartarían
            // como no reservables y se daría el día por perdido justo al abrir. Y tiene que traer las pistas: el día
            // puede salir abierto unos minutos antes, pero sin ninguna, y se daría por perdido igual
            when (val result = timetableRepository.timetable(day)) {
                is TimetableResult.Success -> {
                    val timetable = result.timetable
                    if (timetable.maxAvailability >= start && timetable.hasSchedule) return timetable
                }

                TimetableResult.SessionExpired -> return null

                else -> Unit
            }
            delay((if (Instant.now() < fastUntil) FAST_POLL_INTERVAL else POLL_INTERVAL).toMillis())
        }
        return null
    }

    companion object {
        /** Cuánto antes de la apertura despierta la alarma: cubre un reloj del móvil atrasado hasta 2 minutos. */
        val WAKE_UP_BEFORE: Duration = Duration.ofMinutes(2)
        val OPEN_TIMEOUT: Duration = Duration.ofMinutes(2)
        val POLL_INTERVAL: Duration = Duration.ofSeconds(1)

        /** Durante cuánto se reintenta una pre-reserva rechazada sin motivo, desde que el día sale abierto. */
        val RETRY_WINDOW: Duration = Duration.ofSeconds(30)

        /** Cuánto antes de la apertura se empieza a consultar: cubre el segundo que se pierde al medir el desfase. */
        val POLL_EARLY: Duration = Duration.ofSeconds(2)

        /** Alrededor de la apertura se consulta más a menudo, para reservar en cuanto se abra. */
        val FAST_POLL_INTERVAL: Duration = Duration.ofMillis(250)
        val FAST_POLL_WINDOW: Duration = Duration.ofSeconds(5)

        /** Comprobación en Mis reservas de un resultado dudoso. */
        const val VERIFY_ATTEMPTS = 3
        val VERIFY_DELAY: Duration = Duration.ofSeconds(3)

        /** A partir de este desfase se avisa de que el reloj del móvil está desajustado. */
        val CLOCK_WARNING: Duration = Duration.ofSeconds(30)
    }
}
