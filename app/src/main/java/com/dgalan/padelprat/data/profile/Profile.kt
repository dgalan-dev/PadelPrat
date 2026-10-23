package com.dgalan.padelprat.data.profile

/** Datos de "Mi Cuenta" (ver docs/syltek-api.md, apartado 6.4). Los vacíos en la web llegan como `null`. */
data class Profile(
    val name: String,
    val email: String?,
    val phone: String?,
    val address: String?,
    val postalCode: String?,
    val city: String?
) {
    /** Para el saludo: "Daniel" de "Daniel Galán". */
    val firstName: String get() = name.trim().substringBefore(' ')

    /** Hasta dos iniciales: "DG". */
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }
}
