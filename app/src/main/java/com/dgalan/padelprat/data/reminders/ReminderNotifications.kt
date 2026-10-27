package com.dgalan.padelprat.data.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.dgalan.padelprat.MainActivity
import com.dgalan.padelprat.R
import com.dgalan.padelprat.data.reservations.ReservationCourt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Notificaciones de los avisos, en su propio canal para poder silenciarlas aparte en los ajustes de Android. */
@Singleton
class ReminderNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    /** [court] es la pista que da ahora la web, o `null` si no se ha podido comprobar. */
    fun show(reminder: Reminder, kind: ReminderKind, court: ReservationCourt?) {
        val start = reminder.start.format(TIME)
        val hours = context.getString(R.string.reminder_hours, start, reminder.end.format(TIME))
        val courtText = court?.let { court ->
            court.sponsor?.let { context.getString(R.string.reminder_court_sponsor, court.name, it) } ?: court.name
        }
        val (title, text) = when (kind) {
            ReminderKind.Match -> context.getString(R.string.reminder_match_title, start) to
                listOfNotNull(hours, courtText).joinToString(" · ")

            ReminderKind.CancelMorning -> context.getString(R.string.reminder_cancel_title, start) to
                context.getString(R.string.reminder_cancel_morning_text)

            ReminderKind.CancelEvening -> context.getString(R.string.reminder_cancel_title, start) to
                context.getString(R.string.reminder_cancel_evening_text)
        }
        // En los de anular, la pista va en una segunda línea al desplegar
        val bigText = if (kind == ReminderKind.Match) text else listOfNotNull(text, courtText).joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setAutoCancel(true)
            .setContentIntent(openReservations())
            .build()
        // El aviso de la noche sustituye al de la mañana, si sigue sin quitar
        val group = if (kind == ReminderKind.Match) "match" else "cancel"
        manager.notify("reminder-${reminder.reservationId}-$group".hashCode(), notification)
    }

    private fun openReservations(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_RESERVATIONS,
        MainActivity.openReservationsIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private companion object {
        const val CHANNEL = "reminders"
        const val REQUEST_OPEN_RESERVATIONS = 1
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
