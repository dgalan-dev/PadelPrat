package com.dgalan.padelprat.data.booking

import com.dgalan.padelprat.core.network.Syltek
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Respuesta de un paso del flujo de reserva. */
sealed interface ReservationStep<out T> {
    data class Ok<T>(val value: T) : ReservationStep<T>

    /** El servidor ha redirigido a la parrilla: el hueco ya no es válido. */
    data object SlotUnavailable : ReservationStep<Nothing>

    /** La web no deja reservar y explica por qué (p. ej. el límite de reservas del club). */
    data class Rejected(val message: String) : ReservationStep<Nothing>

    /** Redirección al login: [com.dgalan.padelprat.core.network.SessionExpiredInterceptor] ya ha borrado la sesión. */
    data object SessionExpired : ReservationStep<Nothing>

    data object NetworkError : ReservationStep<Nothing>

    data object UnknownError : ReservationStep<Nothing>
}

/**
 * Peticiones del flujo de reserva (ver docs/syltek-api.md, apartado 5). **Ninguna se reintenta**: la pre-reserva
 * bloquea la pista y la confirmación crea una reserva real.
 */
class ReservationRemoteDataSource @Inject constructor(private val client: OkHttpClient, private val baseUrl: HttpUrl) {

    /** Bloquea la pista 8 minutos y devuelve el formulario para confirmar. */
    suspend fun preReserve(courtId: Int, start: Instant): ReservationStep<ReservationForm> {
        val local = start.atZone(Syltek.ZONE)
        val body = FormBody.Builder()
            .add("idResource", courtId.toString())
            .add("localDatetime", local.format(LOCAL_DATETIME))
            .add("fromWeekTimeTable", "false")
            .add("calendarDate", local.format(CALENDAR_DATE))
            .build()
        return post(Syltek.PRE_RESERVATION_PATH, body) { response -> response.toForm() }
    }

    /** Cambia la duración de la pre-reserva; la web devuelve el formulario actualizado. */
    suspend fun changeDuration(form: ReservationForm, minutes: Int): ReservationStep<ReservationForm> =
        post(Syltek.UPDATE_DURATION_PATH, form.body(minutes)) { response -> response.toForm() }

    /**
     * Envía la confirmación eligiendo "Pagar en el club" (`paymentPending=true`, ver docs/syltek-api.md, apartado
     * 5.4 bis): con cuentas que pagan, sin ese valor la reserva se queda pendiente y se anula sola. La web responde con
     * una redirección a `/booking/confirm`, pero eso no basta: hay que cargar esa página y ver si dice que la reserva
     * está hecha (ver [ReservationParsers.parseConfirmation]).
     */
    suspend fun confirm(form: ReservationForm): ReservationStep<ConfirmationPage> = post(
        Syltek.CONFIRM_RESERVATION_PATH,
        form.body(form.selectedDuration, paymentPending = PAY_AT_CLUB)
    ) { response ->
        val location = response.header("Location").orEmpty()
        if (response.isRedirect && location.contains(Syltek.CONFIRMATION_PATH, ignoreCase = true)) {
            ReservationStep.Ok(confirmationPage(location))
        } else {
            ReservationStep.UnknownError
        }
    }

    /** Se llama desde [post], que ya corre en el hilo de red. */
    private fun confirmationPage(location: String): ConfirmationPage {
        val url = baseUrl.resolve(location) ?: return ConfirmationPage.Unreachable
        return try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.isSuccessful) {
                    ReservationParsers.parseConfirmation(response.body.string())
                } else {
                    ConfirmationPage.Unreachable
                }
            }
        } catch (_: IOException) {
            ConfirmationPage.Unreachable
        }
    }

    private suspend fun <T> post(
        path: String,
        body: FormBody,
        handle: (Response) -> ReservationStep<T>
    ): ReservationStep<T> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl.resolve(path)!!).post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                val location = response.header("Location").orEmpty()
                when {
                    response.isRedirect && location.contains(Syltek.LOGIN_REDIRECT_PATH, ignoreCase = true) ->
                        ReservationStep.SessionExpired

                    response.isRedirect && location.contains(Syltek.TIMETABLE_PATH, ignoreCase = true) ->
                        ReservationStep.SlotUnavailable

                    else -> handle(response)
                }
            }
        } catch (_: IOException) {
            ReservationStep.NetworkError
        }
    }

    /** Sin formulario, la página puede traer el motivo (p. ej. el límite de reservas): se devuelve tal cual. */
    private fun Response.toForm(): ReservationStep<ReservationForm> {
        if (!isSuccessful) return ReservationStep.UnknownError
        val html = body.string()
        ReservationParsers.parseForm(html)?.let { return ReservationStep.Ok(it) }
        return ReservationParsers.parseErrorMessage(html)?.let { ReservationStep.Rejected(it) }
            ?: ReservationStep.UnknownError
    }

    /** Todos los campos del formulario tal cual, con la duración pedida y, si se indica, la forma de pago. */
    private fun ReservationForm.body(minutes: Int, paymentPending: String? = null): FormBody =
        FormBody.Builder().apply {
            fields.forEach { (name, value) ->
                when {
                    name == DURATION -> Unit
                    name == PAYMENT_PENDING && paymentPending != null -> Unit
                    else -> add(name, value)
                }
            }
            add(DURATION, minutes.toString())
            if (paymentPending != null) add(PAYMENT_PENDING, paymentPending)
        }.build()

    private companion object {
        const val DURATION = "duration"
        const val PAYMENT_PENDING = "paymentPending"

        /** Valor del botón "Pagar en el club". */
        const val PAY_AT_CLUB = "true"

        /** Hora local de la pre-reserva: `28/10/2026 10:30`. */
        val LOCAL_DATETIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

        /** Día sin ceros a la izquierda: `2026/10/28`, `2026/1/5`. */
        val CALENDAR_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")
    }
}
