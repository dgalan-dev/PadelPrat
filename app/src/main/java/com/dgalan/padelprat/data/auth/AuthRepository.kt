package com.dgalan.padelprat.data.auth

import com.dgalan.padelprat.core.network.PersistentCookieJar
import com.dgalan.padelprat.core.network.SessionExpiredNotifier
import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.friends.FriendAuthRepository
import com.dgalan.padelprat.data.profile.ProfileRepository
import com.dgalan.padelprat.data.reminders.ReminderRepository
import com.dgalan.padelprat.data.reservations.ReservationsCache
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Sesión del usuario. No se guarda la contraseña: solo la cookie de sesión (cifrada), que dura 6 meses. Cuando
 * caduca, el usuario vuelve a la pantalla de login.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val remote: AuthRemoteDataSource,
    private val cookieJar: PersistentCookieJar,
    private val sessionExpiredNotifier: SessionExpiredNotifier,
    private val profileRepository: ProfileRepository,
    private val reminderRepository: ReminderRepository,
    private val reservationsCache: ReservationsCache,
    private val friendAuthRepository: FriendAuthRepository
) {

    /** `true` mientras haya una cookie de sesión guardada y sin caducar. */
    val isLoggedIn: Flow<Boolean> = cookieJar.cookies
        .map { cookies ->
            val now = System.currentTimeMillis()
            cookies.any { it.name == Syltek.SESSION_COOKIE && it.expiresAt > now }
        }
        .distinctUntilChanged()

    /**
     * `true` si la sesión ha caducado sin que el usuario la cerrara: porque la cookie guardada ha pasado su fecha o
     * porque el servidor la ha rechazado. Sirve para explicarlo en el login.
     */
    val sessionExpired: Flow<Boolean> = combine(
        cookieJar.cookies.map { cookies ->
            val now = System.currentTimeMillis()
            cookies.any { it.name == Syltek.SESSION_COOKIE && it.expiresAt <= now }
        },
        sessionExpiredNotifier.expired
    ) { expiredByDate, expiredByServer -> expiredByDate || expiredByServer }
        .distinctUntilChanged()

    suspend fun login(email: String, password: String): LoginResult {
        val result = remote.login(email.trim(), password)
        if (result == LoginResult.Success) {
            sessionExpiredNotifier.reset()
            // Si la sesión caducó sin cerrarla, quien entra puede ser otro usuario
            reservationsCache.clear()
        }
        return result
    }

    /** Cierra la sesión en el servidor si hay conexión y siempre la borra del móvil. */
    suspend fun logout() {
        try {
            remote.logout()
        } catch (_: IOException) {
            // Sin conexión la sesión queda viva en el servidor hasta que caduque, pero el móvil ya no la tiene
        }
        cookieJar.clear()
        sessionExpiredNotifier.reset()
        profileRepository.clear()
        reminderRepository.clear()
        reservationsCache.clear()
        // Las reservas compartidas van con este usuario: el siguiente tendría que activarlas con su cuenta
        friendAuthRepository.signOut()
    }
}
