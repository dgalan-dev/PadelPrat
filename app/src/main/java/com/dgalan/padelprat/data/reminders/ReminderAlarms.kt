package com.dgalan.padelprat.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarmas de los avisos. A diferencia de las de la reserva programada no son de despertador (`setAlarmClock`): no hace
 * falta tanta precisión y así Android no enseña el icono de alarma durante días.
 */
@Singleton
class ReminderAlarms @Inject constructor(@ApplicationContext private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(reservationId: Long, kind: ReminderKind, at: Instant) {
        val operation = operation(reservationId, kind)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        } catch (_: SecurityException) {
            // Sin permiso de alarmas exactas: puede llegar unos minutos tarde, pero llega
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        }
    }

    fun cancel(reservationId: Long, kind: ReminderKind) {
        alarmManager.cancel(operation(reservationId, kind))
    }

    private fun operation(reservationId: Long, kind: ReminderKind): PendingIntent = PendingIntent.getBroadcast(
        context,
        // Una alarma por reserva y tipo de aviso
        "reminder-$reservationId-$kind".hashCode(),
        ReminderReceiver.intent(context, reservationId, kind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
