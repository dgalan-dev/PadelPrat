package com.dgalan.padelprat.data.booking

import com.dgalan.padelprat.core.network.Syltek
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface TimetableResult {
    data class Success(val timetable: DayTimetable) : TimetableResult

    /** El servidor ha rechazado la sesión; la app ya está volviendo al login. */
    data object SessionExpired : TimetableResult

    data object NetworkError : TimetableResult

    /** El servidor ha respondido con un error. */
    data object UnknownError : TimetableResult

    /**
     * La página no trae la parrilla o ha cambiado de formato. Un día aún cerrado también responde así (ver
     * [com.dgalan.padelprat.data.scheduled.ScheduledBookingRunner]).
     */
    data object FormatChanged : TimetableResult
}

/** Parrilla de un día (ver docs/syltek-api.md, apartado 4.1). Requiere sesión. */
class TimetableRemoteDataSource @Inject constructor(private val client: OkHttpClient, private val baseUrl: HttpUrl) {

    suspend fun timetable(date: LocalDate): TimetableResult = withContext(Dispatchers.IO) {
        val url = baseUrl.newBuilder()
            .addPathSegments(Syltek.TIMETABLE_PATH)
            .addQueryParameter("type", Syltek.PADEL_RESOURCE_TYPE)
            .build()
        val body = FormBody.Builder()
            .add("type", Syltek.PADEL_RESOURCE_TYPE)
            .add("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .build()
        val request = Request.Builder().url(url).post(body).build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    // SessionExpiredInterceptor ya ha borrado la sesión
                    response.isRedirect -> TimetableResult.SessionExpired

                    !response.isSuccessful -> TimetableResult.UnknownError

                    else -> {
                        val html = response.body.string()
                        // El análisis del HTML se hace fuera del hilo de red
                        withContext(Dispatchers.Default) { TimetableParser.parse(html, date) }
                            ?.let(TimetableResult::Success)
                            ?: TimetableResult.FormatChanged
                    }
                }
            }
        } catch (_: IOException) {
            TimetableResult.NetworkError
        }
    }
}
