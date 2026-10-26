package com.dgalan.padelprat.data.scheduled

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Programaciones que el servicio está ejecutando ahora mismo, en este proceso. Evita lanzar dos veces la misma (p. ej.
 * si se abre la app mientras espera a la apertura) y permite saber que una marcada como empezada sigue viva. Solo
 * vive en memoria: si el proceso muere, se vacía, que es justo lo que interesa saber.
 */
@Singleton
class ScheduledRunRegistry @Inject constructor() {

    private val running = MutableStateFlow(emptySet<String>())

    /** Las que se están ejecutando; la actualización de la app espera a que no quede ninguna. */
    val runningIds: StateFlow<Set<String>> = running.asStateFlow()

    /** `false` si ya se está ejecutando. */
    @Synchronized
    fun tryStart(id: String): Boolean {
        if (id in running.value) return false
        running.value += id
        return true
    }

    @Synchronized
    fun finish(id: String) {
        running.value -= id
    }

    fun isRunning(id: String): Boolean = id in running.value
}
