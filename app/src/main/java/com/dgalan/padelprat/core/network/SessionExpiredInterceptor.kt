package com.dgalan.padelprat.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Con la sesión caducada, las páginas privadas responden con un 303 a la página de login. Al detectarlo se borran
 * las cookies (la app vuelve sola a la pantalla de login porque la sesión deja de existir) y se avisa para que el
 * login explique el motivo.
 */
class SessionExpiredInterceptor(
    private val cookieJar: PersistentCookieJar,
    private val notifier: SessionExpiredNotifier
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isRedirect && response.header("Location").orEmpty().isLoginRedirect()) {
            runBlocking { cookieJar.clear() }
            notifier.notifyExpired()
        }
        return response
    }

    private fun String.isLoginRedirect() = substringBefore('?').endsWith(Syltek.LOGIN_REDIRECT_PATH, ignoreCase = true)
}
