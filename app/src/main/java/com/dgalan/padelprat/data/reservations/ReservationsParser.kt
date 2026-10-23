package com.dgalan.padelprat.data.reservations

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Lee el listado de "Mis reservas" y el detalle de una reserva (ver docs/syltek-api.md, apartados 6.1 y 6.2). Está
 * aislado aquí porque es lo primero que se rompe si el club cambia su web. Los textos vienen en castellano porque la
 * app pide la web en español.
 */
object ReservationsParser {

    private val SPANISH = Locale.forLanguageTag("es-ES")

    /** "28 de octubre de 2026", ya sin el día de la semana. Los meses vienen en minúscula. */
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("d 'de' MMMM 'de' yyyy")
        .toFormatter(SPANISH)

    /** "10:00, 90 min". */
    private val HOUR_REGEX = Regex("""(\d{1,2}:\d{2}).*?(\d+)\s*min""")
    private val SHOW_REGEX = Regex("""showreservation/(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * Devuelve `null` si la página no tiene el formato esperado. El primer bloque de la página son las próximas
     * (incluidas las anuladas) y el segundo, el histórico.
     */
    fun parseList(html: String): List<Reservation>? {
        val document = Jsoup.parse(html)
        val blocks = document.select("div.c-page-reservations")
        if (blocks.isEmpty() && document.selectFirst("h1.c-page-title__text") == null) return null
        return blocks.flatMapIndexed { index, block ->
            block.select(".c-card--game").mapNotNull { card -> parseCard(card, upcoming = index == 0) }
        }
    }

    private fun parseCard(card: Element, upcoming: Boolean): Reservation? {
        val links = card.select("a[href]").map { it.attr("href") }
        val id = links.firstNotNullOfOrNull { SHOW_REGEX.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: return null
        val day = card.selectFirst(".c-card__day")?.text()?.let(::parseDate) ?: return null
        val hour = card.selectFirst(".c-card__hour")?.text()?.let(HOUR_REGEX::find) ?: return null
        val time = runCatching { LocalTime.parse(hour.groupValues[1].padStart(5, '0')) }.getOrNull() ?: return null

        val statusText = card.selectFirst(".c-card__status span")?.text()?.trim().orEmpty()
        return Reservation(
            id = id,
            status = when {
                statusText.startsWith("Reservada", ignoreCase = true) -> ReservationStatus.Booked
                statusText.startsWith("Anulada", ignoreCase = true) -> ReservationStatus.Cancelled
                else -> ReservationStatus.Unknown
            },
            start = LocalDateTime.of(day, time),
            durationMinutes = hour.groupValues[2].toInt(),
            total = card.selectFirst(".c-card__text-total")?.text()?.trim()?.ifEmpty { null },
            canCancel = links.any { it.contains("cancelReservation/", ignoreCase = true) },
            upcoming = upcoming
        )
    }

    /** "miércoles, 28 de octubre de 2026" → 2026-10-28. */
    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text.substringAfter(',').trim(), DATE_FORMAT)
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * Pista del detalle de una reserva. Cada fila es `.o-form-row` con la etiqueta y, aparte, un
     * `div.o-form-row__field` con el valor (`span.o-form__text`). Las clases de las etiquetas vienen mal escritas en
     * el HTML, así que la etiqueta se busca por su texto ("Pista") y no por su clase.
     */
    fun parseDetailCourt(html: String): ReservationCourt? {
        val value = Jsoup.parse(html).select("span.o-form__text").firstOrNull { span ->
            detailLabel(span).trim().removeSuffix(":").trim().equals("Pista", ignoreCase = true)
        }?.text()?.trim()?.ifEmpty { null } ?: return null
        val sponsor = value.substringAfter(':', missingDelimiterValue = "").trim().ifEmpty { null }
        return ReservationCourt(name = value.substringBefore(':').trim(), sponsor = sponsor)
    }

    /** Texto de la etiqueta de un valor: lo que hay en su fila aparte del propio valor. */
    private fun detailLabel(value: Element): String {
        val field = value.parent() ?: return ""
        field.previousElementSibling()?.text()?.takeIf { it.isNotBlank() }?.let { return it }
        val row = field.parent() ?: return ""
        return row.ownText().ifBlank { row.text().removeSuffix(field.text()) }
    }
}
