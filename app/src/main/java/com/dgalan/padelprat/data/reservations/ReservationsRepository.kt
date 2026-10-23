package com.dgalan.padelprat.data.reservations

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ReservationsResult {
    data class Success(val reservations: List<Reservation>) : ReservationsResult

    data object NetworkError : ReservationsResult

    /** La app ya está volviendo al login. */
    data object SessionExpired : ReservationsResult

    data object UnknownError : ReservationsResult
}

sealed interface CancelResult {
    /** Comprobado en el listado: la reserva sale como anulada. */
    data object Cancelled : CancelResult

    /** Comprobado en el listado: la reserva sigue activa. */
    data object NotCancelled : CancelResult

    /** No se ha podido comprobar (sin conexión al anular o al recargar). Puede haberse anulado o no. */
    data object Uncertain : CancelResult

    /** La app ya está volviendo al login. */
    data object SessionExpired : CancelResult
}

@Singleton
class ReservationsRepository @Inject constructor(
    private val remote: ReservationsRemoteDataSource,
    private val changeNotifier: ReservationsChangeNotifier
) {

    private val cancelMutex = Mutex()

    /**
     * Todas las reservas del listado. A las próximas activas se les añade la pista, que solo viene en su detalle
     * (una petición por reserva: suelen ser pocas). Las pasadas y anuladas se dejan sin pista para no lanzar decenas
     * de peticiones.
     */
    suspend fun reservations(): ReservationsResult = when (val step = remote.list()) {
        is ReservationsStep.Ok -> ReservationsResult.Success(withCourts(step.value))
        ReservationsStep.NetworkError -> ReservationsResult.NetworkError
        ReservationsStep.SessionExpired -> ReservationsResult.SessionExpired
        ReservationsStep.UnknownError -> ReservationsResult.UnknownError
    }

    private suspend fun withCourts(reservations: List<Reservation>): List<Reservation> = coroutineScope {
        reservations.map { reservation ->
            async {
                if (reservation.upcoming && reservation.status == ReservationStatus.Booked) {
                    reservation.copy(court = remote.court(reservation.id))
                } else {
                    reservation
                }
            }
        }.awaitAll()
    }

    /**
     * Anula una reserva y comprueba el resultado recargando el listado, como pide la web (ver docs/syltek-api.md,
     * apartado 6.3). La petición de anular se lanza una sola vez: nunca se reintenta.
     */
    suspend fun cancel(id: Long): CancelResult = cancelMutex.withLock {
        val sent = remote.cancel(id)
        if (sent == ReservationsStep.SessionExpired) return CancelResult.SessionExpired
        // Aunque falle la red, la petición ha podido llegar: siempre se comprueba en el listado
        val result = when (val list = remote.list()) {
            is ReservationsStep.Ok -> when (list.value.firstOrNull { it.id == id }?.status) {
                ReservationStatus.Cancelled -> CancelResult.Cancelled
                ReservationStatus.Booked -> CancelResult.NotCancelled
                else -> CancelResult.Uncertain
            }

            ReservationsStep.SessionExpired -> CancelResult.SessionExpired

            else -> CancelResult.Uncertain
        }
        changeNotifier.notifyChanged()
        result
    }
}
