package com.dgalan.padelprat.core.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Avisa de que el servidor ha dado la sesión por caducada, para explicárselo al usuario en el login. Solo vive en
 * memoria: si se cierra la app, al volver a abrirla ya no hay sesión y se ve el login sin aviso.
 */
@Singleton
class SessionExpiredNotifier @Inject constructor() {

    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    fun notifyExpired() {
        _expired.value = true
    }

    fun reset() {
        _expired.value = false
    }
}
