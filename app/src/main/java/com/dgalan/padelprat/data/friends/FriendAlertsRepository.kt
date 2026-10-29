package com.dgalan.padelprat.data.friends

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.reservations.Reservation
import com.dgalan.padelprat.data.reservations.ReservationCourt
import com.dgalan.padelprat.data.reservations.ReservationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Avisos de las reservas del amigo. Al llegar su push ([check]), se leen sus reservas en Firestore y se comparan con
 * las últimas vistas: se avisa de las nuevas y de las que ya no están sin haber terminado (anuladas). La primera vez
 * solo se guardan, para no avisar de todas las que ya tenía.
 *
 * Se guardan en el móvil, fuera de las copias de seguridad, y se borran al desactivar las reservas compartidas.
 */
@Singleton
class FriendAlertsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val sharedReservations: SharedReservationsRepository,
    private val notifications: FriendNotifications
) {

    private val dataStore: DataStore<FriendAlertsState> = DataStoreFactory.create(
        serializer = FriendAlertsSerializer,
        produceFile = { context.dataStoreFile(FILE) }
    )

    private val mutex = Mutex()

    val enabled: Flow<Boolean> = dataStore.data.map { it.enabled }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.updateData { it.copy(enabled = enabled) }
    }

    suspend fun check() = mutex.withLock {
        val friend = sharedReservations.fetchFriend() ?: return@withLock
        val state = dataStore.data.first()
        val previous = state.seen.takeIf { state.friendUid == friend.uid }
        val current = friend.reservations.reservations
        dataStore.updateData {
            it.copy(friendUid = friend.uid, seen = current.map { reservation -> reservation.toSeen() })
        }
        if (previous == null || !state.enabled) return@withLock

        val previousIds = previous.map { it.id }.toSet()
        val currentIds = current.map { it.id }.toSet()
        val now = LocalDateTime.now(Syltek.ZONE)
        current.filter { it.id !in previousIds }
            .forEach { notifications.show(friend.reservations.name, it, cancelled = false) }
        // Las que desaparecen por haber terminado no son anulaciones
        previous.map { it.toReservation() }
            .filter { it.id !in currentIds && it.end > now }
            .forEach { notifications.show(friend.reservations.name, it, cancelled = true) }
    }

    /** Al desactivar las reservas compartidas o cerrar sesión. Lo activado en Perfil se mantiene. */
    suspend fun clear() = mutex.withLock {
        dataStore.updateData { it.copy(friendUid = null, seen = null) }
    }

    private companion object {
        /** Excluido de las copias de seguridad (res/xml). */
        const val FILE = "friend_alerts.json"
    }
}

@Serializable
data class FriendAlertsState(
    val enabled: Boolean = true,
    val friendUid: String? = null,
    /** Las reservas del amigo la última vez que se miraron; `null` si todavía no se han mirado. */
    val seen: List<SeenReservation>? = null
)

/** [start] en hora del club, como `LocalDateTime.toString()`. */
@Serializable
data class SeenReservation(
    val id: Long,
    val start: String,
    val durationMinutes: Int,
    val court: String? = null,
    val sponsor: String? = null
) {
    fun toReservation() = Reservation(
        id = id,
        status = ReservationStatus.Booked,
        start = LocalDateTime.parse(start),
        durationMinutes = durationMinutes,
        total = null,
        canCancel = false,
        upcoming = true,
        court = court?.let { ReservationCourt(it, sponsor) }
    )
}

private fun Reservation.toSeen() = SeenReservation(id, start.toString(), durationMinutes, court?.name, court?.sponsor)

private object FriendAlertsSerializer : Serializer<FriendAlertsState> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: FriendAlertsState = FriendAlertsState()

    override suspend fun readFrom(input: InputStream): FriendAlertsState = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("No se pueden leer los avisos del amigo", e)
    }

    override suspend fun writeTo(t: FriendAlertsState, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
