package com.dgalan.padelprat.data.scheduled

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.dgalan.padelprat.data.reservations.ReservationsChangeNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que intenta una reserva programada en la apertura de su día. Dura unos minutos (desde
 * [ScheduledBookingRunner.WAKE_UP_BEFORE] antes de la apertura hasta que reserva o se rinde) y mantiene la CPU
 * despierta solo ese rato.
 */
@AndroidEntryPoint
class ScheduledBookingService : Service() {

    @Inject lateinit var repository: ScheduledBookingRepository

    @Inject lateinit var runner: ScheduledBookingRunner

    @Inject lateinit var notifications: ScheduledBookingNotifications

    @Inject lateinit var changeNotifier: ReservationsChangeNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bookingId = intent?.getStringExtra(EXTRA_BOOKING_ID)
        val dryRun = intent?.getBooleanExtra(EXTRA_DRY_RUN, false) ?: false
        ServiceCompat.startForeground(
            this,
            ScheduledBookingNotifications.RUNNING_NOTIFICATION_ID,
            notifications.running(null, dryRun),
            // El tipo de servicio existe desde Android 10; antes no se indica
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        if (bookingId == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        running++
        scope.launch {
            try {
                val booking = repository.all().firstOrNull { it.id == bookingId } ?: return@launch
                getSystemService(android.app.NotificationManager::class.java)
                    .notify(
                        ScheduledBookingNotifications.RUNNING_NOTIFICATION_ID,
                        notifications.running(booking, dryRun)
                    )
                val run = runner.run(booking, dryRun)
                notifications.showResult(booking, run)
                // La programación ya se ha intentado: se quita (en modo de prueba se deja para poder repetirla)
                if (!dryRun) {
                    repository.remove(booking.id)
                    changeNotifier.notifyChanged()
                }
            } finally {
                running--
                stopIfIdle()
            }
        }
        // Si el sistema mata el servicio no se reintenta solo: una reserva a medias no se repite
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PadelPrat:ScheduledBooking")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun stopIfIdle() {
        if (running > 0) return
        wakeLock?.takeIf { it.isHeld }?.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_BOOKING_ID = "bookingId"
        private const val EXTRA_DRY_RUN = "dryRun"

        /** Margen de sobra: 2 min de espera hasta la apertura + 2 min de consultas + la reserva. */
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 1000L

        fun intent(context: Context, bookingId: String, dryRun: Boolean): Intent =
            Intent(context, ScheduledBookingService::class.java)
                .putExtra(EXTRA_BOOKING_ID, bookingId)
                .putExtra(EXTRA_DRY_RUN, dryRun)
    }
}
