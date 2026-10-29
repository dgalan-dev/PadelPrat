package com.dgalan.padelprat.data.friends

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Recibe la push del amigo ("mis reservas han cambiado"). No trae datos: se leen sus reservas en Firestore y se avisa
 * de lo que haya cambiado de verdad. El token al que la envía lo publica [SharedReservationsRepository].
 */
@AndroidEntryPoint
class FriendMessagingService : FirebaseMessagingService() {

    @Inject lateinit var alerts: FriendAlertsRepository

    /** Se llama fuera del hilo principal y Android da unos 20 s: se espera como mucho 15. */
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data[FcmSender.DATA_TYPE] != FcmSender.TYPE_RESERVATIONS_CHANGED) return
        runBlocking { withTimeoutOrNull(15.seconds) { alerts.check() } }
    }
}
