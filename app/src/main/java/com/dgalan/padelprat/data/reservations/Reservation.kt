package com.dgalan.padelprat.data.reservations

import com.dgalan.padelprat.data.scheduled.LocalDateTimeSerializer
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

enum class ReservationStatus { Booked, Cancelled, Unknown }

/** Una reserva del usuario tal como la da la web (ver docs/syltek-api.md, apartado 6). Horas locales del club. */
@Serializable
data class Reservation(
    val id: Long,
    val status: ReservationStatus,
    @Serializable(with = LocalDateTimeSerializer::class) val start: LocalDateTime,
    val durationMinutes: Int,
    /** Importe tal como lo muestra la web ("0,00 €"). */
    val total: String?,
    /** La web solo da el enlace de anular si todavía se puede. */
    val canCancel: Boolean,
    /** Del bloque "Próximas reservas" de la web; si no, del "Histórico". */
    val upcoming: Boolean,
    /** Solo viene en el detalle de la reserva; se pide para las próximas. */
    val court: ReservationCourt? = null
) {
    val end: LocalDateTime get() = start.plusMinutes(durationMinutes.toLong())
}

/** "Pista 2: San Miguel Magna" → nombre "Pista 2" y patrocinador "San Miguel Magna". */
@Serializable
data class ReservationCourt(val name: String, val sponsor: String?)
