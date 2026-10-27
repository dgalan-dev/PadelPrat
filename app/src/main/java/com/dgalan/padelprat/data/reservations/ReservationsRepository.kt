package com.dgalan.padelprat.data.reservations

import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.reminders.ReminderRepository
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** Si una reserva sigue activa, para los avisos. */
sealed interface ReservationCheck {
    /** Sigue reservada; [court] es la pista que tiene ahora, si se ha podido leer. */
    data class Active(val court: ReservationCourt?) : ReservationCheck

    /** El listado la da como anulada. */
    data object Cancelled : ReservationCheck

    /** Sin conexión, o no aparece en el listado (p. ej. si la web cambia y no se lee bien). */
    data object Unknown : ReservationCheck
}

@Singleton
class ReservationsRepository @Inject constructor(
    private val remote: ReservationsRemoteDataSource,
    private val changeNotifier: ReservationsChangeNotifier,
    private val reminders: ReminderRepository
) {

    private val cancelMutex = Mutex()

    private val _activeCount = MutableStateFlow<Int?>(null)

    /**
     * Reservas activas que todavía no han terminado, para avisar del límite del club
     * ([Syltek.MAX_ACTIVE_RESERVATIONS]) antes de intentar reservar. `null` hasta la primera carga.
     */
    val activeCount: StateFlow<Int?> = _activeCount.asStateFlow()

    /** Actualiza solo el contador, con el listado y sin pedir el detalle de cada reserva. */
    suspend fun refreshActiveCount() {
        list()
    }

    /** El listado de la web. Cada vez que llega se actualizan el contador de activas y los avisos. */
    private suspend fun list(): ReservationsStep<List<Reservation>> = remote.list().also { step ->
        if (step is ReservationsStep.Ok) {
            updateActiveCount(step.value)
            reminders.sync(step.value)
        }
    }

    private fun updateActiveCount(reservations: List<Reservation>) {
        val now = LocalDateTime.now(Syltek.ZONE)
        _activeCount.value = reservations.count { it.upcoming && it.status == ReservationStatus.Booked && it.end > now }
    }

    /**
     * Todas las reservas del listado. A las próximas activas se les añade la pista, que solo viene en su detalle
     * (una petición por reserva: suelen ser pocas). Las pasadas y anuladas se dejan sin pista para no lanzar decenas
     * de peticiones.
     */
    suspend fun reservations(): ReservationsResult = when (val step = list()) {
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
     * Pista de la reserva activa que empieza a [start] (hora del club) y dura [durationMinutes], según el listado y su
     * detalle. Solo lee. `null` si no aparece o no se ha podido comprobar, lo que no quiere decir que no esté hecha:
     * el listado puede tardar en mostrarla.
     */
    suspend fun bookedCourt(start: LocalDateTime, durationMinutes: Int): ReservationCourt? {
        val list = list() as? ReservationsStep.Ok ?: return null
        val reservation = list.value.firstOrNull {
            it.upcoming && it.status == ReservationStatus.Booked && it.start == start &&
                it.durationMinutes == durationMinutes
        } ?: return null
        return remote.court(reservation.id)
    }

    /** Para los avisos: si la reserva sigue activa y en qué pista. Solo lee. */
    suspend fun check(id: Long): ReservationCheck {
        val list = list() as? ReservationsStep.Ok ?: return ReservationCheck.Unknown
        return when (list.value.firstOrNull { it.id == id }?.status) {
            ReservationStatus.Booked -> ReservationCheck.Active(remote.court(id))
            ReservationStatus.Cancelled -> ReservationCheck.Cancelled
            else -> ReservationCheck.Unknown
        }
    }

    /**
     * Anula una reserva y comprueba el resultado recargando el listado, como pide la web (ver docs/syltek-api.md,
     * apartado 6.3). La petición de anular se lanza una sola vez: nunca se reintenta.
     */
    suspend fun cancel(id: Long): CancelResult = cancelMutex.withLock {
        val sent = remote.cancel(id)
        if (sent == ReservationsStep.SessionExpired) return CancelResult.SessionExpired
        // Aunque falle la red, la petición ha podido llegar: siempre se comprueba en el listado
        val result = when (val list = list()) {
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
