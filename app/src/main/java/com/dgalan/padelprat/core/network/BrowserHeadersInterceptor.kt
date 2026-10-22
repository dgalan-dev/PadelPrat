package com.dgalan.padelprat.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Cabeceras de navegador que espera la web del club: User-Agent, idioma español (fechas y estados vienen en el
 * idioma de la sesión) y, en los POST, `Origin` y `Referer` del propio dominio.
 */
class BrowserHeadersInterceptor(baseUrl: HttpUrl) : Interceptor {

    private val origin = "${baseUrl.scheme}://${baseUrl.host}"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "es-ES,es;q=0.9")
            .apply {
                if (chain.request().method != "GET") {
                    header("Origin", origin)
                    header("Referer", "$origin/")
                }
            }
            .build()
        return chain.proceed(request)
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
    }
}
