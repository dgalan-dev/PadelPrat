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

    /**
     * Ocupado por otra reserva, o libre pero sin tiempo suficiente para reservar o dejando un hueco libre de menos de
     * [Syltek.MIN_FREE_GAP_MINUTES] antes o después, que la web no permite.
     */
    Unavailable
}

/** Un inicio posible en una pista. [start] es el instante real; [time], la hora local del club que se muestra. */
data class Slot(val start: Instant, val time: LocalTime, val state: SlotState, val maxMinutes: Int)

/**
 * Una reserva del propio usuario, que se enseña entera en lugar de como huecos de media hora. [time] y [endTime] son
 * la hora local del club.
 */
data class MyBooking(val court: Court, val start: Instant, val time: LocalTime, val endTime: LocalTime)

/** [slots] no incluye los inicios que caen dentro de [mine]. */
data class CourtSlots(val court: Court, val slots: List<Slot>, val mine: List<MyBooking> = emptyList())

/** Una hora de inicio con las pistas que se pueden reservar a esa hora y las reservas propias que empiezan entonces. */
data class TimeSlots(
    val time: LocalTime,
    val start: Instant,
    val courts: List<CourtAtTime>,
    val mine: List<MyBooking> = emptyList()
)

data class CourtAtTime(val court: Court, val state: SlotState)

/** Duraciones que permite reservar la web, en minutos, en el orden en que se muestran. */
val BOOKING_DURATIONS = listOf(90, 60)

/**
 * Calcula los huecos de cada pista a partir de la parrilla (ver docs/syltek-api.md, apartado 4.6): recorre sus
 * tramos en pasos de `interval` minutos y marca cada inicio como libre, ocupado o propio. Se omiten los inicios que
 * ya han pasado y los posteriores al límite de reserva. Las reservas propias van aparte, en [CourtSlots.mine].
 *
 * Un inicio solo es libre si la reserva no deja un hueco de menos de [Syltek.MIN_FREE_GAP_MINUTES] hasta la reserva
 * anterior o la apertura, ni hasta la siguiente o el cierre. Los huecos que ya han pasado también cuentan: no se sabe
 * si la web los descarta, así que se aplica igual.
 */
object SlotCalculator {

    /** [durationMinutes]: un hueco solo es libre si caben esos minutos hasta la siguiente reserva o el cierre. */
    fun slots(timetable: DayTimetable, durationMinutes: Int): List<CourtSlots> = timetable.courts.map { court ->
        CourtSlots(court, courtSlots(court, timetable, durationMinutes), myBookings(court, timetable))
    }

    /**
     * Las mismas pistas agrupadas por hora, para la vista "por hora": solo las horas con alguna pista libre o en las
     * que empieza una reserva propia, y en cada una esas pistas, en el orden de la parrilla.
     */
    fun byTime(courts: List<CourtSlots>): List<TimeSlots> {
        val free = courts
            .flatMap { courtSlots ->
                courtSlots.slots
                    .filter { it.state == SlotState.Free }
                    .map { slot -> slot to CourtAtTime(courtSlots.court, slot.state) }
            }
            .groupBy({ (slot, _) -> slot.start }, { (_, court) -> court })
        val times = courts.flatMap { it.slots }.associate { it.start to it.time }
        val mine = courts.flatMap { it.mine }.groupBy { it.start }
        return (free.keys + mine.keys)
            .map { start ->
                val time = times[start] ?: mine.getValue(start).first().time
                TimeSlots(time, start, free[start].orEmpty(), mine[start].orEmpty())
            }
            .sortedBy { it.start }
    }

    private fun courtSlots(court: Court, timetable: DayTimetable, durationMinutes: Int): List<Slot> {
        val bookings = timetable.bookings.filter { it.courtId == court.id }.sortedBy { it.start }
        val closing = court.periods.maxOfOrNull { timetable.endInstant(it.end) } ?: return emptyList()
        val opening = court.periods.minOf { timetable.instantAt(it.start) }

        return court.periods
            .flatMap { period -> period.starts() }
            .distinct()
            .sorted()
            .map { time -> time to timetable.instantAt(time) }
            .filter { (_, start) -> start >= timetable.now && start <= timetable.maxAvailability }
            .mapNotNull { (time, start) ->
                val booking = bookings.firstOrNull { start >= it.start && start < it.end }
                when {
                    // Los inicios dentro de una reserva propia no salen: la reserva se enseña entera
                    booking?.customerId == timetable.customerId -> null

                    booking != null -> Slot(start, time, SlotState.Unavailable, maxMinutes = 0)

                    else -> {
                        val previousEnd =
                            bookings.filter { it.end <= start }.maxOfOrNull { it.end }?.coerceAtLeast(opening)
                                ?: opening
                        val nextBusy =
                            bookings.firstOrNull { it.start > start }?.start?.coerceAtMost(closing) ?: closing
                        val minutes = Duration.between(start, nextBusy).toMinutes().toInt()
                        val fits = minutes >= durationMinutes
                        val gapsAllowed = Duration.between(previousEnd, start).toMinutes().isAllowedGap() &&
                            (minutes - durationMinutes).toLong().isAllowedGap()
                        val state = if (fits && gapsAllowed) SlotState.Free else SlotState.Unavailable
                        Slot(start, time, state, maxMinutes = minutes)
                    }
                }
            }
    }

    /** Las que aún no han acabado; una ya empezada sigue saliendo con su hora de inicio. */
    private fun myBookings(court: Court, timetable: DayTimetable): List<MyBooking> = timetable.bookings
        .filter { it.courtId == court.id && it.customerId == timetable.customerId && it.end > timetable.now }
        .sortedBy { it.start }
        .map { booking ->
            MyBooking(
                court = court,
                start = booking.start,
                time = booking.start.atZone(Syltek.ZONE).toLocalTime(),
                endTime = booking.end.atZone(Syltek.ZONE).toLocalTime()
            )
        }

    /** Sin hueco, o uno que se pueda reservar después. */
    private fun Long.isAllowedGap(): Boolean = this == 0L || this >= Syltek.MIN_FREE_GAP_MINUTES

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
