package com.dgalan.padelprat.data.booking

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Parrilla de un día tal como la da la web del club (ver docs/syltek-api.md, apartado 4). */
data class DayTimetable(
    val date: LocalDate,
    /** Id del usuario con sesión: sirve para reconocer sus propias reservas. */
    val customerId: Long,
    /** Hora del servidor. */
    val now: Instant,
    /** Hasta cuándo se puede reservar (unos 7 días). */
    val maxAvailability: Instant,
    val courts: List<Court>,
    val bookings: List<Booking>
)

data class Court(
    val id: Int,
    /** Sin el patrocinador: "Pista 1" en lugar de "Pista 1: Solán de Cabras". */
    val name: String,
    /** Patrocinador de la pista ("Solán de Cabras"), si tiene. */
    val sponsor: String?,
    val covered: Boolean,
    val periods: List<Period>
)

/** Tramo del horario de una pista, en hora local del club: inicios posibles cada [intervalMinutes]. */
data class Period(val start: LocalTime, val end: LocalTime, val intervalMinutes: Int)

data class Booking(val id: Long, val courtId: Int, val start: Instant, val end: Instant, val customerId: Long?)
