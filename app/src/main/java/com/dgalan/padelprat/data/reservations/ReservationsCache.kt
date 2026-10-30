package com.dgalan.padelprat.data.reservations

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.dgalan.padelprat.data.scheduled.InstantSerializer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** El último listado de Mis reservas leído de la web, con las pistas, y cuándo se leyó. */
@Serializable
data class CachedReservations(
    @Serializable(with = InstantSerializer::class) val savedAt: Instant,
    val reservations: List<Reservation>
)

/**
 * Copia del último listado de Mis reservas, para enseñarlo sin conexión (p. ej. en el club, para ver la pista).
 * Se guarda en el móvil sin cifrar, como los avisos, y fuera de las copias de seguridad. Se borra al cerrar sesión y
 * al iniciarla, por si entra otro usuario tras caducar la sesión.
 */
@Singleton
class ReservationsCache @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore: DataStore<CachedReservations?> = DataStoreFactory.create(
        serializer = CachedReservationsSerializer,
        // Es solo una copia: si no se puede leer, se descarta
        corruptionHandler = ReplaceFileCorruptionHandler { null },
        produceFile = { context.dataStoreFile(FILE) }
    )

    suspend fun read(): CachedReservations? = dataStore.data.first()

    suspend fun save(reservations: List<Reservation>) {
        dataStore.updateData { CachedReservations(Instant.now(), reservations) }
    }

    suspend fun clear() {
        dataStore.updateData { null }
    }

    private companion object {
        /** Excluido de las copias de seguridad (res/xml). */
        const val FILE = "reservations_cache.json"
    }
}

private object CachedReservationsSerializer : Serializer<CachedReservations?> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: CachedReservations? = null

    override suspend fun readFrom(input: InputStream): CachedReservations? {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return null
        return try {
            json.decodeFromString(text)
        } catch (e: SerializationException) {
            throw CorruptionException("No se puede leer la copia de las reservas", e)
        }
    }

    override suspend fun writeTo(t: CachedReservations?, output: OutputStream) {
        output.write(t?.let { json.encodeToString(it) }.orEmpty().encodeToByteArray())
    }
}
