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

    /** Flujo de reserva: pre-reserva (bloquea 8 minutos), cambio de duración y confirmación. */
    const val PRE_RESERVATION_PATH = "customerZone/newReservation"
    const val UPDATE_DURATION_PATH = "customerzone/updateduration"
    const val CONFIRM_RESERVATION_PATH = "customerZone/newReservationPost"

    /** Tras confirmar, la web redirige aquí; si redirige a la parrilla es que el hueco ya no es válido. */
    const val CONFIRMATION_PATH = "/booking/confirm"

    /** Mis reservas: listado, detalle (`…/{id}`) y anulación (`…/{id}`, un GET que borra: nunca se reintenta). */
    const val RESERVATIONS_PATH = "customerzone/reservations"
    const val RESERVATION_DETAIL_PATH = "customerzone/showreservation/"
    const val CANCEL_RESERVATION_PATH = "customerzone/cancelReservation/"

    /**
     * Cada día se abre para reservar a las 9:00 (hora de Madrid) siete días antes (confirmado por el club,
     * 23/10/2026). El último día abierto en cada momento lo da `nowUtcMaxAvailability` de la parrilla.
     */
    const val BOOKING_OPENS_DAYS_BEFORE = 7L
    val BOOKING_OPENS_AT: java.time.LocalTime = java.time.LocalTime.of(9, 0)

    /** El club solo deja tener a la vez este número de reservas activas; al anular una se puede volver a reservar. */
    const val MAX_ACTIVE_RESERVATIONS = 2

    /**
     * Una reserva no puede dejar antes ni después un hueco libre más corto que esto, hasta otra reserva, la apertura o
     * el cierre. La web lo rechaza con "No se pueden dejar espacios libres menores de 60 minutos" (29/10/2026).
     */
    const val MIN_FREE_GAP_MINUTES = 60L

    /** "Mi Cuenta": datos del usuario (solo lectura en la app). */
    const val ACCOUNT_PATH = "customerzone/account/"

    /** Página tras el login. */
    const val HOME_PATH = "/customerzone/index"

    /** Con la sesión caducada, las páginas privadas redirigen (303) aquí. */
    const val LOGIN_REDIRECT_PATH = "/customer/login"
}
