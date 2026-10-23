package com.dgalan.padelprat.feature.booking

import com.dgalan.padelprat.data.booking.Court
import com.dgalan.padelprat.data.booking.DayPart
import com.dgalan.padelprat.data.booking.DayTimetable
import com.dgalan.padelprat.data.scheduled.MAX_SCHEDULED_COURTS
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Formulario para programar la reserva de un día que todavía no se puede reservar. Las pistas y las horas salen de la
 * parrilla del mismo día de la semana anterior ([DayTimetable] de `day − 7`), que siempre está abierta y tiene el
 * mismo horario (p. ej. los domingos cierra antes). Es orientativo: las reservas reales se ven al abrir el día.
 */
data class ScheduleForm(
    val day: LocalDate,
    val opensAt: Instant,
    val courts: List<Court>,
    /** Inicios posibles en la franja elegida donde cabe la duración elegida antes del cierre. */
    val times: List<LocalTime>,
    val selectedTime: LocalTime? = null,
    /** Ids de las pistas elegidas, por orden de preferencia (como mucho [MAX_SCHEDULED_COURTS]). */
    val selectedCourts: List<Int> = emptyList(),
    val isSaving: Boolean = false
) {
    val canSave: Boolean get() = selectedTime != null && selectedCourts.isNotEmpty() && !isSaving

    companion object {
        fun from(
            day: LocalDate,
            opensAt: Instant,
            template: DayTimetable,
            dayPart: DayPart,
            durationMinutes: Int,
            previous: ScheduleForm? = null
        ): ScheduleForm {
            val times = template.courts
                .flatMap { court -> court.startsFitting(durationMinutes) }
                .filter { DayPart.of(it) == dayPart }
                .distinct()
                .sorted()
            val keepPrevious = previous?.day == day
            return ScheduleForm(
                day = day,
                opensAt = opensAt,
                courts = template.courts,
                times = times,
                // Al cambiar de franja o de duración se conserva lo elegido si sigue siendo posible
                selectedTime = previous?.selectedTime?.takeIf { keepPrevious && it in times },
                selectedCourts = if (keepPrevious) previous.selectedCourts else emptyList()
            )
        }

        /** Inicios de los tramos de la pista en los que caben [durationMinutes] antes de su cierre. */
        private fun Court.startsFitting(durationMinutes: Int): List<LocalTime> {
            val closing = periods.maxOfOrNull { it.end } ?: return emptyList()
            val closingMinutes = if (closing == LocalTime.MIDNIGHT) 24 * 60 else closing.toSecondOfDay() / 60
            return periods.flatMap { period ->
                generateSequence(period.start) { it.plusMinutes(period.intervalMinutes.toLong()) }
                    .takeWhile { (period.end == LocalTime.MIDNIGHT || it < period.end) && it >= period.start }
                    .toList()
            }.filter { it.toSecondOfDay() / 60 + durationMinutes <= closingMinutes }
        }
    }
}
