package com.dgalan.padelprat.data.scheduled

import com.dgalan.padelprat.core.network.Syltek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable

/**
 * Reserva programada: se intentará hacer sola cuando la web abra su día (ver docs/plan-reserva-programada.md).
 * Hasta entonces no es una reserva: no aparece en la web.
 */
@Serializable
data class ScheduledBooking(
    val id: String,
    /** Fecha y hora locales del club. */
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    @Serializable(with = LocalTimeSerializer::class) val start: LocalTime,
    val durationMinutes: Int,
    /** Hasta 3 pistas, por orden de preferencia: se prueba la primera y, si no está libre, la siguiente. */
    val courts: List<ScheduledCourt>,
    /**
     * Se ha empezado a reservar (justo antes de la primera pre-reserva). Si el servicio muere a partir de aquí, no se
     * vuelve a intentar sola: la reserva podría estar ya hecha y se duplicaría.
     */
    val bookingStarted: Boolean = false
) {
    val end: LocalTime get() = start.plusMinutes(durationMinutes.toLong())

    /** Cuándo abre la web este día: a las 9:00 de Madrid, siete días antes. */
    val opensAt: Instant
        get() = date.minusDays(Syltek.BOOKING_OPENS_DAYS_BEFORE).atTime(Syltek.BOOKING_OPENS_AT)
            .atZone(Syltek.ZONE).toInstant()

    /** Inicio del partido, en el reloj real. */
    val startsAt: Instant get() = date.atTime(start).atZone(Syltek.ZONE).toInstant()
}

@Serializable
data class ScheduledCourt(val id: Int, val name: String, val sponsor: String?)

/** Máximo de pistas por programación. */
const val MAX_SCHEDULED_COURTS = 3

/** Hasta cuántos días por delante se puede programar. */
const val MAX_SCHEDULE_DAYS_AHEAD = 14L
