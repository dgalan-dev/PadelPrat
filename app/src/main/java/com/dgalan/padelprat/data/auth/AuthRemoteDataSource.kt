package com.dgalan.padelprat.data.auth

import com.dgalan.padelprat.core.network.Syltek
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Peticiones de login y logout a la web del club. Ver docs/syltek-api.md, apartado 3. */
class AuthRemoteDataSource @Inject constructor(private val client: OkHttpClient, private val baseUrl: HttpUrl) {

    /**
     * El servidor responde 200 tanto si el login es correcto como si no: el resultado se decide por la cookie de
     * sesión, que solo llega si es correcto (y no depende del idioma de la página).
     */
    suspend fun login(email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("email", email)
            .add("password", password)
            .add("safari", "")
            .add("url", Syltek.HOME_PATH)
            .build()
        val request = Request.Builder().url(baseUrl.resolve(Syltek.LOGIN_PATH)!!).post(body).build()

        try {
            client.newCall(request).execute().use { response ->
                val sessionCookieReceived = response.headers("Set-Cookie").any { header ->
                    header.startsWith("${Syltek.SESSION_COOKIE}=") &&
                        header.substringAfter('=').substringBefore(';').isNotEmpty()
                }
                when {
                    sessionCookieReceived -> LoginResult.Success
                    response.code == 200 -> LoginResult.InvalidCredentials
                    else -> LoginResult.UnknownError
                }
            }
        } catch (_: IOException) {
            LoginResult.NetworkError
        }
    }

    /** Invalida la sesión en el servidor. Responde con una redirección que no hace falta seguir. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl.resolve(Syltek.LOGOUT_PATH)!!).get().build()
        client.newCall(request).execute().close()
    }
}
