package com.dgalan.padelprat.data.friends

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.dgalan.padelprat.MainActivity
import com.dgalan.padelprat.R
import com.dgalan.padelprat.data.reservations.Reservation
import com.dgalan.padelprat.ui.format.SPANISH
import com.dgalan.padelprat.ui.format.hourText
import com.dgalan.padelprat.ui.format.longDate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Avisos de las reservas del amigo, en su propio canal para poder silenciarlos aparte en los ajustes de Android. */
@Singleton
class FriendNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notification_channel_friend),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    /** [name] es el del amigo; [cancelled] si ha anulado la reserva en lugar de hacerla. */
    fun show(name: String?, reservation: Reservation, cancelled: Boolean) {
        val friend = name ?: context.getString(R.string.reservations_friend_default)
        val title = context.getString(
            if (cancelled) R.string.notification_friend_cancelled_title else R.string.notification_friend_booked_title,
            friend
        )
        val day = reservation.start.toLocalDate().longDate().replaceFirstChar { it.lowercase(SPANISH) }
        val hours = context.getString(
            R.string.reminder_hours,
            reservation.start.toLocalTime().hourText(),
            reservation.end.toLocalTime().hourText()
        )
        val court = reservation.court?.let { court ->
            court.sponsor?.let { context.getString(R.string.reminder_court_sponsor, court.name, it) } ?: court.name
        }
        val text = listOfNotNull(day, hours, court).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openReservations())
            .build()
        // Si anula una que acababa de reservar, el aviso de anular sustituye al de reservar
        manager.notify("friend-${reservation.id}".hashCode(), notification)
    }

    private fun openReservations(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_RESERVATIONS,
        MainActivity.openReservationsIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private companion object {
        const val CHANNEL = "friend_reservations"

        /** Distinto del de los avisos de las reservas propias. */
        const val REQUEST_OPEN_RESERVATIONS = 2
    }
}
