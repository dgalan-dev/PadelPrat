package com.dgalan.padelprat.core.network

/** Datos fijos de la web de reservas del club. Ver docs/syltek-api.md. */
object Syltek {
    /** Cookie de sesión: nombre fijo, dura 6 meses. */
    const val SESSION_COOKIE = "4358260989987"

    const val LOGIN_PATH = "bookings/customer/login"
    const val LOGOUT_PATH = "customer/logout"

    /** Página tras el login. */
    const val HOME_PATH = "/customerzone/index"

    /** Con la sesión caducada, las páginas privadas redirigen (303) aquí. */
    const val LOGIN_REDIRECT_PATH = "/customer/login"
}
