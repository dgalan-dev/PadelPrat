package com.dgalan.padelprat.data.friends

import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.reservations.Reservation
import com.dgalan.padelprat.data.reservations.ReservationCourt
import com.dgalan.padelprat.data.reservations.ReservationStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.google.firebase.messaging.FirebaseMessaging
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Las próximas reservas del amigo. [reservations] van como reservas normales, sin importe y sin poder anularlas.
 * [updatedAt] es cuándo las publicó su app por última vez; `null` si todavía no ha publicado nada.
 */
data class FriendReservations(val name: String?, val updatedAt: Instant?, val reservations: List<Reservation>)

/** Las reservas del amigo junto a su uid, para saber si es el mismo de la última vez. */
data class Friend(val uid: String, val reservations: FriendReservations)

/**
 * Reservas compartidas en Firestore (ver docs/plan-reservas-amigo.md): cada app publica sus próximas reservas reales
 * en `reservations/{uid}` y lee las del otro. Solo con la sesión de Google activada; si no, no hace nada. Con el
 * documento va el token de FCM del móvil, y cuando cambian las reservas se envía una push al otro con [FcmSender].
 */
@Singleton
class SharedReservationsRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val fcmSender: FcmSender
) {

    /** Publicar no hace esperar a nadie: sin conexión, Firestore guarda la escritura y la manda al volver. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Las publicaciones van de una en una: cada una se compara con la anterior. */
    private val mutex = Mutex()

    /** Lo último que se sabe que está en Firestore, para no escribir si no ha cambiado. `null` hasta leerlo. */
    private var published: List<SharedReservation>? = null
    private var publishedUid: String? = null
    private var publishedToken: String? = null

    /** La pista solo viene en el detalle de cada reserva: se recuerda para no borrarla al publicar desde el listado. */
    private val knownCourts = mutableMapOf<Long, ReservationCourt>()

    /** Con el listado recién leído de la web. Solo las próximas activas; sin conexión, se manda al volver. */
    fun publish(reservations: List<Reservation>) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            mutex.withLock {
                if (publishedUid != uid) seed(uid)
                publishToken(uid)
                val now = LocalDateTime.now(Syltek.ZONE)
                val upcoming = reservations
                    .filter { it.upcoming && it.status == ReservationStatus.Booked && it.end > now }
                    .onEach { reservation -> reservation.court?.let { knownCourts[reservation.id] = it } }
                    .map { SharedReservation(it.id, it.start, it.durationMinutes, it.court ?: knownCourts[it.id]) }
                    .sortedBy { it.start }
                if (upcoming == published) return@withLock
                try {
                    document(uid).set(
                        mapOf(UPCOMING to upcoming.map { it.toMap() }, UPDATED_AT to FieldValue.serverTimestamp()),
                        // Sin tocar los demás campos del documento
                        SetOptions.merge()
                    ).await()
                    // Solo si hay alguna nueva o falta alguna: que cambie la pista no merece avisar
                    val changed = published?.map { it.id }?.toSet() != upcoming.map { it.id }.toSet()
                    published = upcoming
                    if (changed) notifyFriend(uid)
                } catch (_: FirebaseFirestoreException) {
                    // P. ej. sin alta en members todavía: se volverá a intentar con el siguiente listado
                }
            }
        }
    }

    /**
     * El token de FCM del móvil, para que el amigo pueda enviarle la push. Solo se escribe si ha cambiado, y se
     * comprueba con cada listado, así que un token renovado se publica la siguiente vez que se abre la app.
     */
    // FCM 26 propone register() con el Firebase Installation ID, que hay que activar aparte; el token sigue funcionando
    @Suppress("DEPRECATION")
    private suspend fun publishToken(uid: String) {
        val token = try {
            messaging.token.await()
        } catch (_: IOException) {
            return
        }
        if (token == publishedToken) return
        try {
            document(uid).set(mapOf(FCM_TOKEN to token), SetOptions.merge()).await()
            publishedToken = token
        } catch (_: FirebaseFirestoreException) {
            // Se volverá a intentar con el siguiente listado
        }
    }

    /** Push al amigo para que mire las reservas. Una sola vez, sin reintentos. */
    private suspend fun notifyFriend(uid: String) {
        if (!fcmSender.isConfigured) return
        val token = try {
            val friend = friendMember(uid) ?: return
            document(friend.id).get().await().getString(FCM_TOKEN)
        } catch (_: FirebaseFirestoreException) {
            null
        } ?: return
        fcmSender.sendReservationsChanged(token)
    }

    /**
     * Las reservas del amigo tal como están ahora en el servidor, para los avisos. `null` sin sesión de Google, sin
     * amigo o sin conexión.
     */
    suspend fun fetchFriend(): Friend? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val friend = friendMember(uid) ?: return null
            val snapshot = document(friend.id).get(Source.SERVER).await()
            Friend(
                uid = friend.id,
                reservations = FriendReservations(
                    name = friend.getString(NAME),
                    updatedAt = snapshot.getTimestamp(UPDATED_AT)?.toInstant(),
                    reservations = snapshot.toSharedReservations().map { it.toReservation() }
                )
            )
        } catch (_: FirebaseFirestoreException) {
            null
        }
    }

    /** El otro miembro: solo hay dos. */
    private suspend fun friendMember(uid: String): DocumentSnapshot? =
        firestore.collection(FirestoreCollections.MEMBERS).get().await().documents.firstOrNull { it.id != uid }

    /** Lo que ya había publicado, para comparar y para recuperar las pistas tras abrir la app. */
    private suspend fun seed(uid: String) {
        publishedUid = uid
        knownCourts.clear()
        publishedToken = null
        published = try {
            val snapshot = document(uid).get().await()
            publishedToken = snapshot.getString(FCM_TOKEN)
            val reservations = snapshot.toSharedReservations()
            reservations.forEach { reservation -> reservation.court?.let { knownCourts[reservation.id] = it } }
            reservations
        } catch (_: FirebaseFirestoreException) {
            null
        }
    }

    /**
     * Al desactivar las reservas compartidas o cerrar sesión: borra lo publicado, para que el otro no vea reservas
     * que ya no se actualizan. Tiene que ir antes de cerrar la sesión de Firebase. Sin conexión no espera más de 5 s.
     */
    suspend fun unpublish() {
        val uid = auth.currentUser?.uid ?: return
        scope.coroutineContext.cancelChildren()
        withTimeoutOrNull(5.seconds) {
            mutex.withLock {
                published = null
                publishedUid = null
                publishedToken = null
                knownCourts.clear()
                try {
                    document(uid).delete().await()
                } catch (_: FirebaseFirestoreException) {
                    // Sin alta en members no había nada publicado
                }
            }
        }
    }

    /**
     * Las reservas del otro miembro, al momento cada vez que las publica. `null` sin sesión de Google, sin alta en
     * members o si todavía no hay otro miembro. Se vuelve a calcular al iniciar o cerrar la sesión de Google.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val friendReservations: Flow<FriendReservations?>
        get() = currentUid().flatMapLatest { uid -> if (uid == null) flowOf(null) else friendReservations(uid) }

    private fun friendReservations(uid: String): Flow<FriendReservations?> = flow {
        val friend = friendMember(uid)
        if (friend == null) {
            emit(null)
            return@flow
        }
        val name = friend.getString(NAME)
        emitAll(
            document(friend.id).snapshots().map { snapshot ->
                FriendReservations(
                    name = name,
                    updatedAt = snapshot.getTimestamp(UPDATED_AT)?.toInstant(),
                    reservations = snapshot.toSharedReservations().map { it.toReservation() }
                )
            }
        )
    }.catch { e -> if (e is FirebaseFirestoreException) emit(null) else throw e }

    private fun currentUid(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    private fun document(uid: String) = firestore.collection(FirestoreCollections.RESERVATIONS).document(uid)

    private companion object {
        const val NAME = "name"
        const val UPCOMING = "upcoming"
        const val UPDATED_AT = "updatedAt"
        const val FCM_TOKEN = "fcmToken"
    }
}

/** Lo que se publica de cada reserva: nada de importes ni datos de la cuenta. [start] en hora del club. */
private data class SharedReservation(
    val id: Long,
    val start: LocalDateTime,
    val durationMinutes: Int,
    val court: ReservationCourt?
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "startUtc" to Timestamp(start.atZone(Syltek.ZONE).toInstant()),
        "durationMinutes" to durationMinutes,
        "court" to court?.name,
        "sponsor" to court?.sponsor
    )

    fun toReservation() = Reservation(
        id = id,
        status = ReservationStatus.Booked,
        start = start,
        durationMinutes = durationMinutes,
        total = null,
        canCancel = false,
        upcoming = true,
        court = court
    )
}

/** Se lee a mano (sin `toObject()`) para no necesitar reglas de R8. Lo que no se entiende se descarta. */
private fun DocumentSnapshot.toSharedReservations(): List<SharedReservation> =
    (get("upcoming") as? List<*>).orEmpty().mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val id = (map["id"] as? Number)?.toLong() ?: return@mapNotNull null
        val start = (map["startUtc"] as? Timestamp)?.toInstant() ?: return@mapNotNull null
        val duration = (map["durationMinutes"] as? Number)?.toInt() ?: return@mapNotNull null
        SharedReservation(
            id = id,
            start = LocalDateTime.ofInstant(start, Syltek.ZONE),
            durationMinutes = duration,
            court = (map["court"] as? String)?.let { ReservationCourt(it, map["sponsor"] as? String) }
        )
    }
