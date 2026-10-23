package com.dgalan.padelprat.data.scheduled

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programaciones que el servicio está ejecutando ahora mismo, en este proceso. Evita lanzar dos veces la misma (p. ej.
 * si se abre la app mientras espera a la apertura) y permite saber que una marcada como empezada sigue viva. Solo
 * vive en memoria: si el proceso muere, se vacía, que es justo lo que interesa saber.
 */
@Singleton
class ScheduledRunRegistry @Inject constructor() {

    private val running = mutableSetOf<String>()

    /** `false` si ya se está ejecutando. */
    @Synchronized
    fun tryStart(id: String): Boolean = running.add(id)

    @Synchronized
    fun finish(id: String) {
        running.remove(id)
    }

    @Synchronized
    fun isRunning(id: String): Boolean = id in running
}
