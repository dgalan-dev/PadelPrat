package com.dgalan.padelprat.data.scheduled

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reservas programadas, guardadas en el móvil para que sobrevivan a reinicios. No son datos sensibles (día, hora y
 * pistas), así que no se cifran.
 */
@Singleton
class ScheduledBookingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val alarms: ScheduledBookingAlarms
) {

    private val dataStore: DataStore<List<ScheduledBooking>> = DataStoreFactory.create(
        serializer = ScheduledBookingsSerializer,
        produceFile = { context.dataStoreFile(FILE) }
    )

    /** Ordenadas por cuándo se intentarán. */
    val scheduled: Flow<List<ScheduledBooking>> = dataStore.data

    /** Guarda la programación y pone su alarma a la hora de apertura del día. */
    suspend fun add(booking: ScheduledBooking) {
        dataStore.updateData { current -> (current + booking).sortedWith(compareBy({ it.date }, { it.start })) }
        alarms.schedule(booking)
    }

    /** Quita la programación y su alarma. */
    suspend fun remove(id: String) {
        dataStore.updateData { current -> current.filterNot { it.id == id } }
        alarms.cancel(id)
    }

    suspend fun all(): List<ScheduledBooking> = dataStore.data.first()

    suspend fun markBookingStarted(id: String) {
        dataStore.updateData { current -> current.map { if (it.id == id) it.copy(bookingStarted = true) else it } }
    }

    fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val FILE = "scheduled_bookings.json"
    }
}

private object ScheduledBookingsSerializer : Serializer<List<ScheduledBooking>> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: List<ScheduledBooking> = emptyList()

    override suspend fun readFrom(input: InputStream): List<ScheduledBooking> = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("No se pueden leer las reservas programadas", e)
    }

    override suspend fun writeTo(t: List<ScheduledBooking>, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
