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

    /** Sin conexión o la web respondió algo inesperado. */
    data object Failed : ScheduledRunResult
}

/** Resultado y, si el reloj del móvil estaba desajustado más de [CLOCK_WARNING], cuánto. */
data class ScheduledRun(val result: ScheduledRunResult, val clockOffset: Duration?)

/**
 * Intenta una reserva programada en la apertura de su día (ver docs/plan-reserva-programada.md):
 * 1. comprueba la sesión y el límite de reservas del club;
 * 2. mide el desfase entre el reloj del móvil y el del club con una consulta;
 * 3. espera a la hora de apertura según el reloj del club;
 * 4. consulta el día cada segundo hasta que la web lo abre (como mucho [OPEN_TIMEOUT]);
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

    suspend fun run(booking: ScheduledBooking, dryRun: Boolean): ScheduledRun {
        if (!authRepository.isLoggedIn.first()) return ScheduledRun(ScheduledRunResult.LoggedOut, null)

        reservationsRepository.refreshActiveCount()
        if (!dryRun && (reservationsRepository.activeCount.value ?: 0) >= Syltek.MAX_ACTIVE_RESERVATIONS) {
            return ScheduledRun(ScheduledRunResult.LimitReached, null)
        }

        val offset = measureClockOffset()
        val warning = offset?.takeIf { abs(it.seconds) >= CLOCK_WARNING.seconds }
        val targetDay = if (dryRun) booking.date.minusDays(Syltek.BOOKING_OPENS_DAYS_BEFORE) else booking.date

        if (!dryRun) {
            // Hasta la apertura según el reloj del club (el del móvil corregido con el desfase medido)
            val serverNow = Instant.now().plus(offset ?: Duration.ZERO)
            val wait = Duration.between(serverNow, booking.opensAt)
            if (!wait.isNegative) delay(wait.toMillis())
        }

        val timetable = waitUntilOpen(targetDay) ?: return ScheduledRun(ScheduledRunResult.NotOpened, warning)
        val slots = SlotCalculator.slots(timetable, booking.durationMinutes)
        val start = booking.date.atTime(booking.start).atZone(Syltek.ZONE).toInstant()
        val dryRunStart = targetDay.atTime(booking.start).atZone(Syltek.ZONE).toInstant()

        for (court in booking.courts) {
            val slotStart = if (dryRun) dryRunStart else start
            val slot = slots.firstOrNull { it.court.id == court.id }?.slots?.firstOrNull { it.start == slotStart }
            if (slot?.state != SlotState.Free) continue
            if (dryRun) return ScheduledRun(ScheduledRunResult.Booked(court, dryRun = true), warning)

            when (reservationRepository.book(court.id, start, booking.durationMinutes)) {
                is ReservationResult.Success -> return ScheduledRun(ScheduledRunResult.Booked(court, false), warning)

                // Otro socio se adelantó o la web no deja esa pista: a por la siguiente
                ReservationResult.SlotUnavailable, is ReservationResult.Rejected -> continue

                // Puede que esté hecha: no se prueba otra pista para no reservar dos veces
                ReservationResult.Uncertain, ReservationResult.Incomplete ->
                    return ScheduledRun(ScheduledRunResult.Uncertain, warning)

                ReservationResult.SessionExpired -> return ScheduledRun(ScheduledRunResult.LoggedOut, warning)

                ReservationResult.NetworkError, ReservationResult.UnknownError ->
                    return ScheduledRun(ScheduledRunResult.Failed, warning)
            }
        }
        return ScheduledRun(ScheduledRunResult.NoCourtFree, warning)
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

    /** Consulta el día cada segundo hasta que la web trae su parrilla, como mucho [OPEN_TIMEOUT]. */
    private suspend fun waitUntilOpen(day: LocalDate): DayTimetable? {
        val deadline = Instant.now().plus(OPEN_TIMEOUT)
        while (Instant.now() < deadline) {
            // Un día aún cerrado responde sin parrilla (UnknownError); sin conexión también se sigue intentando
            when (val result = timetableRepository.timetable(day)) {
                is TimetableResult.Success -> return result.timetable
                TimetableResult.SessionExpired -> return null
                else -> delay(POLL_INTERVAL.toMillis())
            }
        }
        return null
    }

    companion object {
        /** Cuánto antes de la apertura despierta la alarma: cubre un reloj del móvil atrasado hasta 2 minutos. */
        val WAKE_UP_BEFORE: Duration = Duration.ofMinutes(2)
        val OPEN_TIMEOUT: Duration = Duration.ofMinutes(2)
        val POLL_INTERVAL: Duration = Duration.ofSeconds(1)

        /** A partir de este desfase se avisa de que el reloj del móvil está desajustado. */
        val CLOCK_WARNING: Duration = Duration.ofSeconds(30)
    }
}
