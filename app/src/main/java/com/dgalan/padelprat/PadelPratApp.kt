package com.dgalan.padelprat

import android.app.Application
import com.dgalan.padelprat.data.reminders.ReminderNotifications
import com.dgalan.padelprat.data.scheduled.ScheduledBookingNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PadelPratApp : Application() {

    @Inject lateinit var scheduledNotifications: ScheduledBookingNotifications

    @Inject lateinit var reminderNotifications: ReminderNotifications

    override fun onCreate() {
        super.onCreate()
        scheduledNotifications.createChannels()
        reminderNotifications.createChannel()
    }
}
