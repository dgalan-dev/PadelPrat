package com.dgalan.padelprat.data.scheduled

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pone al día las alarmas de las programaciones. Android las borra al forzar la detención de la app, al reiniciar el
 * móvil y a veces al actualizarla, y pueden quedar mal tras un cambio de hora; por eso se llama al abrir la app y desde
 * [RescheduleReceiver]. Para cada programación:
 * - antes de la hora de despertar: se vuelve a poner su alarma;
 * - el día ya ha abierto pero el partido aún no ha empezado (el móvil estaba apagado a las 9:00): se intenta ya;
 * - el partido ya ha empezado: se quita y se avisa;
 * - se empezó a reservar y el servicio murió a medias: se quita sin reintentar (podría estar hecha) y se avisa.
 */
@Singleton
class ScheduledBookingRescheduler @Inject constructor(
    private val repository: ScheduledBookingRepository,
    private val alarms: ScheduledBookingAlarms,
    private val registry: ScheduledRunRegistry,
    private val notifications: ScheduledBookingNotifications
) {

    suspend fun reconcile() {
        val now = Instant.now()
        for (booking in repository.all()) {
            // La que está en marcha en este proceso ya tiene quien se ocupe
            if (registry.isRunning(booking.id)) continue
            when {
                booking.bookingStarted -> giveUp(booking, ScheduledRunResult.Uncertain)
                now.isBefore(booking.opensAt.minus(ScheduledBookingRunner.WAKE_UP_BEFORE)) -> alarms.schedule(booking)
                now.isBefore(booking.startsAt) -> alarms.scheduleSoon(booking)
                else -> giveUp(booking, ScheduledRunResult.Missed)
            }
        }
    }

    private suspend fun giveUp(booking: ScheduledBooking, result: ScheduledRunResult) {
        notifications.showResult(booking, ScheduledRun(result, clockOffset = null))
        repository.remove(booking.id)
    }
}
