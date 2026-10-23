package com.dgalan.padelprat.data.scheduled

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dgalan.padelprat.MainActivity
import com.dgalan.padelprat.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Notificaciones de las reservas programadas: la de "esperando" mientras corre el servicio y la del resultado. */
@Singleton
class ScheduledBookingNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_RUNNING,
                    context.getString(R.string.notification_channel_running),
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    CHANNEL_RESULT,
                    context.getString(R.string.notification_channel_result),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        )
    }

    /** "Esperando a que abra el sábado 31…", mientras el servicio espera la apertura y reserva. */
    fun running(booking: ScheduledBooking?, dryRun: Boolean): Notification =
        NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(
                    if (dryRun) R.string.notification_running_test_title else R.string.notification_running_title
                )
            )
            .setContentText(booking?.let { context.getString(R.string.notification_running_text, it.date.longDate()) })
            .setOngoing(true)
            .setContentIntent(openApp())
            .build()

    fun showResult(booking: ScheduledBooking, run: ScheduledRun) {
        val time = booking.start.format(TIME)
        val day = booking.date.longDate()
        val (title, text) = when (val result = run.result) {
            is ScheduledRunResult.Booked -> if (result.dryRun) {
                context.getString(R.string.notification_result_test_title) to
                    context.getString(R.string.notification_result_test_text, result.court.name, time)
            } else {
                context.getString(R.string.notification_result_booked_title) to
                    context.getString(R.string.notification_result_booked_text, result.court.name, day, time)
            }

            ScheduledRunResult.NoCourtFree -> failed(R.string.notification_result_no_court, day, time)

            ScheduledRunResult.LimitReached -> failed(R.string.notification_result_limit, day, time)

            ScheduledRunResult.LoggedOut -> failed(R.string.notification_result_logged_out, day, time)

            ScheduledRunResult.NotOpened -> failed(R.string.notification_result_not_opened, day, time)

            ScheduledRunResult.Uncertain -> context.getString(R.string.notification_result_uncertain_title) to
                context.getString(R.string.notification_result_uncertain_text, day, time)

            ScheduledRunResult.Failed -> failed(R.string.notification_result_failed, day, time)

            ScheduledRunResult.Missed -> failed(R.string.notification_result_missed, day, time)
        }
        val clockWarning = run.clockOffset?.let {
            "\n" + context.getString(R.string.notification_clock_warning, abs(it.seconds))
        }.orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + clockWarning))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        manager.notify(booking.id.hashCode(), notification)
    }

    private fun failed(reason: Int, day: String, time: String): Pair<String, String> =
        context.getString(R.string.notification_result_failed_title) to context.getString(reason, day, time)

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun java.time.LocalDate.longDate(): String = format(LONG_DATE)

    companion object {
        const val RUNNING_NOTIFICATION_ID = 1001
        private const val CHANNEL_RUNNING = "scheduled_running"
        private const val CHANNEL_RESULT = "scheduled_result"
        private val SPANISH = Locale.forLanguageTag("es-ES")
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)
    }
}
