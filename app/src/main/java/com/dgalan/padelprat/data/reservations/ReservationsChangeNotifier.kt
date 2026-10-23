package com.dgalan.padelprat.data.reservations

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Avisa de que las reservas del usuario han podido cambiar (se ha reservado o anulado), para que la parrilla y
 * Mis reservas se recarguen sin que el usuario tenga que tirar para refrescar.
 */
@Singleton
class ReservationsChangeNotifier @Inject constructor() {

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}
