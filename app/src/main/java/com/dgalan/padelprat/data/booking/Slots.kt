package com.dgalan.padelprat.data.booking

import com.dgalan.padelprat.core.network.Syltek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** Franjas del filtro de la pantalla de reservar. */
enum class DayPart {
    /** Inicios de 09:00 a 16:30. */
    Morning,

    /** Inicios de las 17:00 al cierre. */
    Afternoon;

    companion object {
        val AFTERNOON_START: LocalTime = LocalTime.of(17, 0)

        fun of(time: LocalTime): DayPart = if (time < AFTERNOON_START) Morning else Afternoon
    }
}

enum class SlotState {
    /** Se puede reservar: cabe la duración pedida hasta la siguiente reserva o el cierre. */
    Free,

    /** Ocupado por otra reserva, o libre pero sin tiempo suficiente para reservar. */
    Unavailable,

    /** Dentro de una reserva del propio usuario. */
    Mine
}

/** Un inicio posible en una pista. [start] es el instante real; [time], la hora local del club que se muestra. */
data class Slot(val start: Instant, val time: LocalTime, val state: SlotState, val maxMinutes: Int)

data class CourtSlots(val court: Court, val slots: List<Slot>)

/** Duraciones que permite reservar la web, en minutos, en el orden en que se muestran. */
val BOOKING_DURATIONS = listOf(90, 60)

/**
 * Calcula los huecos de cada pista a partir de la parrilla (ver docs/syltek-api.md, apartado 4.6): recorre sus
 * tramos en pasos de `interval` minutos y marca cada inicio como libre, ocupado o propio. Se omiten los inicios que
 * ya han pasado y los posteriores al límite de reserva.
 */
object SlotCalculator {

    /** [durationMinutes]: un hueco solo es libre si caben esos minutos hasta la siguiente reserva o el cierre. */
    fun slots(timetable: DayTimetable, durationMinutes: Int): List<CourtSlots> = timetable.courts.map { court ->
        CourtSlots(court, courtSlots(court, timetable, durationMinutes))
    }

    private fun courtSlots(court: Court, timetable: DayTimetable, durationMinutes: Int): List<Slot> {
        val bookings = timetable.bookings.filter { it.courtId == court.id }.sortedBy { it.start }
        val closing = court.periods.maxOfOrNull { timetable.endInstant(it.end) } ?: return emptyList()

        return court.periods
            .flatMap { period -> period.starts() }
            .distinct()
            .sorted()
            .map { time -> time to timetable.instantAt(time) }
            .filter { (_, start) -> start >= timetable.now && start <= timetable.maxAvailability }
            .map { (time, start) ->
                val booking = bookings.firstOrNull { start >= it.start && start < it.end }
                if (booking != null) {
                    val mine = booking.customerId == timetable.customerId
                    Slot(start, time, if (mine) SlotState.Mine else SlotState.Unavailable, maxMinutes = 0)
                } else {
                    val nextBusy = bookings.firstOrNull { it.start > start }?.start?.coerceAtMost(closing) ?: closing
                    val minutes = Duration.between(start, nextBusy).toMinutes().toInt()
                    val state = if (minutes >= durationMinutes) SlotState.Free else SlotState.Unavailable
                    Slot(start, time, state, maxMinutes = minutes)
                }
            }
    }

    private fun Period.starts(): List<LocalTime> = generateSequence(start) { it.plusMinutes(intervalMinutes.toLong()) }
        // Un inicio justo en el final del tramo no sirve. Un tramo que acaba a medianoche (00:00) llega hasta el
        // final del día, y el `>= start` corta cuando plusMinutes da la vuelta a las 00:00
        .takeWhile { (end == LocalTime.MIDNIGHT || it < end) && it >= start }
        .toList()

    private fun DayTimetable.instantAt(time: LocalTime): Instant = date.atTime(time).atZone(Syltek.ZONE).toInstant()

    /** Como [instantAt], pero un final a las 00:00 es la medianoche del día siguiente. */
    private fun DayTimetable.endInstant(time: LocalTime): Instant = if (time == LocalTime.MIDNIGHT) {
        date.plusDays(1).atStartOfDay(Syltek.ZONE).toInstant()
    } else {
        instantAt(time)
    }
}
