package com.dgalan.padelprat.core.network

import androidx.datastore.core.DataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * [CookieJar] que guarda las cookies en DataStore (cifradas), para que la sesión dure entre arranques de la app.
 *
 * OkHttp llama a estos métodos de forma síncrona desde sus propios hilos (nunca el principal), así que leer y
 * escribir bloqueando es seguro y garantiza que la cookie de sesión está guardada antes de procesar la respuesta.
 */
@Singleton
class PersistentCookieJar @Inject constructor(private val dataStore: DataStore<List<Cookie>>) : CookieJar {

    /** Cookies guardadas. */
    val cookies: Flow<List<Cookie>> get() = dataStore.data

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return runBlocking { dataStore.data.first() }.filter { it.expiresAt > now && it.matches(url) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        runBlocking {
            dataStore.updateData { stored ->
                val now = System.currentTimeMillis()
                // Una cookie nueva sustituye a la guardada con el mismo nombre, dominio y ruta; las caducadas (como
                // la que manda el logout) la borran. Las que no traen caducidad (p. ej. la del balanceador,
                // PCRMBKLBK) también se guardan
                val replaced = stored.filterNot { old -> cookies.any { it.sameIdentityAs(old) } }
                (replaced + cookies).filter { it.expiresAt > now }
            }
        }
    }

    suspend fun clear() {
        dataStore.updateData { emptyList() }
    }

    private fun Cookie.sameIdentityAs(other: Cookie) =
        name == other.name && domain == other.domain && path == other.path
}
