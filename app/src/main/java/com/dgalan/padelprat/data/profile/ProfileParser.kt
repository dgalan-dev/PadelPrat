package com.dgalan.padelprat.data.profile

import org.jsoup.Jsoup

/**
 * Lee "Mi Cuenta" (`/customerzone/account/`, ver docs/syltek-api.md, apartado 6.4). Cada dato es una `label` con su
 * texto ("Nombre", "Email"…) seguida de un `input` sin `name` con el valor. Está aislado aquí porque es lo primero que
 * se rompe si el club cambia su web.
 */
object ProfileParser {

    /** Devuelve `null` si la página no trae al menos el nombre. */
    fun parse(html: String): Profile? {
        val values = mutableMapOf<String, String>()
        Jsoup.parse(html).select("label").forEach { label ->
            // El valor es el primer input tras la etiqueta, antes de la siguiente etiqueta
            val input = label.nextElementSiblings()
                .takeWhile { it.tagName() != "label" }
                .firstOrNull { it.tagName() == "input" }
                ?: return@forEach
            val value = input.attr("value").trim()
            if (value.isNotEmpty()) values.putIfAbsent(label.text().trim().lowercase(), value)
        }
        val name = values["nombre"] ?: return null
        return Profile(
            name = name,
            email = values["email"],
            phone = values["teléfono"],
            address = values["dirección"],
            postalCode = values["cod. postal"],
            city = values["población, ciudad"]
        )
    }
}
