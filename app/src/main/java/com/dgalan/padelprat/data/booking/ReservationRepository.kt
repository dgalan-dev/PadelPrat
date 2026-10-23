package com.dgalan.padelprat.data.booking

import com.dgalan.padelprat.data.reservations.ReservationsChangeNotifier
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ReservationResult {
    /** Reserva hecha. [total] es el importe tal como lo muestra la web ("0,00 €"), si se ha podido leer. */
    data class Success(val total: String?) : ReservationResult

    /** El hueco ya no se puede reservar (lo ha cogido otro socio o ya no cabe la duración). */
    data object SlotUnavailable : ReservationResult

    /** La web no deja reservar y da el motivo (p. ej. el límite de reservas del club), que se muestra tal cual. */
    data class Rejected(val message: String) : ReservationResult

    /** Sin conexión antes de confirmar: no se ha reservado nada. */
    data object NetworkError : ReservationResult

    /** Se cortó la conexión al confirmar: la reserva puede haberse hecho o no. No se reintenta nunca solo. */
    data object Uncertain : ReservationResult

    /**
     * La web pide un paso más que la app todavía no hace (p. ej. elegir cómo pagar, con cuentas que pagan la reserva).
     * La reserva no está hecha y se anula sola si no se termina.
     */
    data object Incomplete : ReservationResult

    /** La app ya está volviendo al login. */
    data object SessionExpired : ReservationResult

    data object UnknownError : ReservationResult
}

/**
 * Reserva una pista encadenando los pasos de la web: pre-reserva, cambio de duración si hace falta y confirmación.
 * Se hacen seguidos al confirmar, así que la pista solo queda bloqueada unos instantes (ver
 * docs/plan-confirmar-reserva.md).
 */
@Singleton
class ReservationRepository @Inject constructor(
    private val remote: ReservationRemoteDataSource,
    private val changeNotifier: ReservationsChangeNotifier
) {

    /** Una reserva cada vez: evita reservar dos veces si se lanza otra mientras la primera sigue en curso. */
    private val mutex = Mutex()

    suspend fun book(courtId: Int, start: Instant, durationMinutes: Int): ReservationResult = mutex.withLock {
        var form = when (val step = remote.preReserve(courtId, start)) {
            is ReservationStep.Ok -> step.value
            else -> return step.toResult(confirming = false)
        }

        if (form.selectedDuration != durationMinutes) {
            if (durationMinutes !in form.durations) return ReservationResult.SlotUnavailable
            form = when (val step = remote.changeDuration(form, durationMinutes)) {
                is ReservationStep.Ok -> step.value
                else -> return step.toResult(confirming = false)
            }
            // Si la web no ha aplicado la duración, mejor no confirmar algo distinto de lo que se ha pedido
            if (form.selectedDuration != durationMinutes) return ReservationResult.UnknownError
        }

        val result = when (val step = remote.confirm(form)) {
            is ReservationStep.Ok -> when (val page = step.value) {
                is ConfirmationPage.Confirmed -> ReservationResult.Success(page.total)
                is ConfirmationPage.Incomplete -> ReservationResult.Incomplete
                ConfirmationPage.Unreachable -> ReservationResult.Uncertain
            }

            else -> step.toResult(confirming = true)
        }
        // Mis reservas ha cambiado (o puede haberlo hecho, si el resultado es dudoso)
        if (result is ReservationResult.Success || result == ReservationResult.Uncertain) changeNotifier.notifyChanged()
        result
    }

    private fun ReservationStep<*>.toResult(confirming: Boolean): ReservationResult = when (this) {
        is ReservationStep.Ok -> ReservationResult.UnknownError

        ReservationStep.SlotUnavailable -> ReservationResult.SlotUnavailable

        is ReservationStep.Rejected -> ReservationResult.Rejected(message)

        ReservationStep.SessionExpired -> ReservationResult.SessionExpired

        // Un fallo al confirmar puede haber dejado la reserva hecha o a medias: dudoso, nunca reintentable
        ReservationStep.NetworkError -> if (confirming) ReservationResult.Uncertain else ReservationResult.NetworkError

        ReservationStep.UnknownError -> if (confirming) ReservationResult.Uncertain else ReservationResult.UnknownError
    }
}
