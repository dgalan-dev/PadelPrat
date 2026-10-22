package com.dgalan.padelprat.data.booking

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Saca la parrilla del HTML de `/customerzone/timetable`. Está aislado aquí porque es lo primero que se rompe si el
 * club cambia su web (ver docs/syltek-api.md, apartados 4.2 a 4.5).
 *
 * La parrilla viene como un objeto JavaScript (`var timetable = {…};`), no JSON: claves sin comillas, comillas
 * simples y `new Date(…)`. `org.json` acepta claves sin comillas y comillas simples, pero no claves numéricas
 * (`resources:{1477:{…}}`), así que a esas se les ponen comillas. Los `new Date(…)` se cambian por `null`, porque los
 * datos útiles están en los campos `*Utc`.
 */
object TimetableParser {

    private val TIMETABLE_REGEX = Regex("""var\s+timetable\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
    private val JS_DATE_REGEX = Regex("""new\s+Date\([^)]*\)""")

    /** Clave numérica sin comillas justo tras `{` o `,`: `{1477:` → `{"1477":`. */
    private val NUMERIC_KEY_REGEX = Regex("""([{,]\s*)(\d+)\s*:""")

    /** Devuelve `null` si la página no trae la parrilla o no tiene el formato esperado. */
    fun parse(html: String, date: LocalDate): DayTimetable? {
        val script = TIMETABLE_REGEX.find(html)?.groupValues?.get(1) ?: return null
        return try {
            val json = JSONObject(script.replace(JS_DATE_REGEX, "null").replace(NUMERIC_KEY_REGEX, "$1\"$2\":"))
            DayTimetable(
                date = date,
                customerId = json.getLong("idCustomer"),
                now = Instant.parse(json.getString("nowUtc")),
                maxAvailability = Instant.parse(json.getString("nowUtcMaxAvailability")),
                courts = parseCourts(json),
                bookings = json.getJSONArray("reservations").objects().map(::parseBooking).toList()
            )
        } catch (_: JSONException) {
            null
        } catch (_: RuntimeException) {
            // Fechas u horas con un formato inesperado
            null
        }
    }

    /** Las pistas, en el orden en que las lista el grupo de pádel. */
    private fun parseCourts(json: JSONObject): List<Court> {
        val resources = json.getJSONObject("resources")
        val ids = json.getJSONArray("groups").getJSONObject(0).getJSONArray("resources")
        return (0 until ids.length()).map { index ->
            val resource = resources.getJSONObject(ids.get(index).toString())
            // "Pista 1: Solán de Cabras": nombre y patrocinador; las pistas sin patrocinador no llevan ":"
            val fullName = resource.getString("name")
            Court(
                id = resource.getString("id").toInt(),
                name = fullName.substringBefore(':').trim(),
                sponsor = fullName.substringAfter(':', missingDelimiterValue = "").trim().ifEmpty { null },
                covered = resource.optString("subtype").startsWith("Cubierta", ignoreCase = true),
                periods = resource.getJSONArray("periods").objects().map { period ->
                    Period(
                        start = LocalTime.parse(period.getString("start")),
                        end = LocalTime.parse(period.getString("end")),
                        intervalMinutes = period.getInt("interval")
                    )
                }.toList()
            )
        }
    }

    private fun parseBooking(json: JSONObject) = Booking(
        id = json.getLong("id"),
        courtId = json.getJSONArray("idResource").getInt(0),
        start = Instant.parse(json.getString("startUtc")),
        end = Instant.parse(json.getString("endUtc")),
        customerId = if (json.isNull("idCustomer")) null else json.optLong("idCustomer")
    )

    private fun JSONArray.objects(): Sequence<JSONObject> = (0 until length()).asSequence().map(::getJSONObject)
}
