package com.dgalan.padelprat.data.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recibe la alarma de una reserva programada y arranca [ScheduledBookingService]. Android permite arrancar un servicio
 * en primer plano desde una alarma exacta aunque la app esté en segundo plano.
 */
class ScheduledBookingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bookingId = intent.getStringExtra(EXTRA_BOOKING_ID) ?: return
        context.startForegroundService(
            ScheduledBookingService.intent(context, bookingId, intent.getBooleanExtra(EXTRA_DRY_RUN, false))
        )
    }

    companion object {
        private const val EXTRA_BOOKING_ID = "bookingId"
        private const val EXTRA_DRY_RUN = "dryRun"

        fun intent(context: Context, bookingId: String, dryRun: Boolean): Intent =
            Intent(context, ScheduledBookingReceiver::class.java)
                .putExtra(EXTRA_BOOKING_ID, bookingId)
                .putExtra(EXTRA_DRY_RUN, dryRun)
    }
}
