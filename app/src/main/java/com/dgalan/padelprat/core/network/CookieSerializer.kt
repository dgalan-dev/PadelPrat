package com.dgalan.padelprat.core.network

import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import okhttp3.Cookie
import okhttp3.HttpUrl

/**
 * Guarda las cookies cifradas con Tink (clave en el Keystore de Android). Cada cookie se guarda como su cabecera
 * `Set-Cookie`, junto al dominio para poder reconstruirla con [Cookie.parse].
 */
class CookieSerializer(private val aead: Aead) : Serializer<List<Cookie>> {

    override val defaultValue: List<Cookie> = emptyList()

    override suspend fun readFrom(input: InputStream): List<Cookie> {
        val encrypted = input.readBytes()
        if (encrypted.isEmpty()) return defaultValue
        val plain = try {
            aead.decrypt(encrypted, ASSOCIATED_DATA).decodeToString()
        } catch (_: GeneralSecurityException) {
            // Clave perdida (p. ej. datos restaurados en otro móvil): se empieza sin sesión
            return defaultValue
        }
        return plain.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val (domain, header) = line.split(SEPARATOR, limit = 2).takeIf { it.size == 2 }
                    ?: return@mapNotNull null
                Cookie.parse(HttpUrl.Builder().scheme("https").host(domain).build(), header)
            }
            .toList()
    }

    override suspend fun writeTo(t: List<Cookie>, output: OutputStream) {
        val plain = t.joinToString("\n") { "${it.domain}$SEPARATOR$it" }
        output.write(aead.encrypt(plain.encodeToByteArray(), ASSOCIATED_DATA))
    }

    private companion object {
        const val SEPARATOR = "\t"
        val ASSOCIATED_DATA = "padelprat-cookies".encodeToByteArray()
    }
}
