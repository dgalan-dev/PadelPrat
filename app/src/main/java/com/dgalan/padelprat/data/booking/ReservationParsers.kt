package com.dgalan.padelprat.data.booking

import org.jsoup.Jsoup

/**
 * Formulario que devuelve la pre-reserva (ver docs/syltek-api.md, apartado 5.2). [fields] son todos los `input` del
 * formulario tal como vienen, para reenviarlos sin tocarlos al confirmar.
 */
data class ReservationForm(val fields: Map<String, String>, val durations: List<Int>, val selectedDuration: Int)

sealed interface ConfirmationPage {
    data class Confirmed(val total: String?) : ConfirmationPage

    /** La web pide algo más (p. ej. elegir cómo pagar): la reserva todavía no está hecha. */
    data class Incomplete(val total: String?) : ConfirmationPage

    /** La confirmación se aceptó pero su página no se pudo cargar: no se sabe en qué estado ha quedado. */
    data object Unreachable : ConfirmationPage
}

/**
 * Lee las páginas del flujo de reserva. Está aislado aquí porque es lo primero que se rompe si el club cambia su web.
 */
object ReservationParsers {

    private const val FORM_SELECTOR = "form#newreservation"
    private const val DURATION_FIELD = "duration"

    /**
     * La duración viene de dos formas: un `select` si caben 60 y 90 minutos, o un `input` oculto si solo cabe una.
     * Devuelve `null` si la página no trae el formulario.
     */
    fun parseForm(html: String): ReservationForm? {
        val form = Jsoup.parse(html).selectFirst(FORM_SELECTOR) ?: return null
        val fields = linkedMapOf<String, String>()
        form.select("input[name]").forEach { input ->
            if (input.attr("type") !in setOf("submit", "button")) fields[input.attr("name")] = input.attr("value")
        }

        val select = form.selectFirst("select[name=$DURATION_FIELD]")
        val durations: List<Int>
        val selected: Int?
        if (select != null) {
            val options = select.select("option")
            durations = options.mapNotNull { it.attr("value").toIntOrNull() }
            selected = (options.firstOrNull { it.hasAttr("selected") } ?: options.firstOrNull())
                ?.attr("value")?.toIntOrNull()
        } else {
            selected = fields[DURATION_FIELD]?.toIntOrNull()
            durations = listOfNotNull(selected)
        }

        if (selected == null || fields["idReservation"].isNullOrBlank()) return null
        return ReservationForm(fields, durations, selected)
    }

    /**
     * Mensaje de error que la web muestra en la página (p. ej. al superar el límite de reservas del club). Usa el
     * mismo aviso que el error del login (`.c-message-indicator--danger`), o el de advertencia si es ese el que sale.
     * `null` si la página no trae ninguno.
     */
    fun parseErrorMessage(html: String): String? = Jsoup.parse(html)
        .selectFirst(
            ".c-message-indicator--danger .c-message-indicator__content, " +
                ".c-message-indicator--warning .c-message-indicator__content"
        )
        ?.text()?.trim()?.ifEmpty { null }

    /**
     * Página a la que redirige la confirmación (apartado 5.5). Solo es una reserva hecha si muestra el mensaje de
     * éxito: con cuentas que pagan, esta misma página enseña el importe y pide elegir cómo pagar, y hasta entonces
     * la reserva no queda hecha.
     */
    fun parseConfirmation(html: String): ConfirmationPage {
        val page = Jsoup.parse(html)
        val total = page.selectFirst(".c-reservation-resume__total .o-form__text")?.text()?.trim()?.ifEmpty { null }
        val confirmed = page.selectFirst(".c-message-indicator--success") != null
        return if (confirmed) ConfirmationPage.Confirmed(total) else ConfirmationPage.Incomplete(total)
    }
}
