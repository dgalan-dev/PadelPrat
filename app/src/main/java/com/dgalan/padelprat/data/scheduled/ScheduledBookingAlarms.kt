package com.dgalan.padelprat.data.scheduled

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dgalan.padelprat.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarmas de las reservas programadas. Se usa `setAlarmClock`, la de los despertadores: el sistema no la retrasa ni
 * en reposo profundo y saca al móvil del modo Doze, así que a la hora de apertura hay red y CPU. Como efecto
 * secundario, Android muestra el icono de alarma mientras haya una programada.
 */
private const val SOON_SECONDS = 5L

@Singleton
class ScheduledBookingAlarms @Inject constructor(@ApplicationContext private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Despierta la app [ScheduledBookingRunner.WAKE_UP_BEFORE] antes de la apertura del día. */
    fun schedule(booking: ScheduledBooking) {
        val triggerAt = booking.opensAt.minus(ScheduledBookingRunner.WAKE_UP_BEFORE)
        scheduleAt(booking.id, triggerAt, dryRun = false)
    }

    /** El día ya está abierto (p. ej. el móvil estaba apagado a las 9:00): se intenta en unos segundos. */
    fun scheduleSoon(booking: ScheduledBooking) {
        scheduleAt(booking.id, Instant.now().plusSeconds(SOON_SECONDS), dryRun = false)
    }

    /** Modo de prueba (solo debug): lanza la programación al momento indicado, sin reservar nada. */
    fun scheduleDryRun(booking: ScheduledBooking, at: Instant) {
        scheduleAt(booking.id, at, dryRun = true)
    }

    fun cancel(bookingId: String) {
        alarmManager.cancel(operation(bookingId, dryRun = false))
        alarmManager.cancel(operation(bookingId, dryRun = true))
    }

    private fun scheduleAt(bookingId: String, at: Instant, dryRun: Boolean) {
        // Al tocar el icono de la alarma en el sistema se abre la app
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val info = AlarmManager.AlarmClockInfo(at.toEpochMilli(), show)
        try {
            alarmManager.setAlarmClock(info, operation(bookingId, dryRun))
        } catch (_: SecurityException) {
            // Sin permiso de alarmas exactas (el usuario puede quitarlo en Ajustes): mejor una alarma que pueda llegar
            // algo tarde que ninguna
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation(bookingId, dryRun))
        }
    }

    private fun operation(bookingId: String, dryRun: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        // Una alarma por programación (y otra distinta para su prueba)
        (bookingId + dryRun).hashCode(),
        ScheduledBookingReceiver.intent(context, bookingId, dryRun),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
