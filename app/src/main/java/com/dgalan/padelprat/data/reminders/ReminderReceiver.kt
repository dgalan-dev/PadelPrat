package com.dgalan.padelprat.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dgalan.padelprat.data.auth.AuthRepository
import com.dgalan.padelprat.data.reservations.ReservationCheck
import com.dgalan.padelprat.data.reservations.ReservationsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Recibe la alarma de un aviso. Antes de avisar mira en Mis reservas que la reserva siga activa (solo lee) y, de paso,
 * qué pista tiene ahora. Si no se puede comprobar (sin conexión o sin sesión), avisa igual con lo guardado: es peor
 * perderse un partido que recibir el aviso de uno anulado.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository

    @Inject lateinit var reservationsRepository: ReservationsRepository

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var notifications: ReminderNotifications

    override fun onReceive(context: Context, intent: Intent) {
        val reservationId = intent.getLongExtra(EXTRA_RESERVATION_ID, -1)
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { name ->
            ReminderKind.entries.firstOrNull { it.name == name }
        }
        if (reservationId < 0 || kind == null) return
        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                // Sin aviso guardado es que la reserva ya no está (o se ha cerrado sesión)
                val reminder = repository.find(reservationId) ?: return@launch
                if (!repository.isEnabled(kind)) return@launch
                val check = scope.async {
                    if (authRepository.isLoggedIn.first()) {
                        reservationsRepository.check(reservationId)
                    } else {
                        ReservationCheck.Unknown
                    }
                }
                // La petición no se puede cortar a medias: si tarda, se avisa sin esperarla
                val result = withTimeoutOrNull(CHECK_TIMEOUT_MS) { check.await() } ?: ReservationCheck.Unknown
                when (result) {
                    is ReservationCheck.Active -> notifications.show(reminder, kind, result.court)
                    ReservationCheck.Unknown -> notifications.show(reminder, kind, court = null)
                    ReservationCheck.Cancelled -> Unit
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_RESERVATION_ID = "reservationId"
        private const val EXTRA_KIND = "kind"

        /** Android da unos 10 s a un receptor; se deja margen. */
        private const val CHECK_TIMEOUT_MS = 8_000L

        fun intent(context: Context, reservationId: Long, kind: ReminderKind): Intent =
            Intent(context, ReminderReceiver::class.java)
                .putExtra(EXTRA_RESERVATION_ID, reservationId)
                .putExtra(EXTRA_KIND, kind.name)
    }
}
