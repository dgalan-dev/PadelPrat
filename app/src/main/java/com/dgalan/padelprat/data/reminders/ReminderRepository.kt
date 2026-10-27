package com.dgalan.padelprat.data.reminders

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.dgalan.padelprat.data.reservations.Reservation
import com.dgalan.padelprat.data.reservations.ReservationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Avisos de las próximas reservas: 3 h antes del partido y, el día anterior, a las 10:00 y a las 21:00 para recordar
 * que es el último día para anular sin cargos. Se ponen al día cada vez que se lee el listado de Mis reservas
 * ([sync]), y [reschedule] vuelve a poner las alarmas cuando Android las ha podido perder (como con las reservas
 * programadas).
 *
 * Se guardan en el móvil, sin cifrar (día, hora y duración), y fuera de las copias de seguridad: se rehacen solos con
 * el listado. Al cerrar sesión se borran.
 */
@Singleton
class ReminderRepository @Inject constructor(@ApplicationContext context: Context, private val alarms: ReminderAlarms) {

    private val dataStore: DataStore<RemindersState> = DataStoreFactory.create(
        serializer = RemindersSerializer,
        produceFile = { context.dataStoreFile(FILE) }
    )

    /** Los cambios van de uno en uno: [sync] compara con lo guardado antes de quitar alarmas. */
    private val mutex = Mutex()

    val state: Flow<RemindersState> = dataStore.data

    suspend fun find(reservationId: Long): Reminder? =
        dataStore.data.first().reminders.firstOrNull { it.reservationId == reservationId }

    suspend fun isEnabled(kind: ReminderKind): Boolean = dataStore.data.first().isEnabled(kind)

    /** Con el listado recién leído: avisos para las próximas reservas activas y fuera los de las demás. */
    suspend fun sync(reservations: List<Reservation>) = mutex.withLock {
        val now = Instant.now()
        val upcoming = reservations
            .filter { it.upcoming && it.status == ReservationStatus.Booked }
            .map { Reminder(it.id, it.start.toLocalDate(), it.start.toLocalTime(), it.durationMinutes) }
            .filter { it.startsAt > now }
        val previous = dataStore.data.first().reminders
        val state = dataStore.updateData { it.copy(reminders = upcoming) }
        val kept = upcoming.map { it.reservationId }.toSet()
        previous.filterNot { it.reservationId in kept }.forEach(::cancelAll)
        state.reminders.forEach { schedule(it, state) }
    }

    /** Vuelve a poner las alarmas y quita las reservas que ya han empezado. */
    suspend fun reschedule() = mutex.withLock {
        val now = Instant.now()
        val state = dataStore.updateData { state ->
            state.copy(reminders = state.reminders.filter { it.startsAt > now })
        }
        state.reminders.forEach { schedule(it, state) }
    }

    suspend fun setMatchEnabled(enabled: Boolean) = mutex.withLock {
        val state = dataStore.updateData { it.copy(matchEnabled = enabled) }
        state.reminders.forEach { schedule(it, state) }
    }

    suspend fun setCancelEnabled(enabled: Boolean) = mutex.withLock {
        val state = dataStore.updateData { it.copy(cancelEnabled = enabled) }
        state.reminders.forEach { schedule(it, state) }
    }

    /** Al cerrar sesión: fuera los avisos y sus alarmas. Lo activado en Perfil se mantiene. */
    suspend fun clear() = mutex.withLock {
        val previous = dataStore.data.first().reminders
        dataStore.updateData { it.copy(reminders = emptyList()) }
        previous.forEach(::cancelAll)
    }

    /** Pone las alarmas activadas que aún no han pasado y quita las demás. */
    private fun schedule(reminder: Reminder, state: RemindersState) {
        val now = Instant.now()
        ReminderKind.entries.forEach { kind ->
            val at = kind.at(reminder)
            if (at != null && state.isEnabled(kind) && at > now) {
                alarms.schedule(reminder.reservationId, kind, at)
            } else {
                alarms.cancel(reminder.reservationId, kind)
            }
        }
    }

    private fun cancelAll(reminder: Reminder) {
        ReminderKind.entries.forEach { alarms.cancel(reminder.reservationId, it) }
    }

    private companion object {
        /** Excluido de las copias de seguridad (res/xml). */
        const val FILE = "reminders.json"
    }
}

private object RemindersSerializer : Serializer<RemindersState> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: RemindersState = RemindersState()

    override suspend fun readFrom(input: InputStream): RemindersState = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("No se pueden leer los avisos", e)
    }

    override suspend fun writeTo(t: RemindersState, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
