package com.dgalan.padelprat.ui.format

import com.dgalan.padelprat.core.network.Syltek
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Fechas y horas de la interfaz, siempre en español y en la zona del club

val SPANISH: Locale = Locale.forLanguageTag("es-ES")

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SPANISH)
private val OPENING_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'a las' H:mm", SPANISH)

/** "18:30". */
fun LocalTime.hourText(): String = format(TIME_FORMAT)

/** "Sábado, 24 de octubre". */
fun LocalDate.longDate(): String = format(LONG_DATE).replaceFirstChar { it.titlecase(SPANISH) }

/** "el sábado 24 a las 9:00". */
fun Instant.openingText(): String = atZone(Syltek.ZONE).format(OPENING_FORMAT)

/** "Vie", "Sáb"…: la abreviatura del sistema puede traer punto final ("vie."). */
fun DayOfWeek.shortName(): String =
    getDisplayName(TextStyle.SHORT, SPANISH).removeSuffix(".").replaceFirstChar { it.titlecase(SPANISH) }
