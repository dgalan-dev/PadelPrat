package com.dgalan.padelprat.data.reminders

import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.scheduled.LocalDateSerializer
import com.dgalan.padelprat.data.scheduled.LocalTimeSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable

/**
 * Una próxima reserva con sus avisos, sacada del listado de Mis reservas. Solo se guarda lo necesario para poner las
 * alarmas y avisar sin conexión: la pista se pide al avisar, porque el club la puede cambiar.
 */
@Serializable
data class Reminder(
    val reservationId: Long,
    /** Fecha y hora locales del club. */
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    @Serializable(with = LocalTimeSerializer::class) val start: LocalTime,
    val durationMinutes: Int
) {
    val end: LocalTime get() = start.plusMinutes(durationMinutes.toLong())

    val startsAt: Instant get() = date.atTime(start).atZone(Syltek.ZONE).toInstant()
}

enum class ReminderKind {
    /** [MATCH_HOURS_BEFORE] horas antes del partido, salvo que caiga antes de las [MATCH_NOT_BEFORE]. */
    Match,

    /** El día anterior por la mañana: es el último día para anular sin cargos (normativa del club). */
    CancelMorning,

    /** El día anterior por la noche, el último aviso antes de que anular tenga cargos. */
    CancelEvening;

    /** Cuándo salta el aviso de [reminder], o `null` si no toca. */
    fun at(reminder: Reminder): Instant? = when (this) {
        // A los partidos a primera hora (9:00 y 9:30) no se les avisa de madrugada
        Match -> reminder.startsAt.minusSeconds(MATCH_HOURS_BEFORE * 3600)
            .takeIf { !it.atZone(Syltek.ZONE).toLocalTime().isBefore(MATCH_NOT_BEFORE) }

        CancelMorning -> reminder.date.minusDays(1).atTime(CANCEL_MORNING_AT).atZone(Syltek.ZONE).toInstant()

        CancelEvening -> reminder.date.minusDays(1).atTime(CANCEL_EVENING_AT).atZone(Syltek.ZONE).toInstant()
    }

    companion object {
        const val MATCH_HOURS_BEFORE = 3L
        val MATCH_NOT_BEFORE: LocalTime = LocalTime.of(7, 0)
        val CANCEL_MORNING_AT: LocalTime = LocalTime.of(10, 0)
        val CANCEL_EVENING_AT: LocalTime = LocalTime.of(21, 0)
    }
}

/** Lo que se guarda: los avisos de las próximas reservas y si cada tipo está activado (en Perfil). */
@Serializable
data class RemindersState(
    val reminders: List<Reminder> = emptyList(),
    val matchEnabled: Boolean = true,
    val cancelEnabled: Boolean = true
) {
    fun isEnabled(kind: ReminderKind): Boolean = when (kind) {
        ReminderKind.Match -> matchEnabled
        ReminderKind.CancelMorning, ReminderKind.CancelEvening -> cancelEnabled
    }
}
