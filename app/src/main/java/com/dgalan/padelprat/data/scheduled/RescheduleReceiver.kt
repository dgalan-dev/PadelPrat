package com.dgalan.padelprat.data.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Vuelve a poner las alarmas de las reservas programadas cuando Android las ha podido perder o desajustar: al
 * reiniciar el móvil, al actualizar la app, al cambiar la hora o la zona horaria y al conceder el permiso de alarmas
 * exactas (ver [ScheduledBookingRescheduler]).
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var rescheduler: ScheduledBookingRescheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                rescheduler.reconcile()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** Las mismas del manifest. */
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, que es de la API 31
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}
