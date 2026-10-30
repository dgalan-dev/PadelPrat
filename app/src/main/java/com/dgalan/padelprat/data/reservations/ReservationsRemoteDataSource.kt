package com.dgalan.padelprat.data.reservations

import com.dgalan.padelprat.core.network.Syltek
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Respuesta de una petición de Mis reservas. */
sealed interface ReservationsStep<out T> {
    data class Ok<T>(val value: T) : ReservationsStep<T>

    /** Redirección al login: [com.dgalan.padelprat.core.network.SessionExpiredInterceptor] ya ha borrado la sesión. */
    data object SessionExpired : ReservationsStep<Nothing>

    data object NetworkError : ReservationsStep<Nothing>

    data object UnknownError : ReservationsStep<Nothing>

    /** La página ha llegado bien pero no se entiende: la web del club ha cambiado. */
    data object FormatChanged : ReservationsStep<Nothing>
}

/** Peticiones de Mis reservas (ver docs/syltek-api.md, apartado 6). */
class ReservationsRemoteDataSource @Inject constructor(private val client: OkHttpClient, private val baseUrl: HttpUrl) {

    suspend fun list(): ReservationsStep<List<Reservation>> = get(Syltek.RESERVATIONS_PATH) { response ->
        if (!response.isSuccessful) return@get ReservationsStep.UnknownError
        ReservationsParser.parseList(response.body.string())?.let { ReservationsStep.Ok(it) }
            ?: ReservationsStep.FormatChanged
    }

    /** Pista de una reserva; `null` si no se ha podido leer (la tarjeta se muestra igualmente, sin pista). */
    suspend fun court(id: Long): ReservationCourt? {
        val step = get("${Syltek.RESERVATION_DETAIL_PATH}$id") { response ->
            if (response.isSuccessful) {
                ReservationsStep.Ok(ReservationsParser.parseDetailCourt(response.body.string()))
            } else {
                ReservationsStep.UnknownError
            }
        }
        return (step as? ReservationsStep.Ok)?.value
    }

    /**
     * Anula una reserva. **Es un GET que borra datos: se lanza una sola vez y nunca se reintenta.** La web responde
     * con una redirección al listado y sin ningún mensaje, así que el resultado hay que comprobarlo en el listado.
     */
    suspend fun cancel(id: Long): ReservationsStep<Unit> = get("${Syltek.CANCEL_RESERVATION_PATH}$id") { response ->
        if (response.isRedirect || response.isSuccessful) ReservationsStep.Ok(Unit) else ReservationsStep.UnknownError
    }

    private suspend fun <T> get(path: String, handle: (Response) -> ReservationsStep<T>): ReservationsStep<T> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(baseUrl.resolve(path)!!).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    val location = response.header("Location").orEmpty()
                    if (response.isRedirect && location.contains(Syltek.LOGIN_REDIRECT_PATH, ignoreCase = true)) {
                        ReservationsStep.SessionExpired
                    } else {
                        handle(response)
                    }
                }
            } catch (_: IOException) {
                ReservationsStep.NetworkError
            }
        }
}
