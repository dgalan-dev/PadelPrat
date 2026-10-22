package com.dgalan.padelprat.core.network

import java.time.ZoneId

/** Datos fijos de la web de reservas del club. Ver docs/syltek-api.md. */
object Syltek {
    /** Zona horaria del club: los horarios de las pistas y la pre-reserva van en hora local. */
    val ZONE: ZoneId = ZoneId.of("Europe/Madrid")

    /** Cookie de sesión: nombre fijo, dura 6 meses. */
    const val SESSION_COOKIE = "4358260989987"

    const val LOGIN_PATH = "bookings/customer/login"
    const val LOGOUT_PATH = "customer/logout"

    /** Parrilla de un día; `type=56` es el tipo de recurso "pista de pádel". */
    const val TIMETABLE_PATH = "customerzone/timetable"
    const val PADEL_RESOURCE_TYPE = "56"

    /** Página tras el login. */
    const val HOME_PATH = "/customerzone/index"

    /** Con la sesión caducada, las páginas privadas redirigen (303) aquí. */
    const val LOGIN_REDIRECT_PATH = "/customer/login"
}
