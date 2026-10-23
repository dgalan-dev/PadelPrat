package com.dgalan.padelprat.data.profile

import com.dgalan.padelprat.core.network.Syltek
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface ProfileResult {
    data class Success(val profile: Profile) : ProfileResult

    data object NetworkError : ProfileResult

    /** La app ya está volviendo al login. */
    data object SessionExpired : ProfileResult

    data object UnknownError : ProfileResult
}

/**
 * Datos del usuario. Se guardan en memoria para que Perfil y el saludo de la parrilla compartan una sola petición.
 * No se guardan en disco: son datos personales y la web es la fuente de verdad.
 */
@Singleton
class ProfileRepository @Inject constructor(private val client: OkHttpClient, private val baseUrl: HttpUrl) {

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    suspend fun refresh(): ProfileResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl.resolve(Syltek.ACCOUNT_PATH)!!).get().build()
        val result = try {
            client.newCall(request).execute().use { response ->
                val location = response.header("Location").orEmpty()
                when {
                    response.isRedirect && location.contains(Syltek.LOGIN_REDIRECT_PATH, ignoreCase = true) ->
                        ProfileResult.SessionExpired

                    !response.isSuccessful -> ProfileResult.UnknownError

                    else -> ProfileParser.parse(response.body.string())?.let(ProfileResult::Success)
                        ?: ProfileResult.UnknownError
                }
            }
        } catch (_: IOException) {
            ProfileResult.NetworkError
        }
        if (result is ProfileResult.Success) _profile.value = result.profile
        if (result == ProfileResult.SessionExpired) clear()
        result
    }

    /** Al cerrar sesión: que el siguiente usuario no vea los datos del anterior. */
    fun clear() {
        _profile.value = null
    }
}
