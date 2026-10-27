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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Resultado de intentar una reserva programada, para la notificación. */
sealed interface ScheduledRunResult {
    /** Reservada (o, en modo de prueba, la pista que se habría reservado). */
    data class Booked(val court: ScheduledCourt, val dryRun: Boolean) : ScheduledRunResult

    /** Ninguna de las pistas elegidas estaba libre a esa hora. */
    data object NoCourtFree : ScheduledRunResult

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

/** Resultado y, si el reloj del móvil estaba desajustado más de [CLOCK_WARNING], cuánto. */
data class ScheduledRun(val result: ScheduledRunResult, val clockOffset: Duration?)

/**
 * Intenta una reserva programada en la apertura de su día (ver docs/plan-reserva-programada.md):
 * 1. comprueba la sesión y el límite de reservas del club;
 * 2. mide el desfase entre el reloj del móvil y el del club con una consulta;
 * 3. espera hasta [POLL_EARLY] antes de la apertura según el reloj del club;
 * 4. consulta el día hasta que la web lo abre (como mucho [OPEN_TIMEOUT]): cada [FAST_POLL_INTERVAL] hasta
 *    [FAST_POLL_WINDOW] después de la apertura y luego cada [POLL_INTERVAL];
 * 5. reserva la primera pista libre a esa hora, por orden de preferencia, con el flujo normal de la app.
 *
 * Nunca hace más de una reserva y no reintenta si el resultado es dudoso, para no duplicarla.
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

        val fastUntil = if (dryRun) Instant.MIN else opensAtLocal.plus(FAST_POLL_WINDOW)
        val timetable = waitUntilOpen(targetDay, fastUntil)
            ?: return ScheduledRun(ScheduledRunResult.NotOpened, warning)
        val slots = SlotCalculator.slots(timetable, booking.durationMinutes)
        val start = booking.date.atTime(booking.start).atZone(Syltek.ZONE).toInstant()
        val dryRunStart = targetDay.atTime(booking.start).atZone(Syltek.ZONE).toInstant()

        for (court in booking.courts) {
            val slotStart = if (dryRun) dryRunStart else start
            val slot = slots.firstOrNull { it.court.id == court.id }?.slots?.firstOrNull { it.start == slotStart }
            if (slot?.state != SlotState.Free) continue
            if (dryRun) return ScheduledRun(ScheduledRunResult.Booked(court, dryRun = true), warning)
            // A partir de aquí puede quedar hecha una reserva: se apunta por si el servicio muere a medias
            onBookingStart()

            when (reservationRepository.book(court.id, start, booking.durationMinutes)) {
                is ReservationResult.Success -> return ScheduledRun(ScheduledRunResult.Booked(court, false), warning)

                // Otro socio se adelantó o la web no deja esa pista: a por la siguiente
                ReservationResult.SlotUnavailable, is ReservationResult.Rejected -> continue

                // Se cortó al confirmar: se mira en Mis reservas si está hecha, pero solo para el aviso. Si no aparece
                // no se prueba otra pista, porque el listado puede tardar en mostrarla y se reservaría dos veces
                ReservationResult.Uncertain -> return ScheduledRun(
                    if (appearsBooked(booking, court)) {
                        ScheduledRunResult.Booked(court, dryRun = false)
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
        return ScheduledRun(ScheduledRunResult.NoCourtFree, warning)
    }

    /**
     * Si una reserva dudosa sale hecha en Mis reservas, a su hora, con su duración y en la pista que se estaba
     * reservando. Solo lee. Lo mira [VERIFY_ATTEMPTS] veces, por si la conexión tarda en volver.
     */
    private suspend fun appearsBooked(booking: ScheduledBooking, court: ScheduledCourt): Boolean {
        val start = booking.date.atTime(booking.start)
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

    /**
     * Consulta el día hasta que la web trae su parrilla, como mucho [OPEN_TIMEOUT]: más a menudo hasta [fastUntil]
     * (hora del móvil) y luego cada [POLL_INTERVAL]. Las consultas van de una en una, nunca en paralelo.
     */
    private suspend fun waitUntilOpen(day: LocalDate, fastUntil: Instant): DayTimetable? {
        val deadline = Instant.now().plus(OPEN_TIMEOUT)
        while (Instant.now() < deadline) {
            // Un día aún cerrado responde sin parrilla (UnknownError); sin conexión también se sigue intentando
            when (val result = timetableRepository.timetable(day)) {
                is TimetableResult.Success -> return result.timetable
                TimetableResult.SessionExpired -> return null
                else -> delay((if (Instant.now() < fastUntil) FAST_POLL_INTERVAL else POLL_INTERVAL).toMillis())
            }
        }
        return null
    }

    companion object {
        /** Cuánto antes de la apertura despierta la alarma: cubre un reloj del móvil atrasado hasta 2 minutos. */
        val WAKE_UP_BEFORE: Duration = Duration.ofMinutes(2)
        val OPEN_TIMEOUT: Duration = Duration.ofMinutes(2)
        val POLL_INTERVAL: Duration = Duration.ofSeconds(1)

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
