package com.dgalan.padelprat.data.friends

import com.dgalan.padelprat.BuildConfig
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Envía al amigo la push de "tus reservas han cambiado" desde la propia app, con la clave de la cuenta de servicio
 * `padelprat-fcm` (solo tiene el rol de FCM; ver docs/plan-reservas-amigo.md). La push no lleva datos: su app los lee
 * de Firestore, así que una push falsa no puede mostrar nada.
 */
@Singleton
class FcmSender @Inject constructor(@FcmClient private val client: OkHttpClient) {

    /** El token de acceso dura 1 h: se reutiliza mientras no caduque. */
    private val mutex = Mutex()
    private var accessToken: String? = null
    private var accessTokenExpiresAt: Instant = Instant.EPOCH

    /** Sin la clave en el build (p. ej. en el CI de debug) no se envía nada. */
    val isConfigured: Boolean
        get() = BuildConfig.FCM_PROJECT_ID.isNotEmpty() &&
            BuildConfig.FCM_CLIENT_EMAIL.isNotEmpty() &&
            BuildConfig.FCM_PRIVATE_KEY.isNotEmpty()

    /** Una sola vez, sin reintentos: si se pierde, el amigo lo verá al abrir Mis reservas. */
    suspend fun sendReservationsChanged(fcmToken: String) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            try {
                val bearer = accessToken() ?: return@withContext
                val body = buildJsonObject {
                    putJsonObject("message") {
                        put("token", fcmToken)
                        putJsonObject("data") { put(DATA_TYPE, TYPE_RESERVATIONS_CHANGED) }
                        // Alta prioridad: llega aunque el móvil esté en reposo
                        putJsonObject("android") { put("priority", "high") }
                    }
                }
                val request = Request.Builder()
                    .url("https://fcm.googleapis.com/v1/projects/${BuildConfig.FCM_PROJECT_ID}/messages:send")
                    .header("Authorization", "Bearer $bearer")
                    .post(body.toString().toRequestBody(JSON))
                    .build()
                client.newCall(request).execute().close()
            } catch (_: IOException) {
                // Sin conexión: no se reintenta
            } catch (_: GeneralSecurityException) {
                // Clave mal copiada en el build
            }
        }
    }

    /** Token OAuth de la cuenta de servicio, con un JWT firmado con su clave (sin google-auth-library). */
    private suspend fun accessToken(): String? = mutex.withLock {
        val now = Instant.now()
        accessToken?.takeIf { now < accessTokenExpiresAt }?.let { return it }
        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(FormBody.Builder().add("grant_type", JWT_GRANT).add("assertion", jwt(now)).build())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = Json.parseToJsonElement(response.body.string()).jsonObject
            val token = json["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
            val expiresIn = json["expires_in"]?.jsonPrimitive?.longOrNull ?: TOKEN_LIFETIME_SECONDS
            accessToken = token
            // Un minuto de margen para no usarlo justo cuando caduca
            accessTokenExpiresAt = now.plusSeconds(expiresIn - 60)
            token
        }
    }

    private fun jwt(now: Instant): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = buildJsonObject {
            put("iss", BuildConfig.FCM_CLIENT_EMAIL)
            put("scope", SCOPE)
            put("aud", TOKEN_URL)
            put("iat", now.epochSecond)
            put("exp", now.epochSecond + TOKEN_LIFETIME_SECONDS)
        }.toString()
        val unsigned = "${base64Url(header.toByteArray())}.${base64Url(claims.toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey())
            update(unsigned.toByteArray())
            sign()
        }
        return "$unsigned.${base64Url(signature)}"
    }

    /** La clave viene en PEM (PKCS#8), como la da Google Cloud. */
    private fun privateKey(): PrivateKey {
        val pem = BuildConfig.FCM_PRIVATE_KEY
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .filterNot { it.isWhitespace() }
        val der = try {
            Base64.getDecoder().decode(pem)
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException(e)
        }
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        /** Clave del mensaje de datos y su único valor, que comprueba [FriendMessagingService]. */
        const val DATA_TYPE = "type"
        const val TYPE_RESERVATIONS_CHANGED = "reservations_changed"

        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val JWT_GRANT = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        private const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        private const val TOKEN_LIFETIME_SECONDS = 3600L
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
