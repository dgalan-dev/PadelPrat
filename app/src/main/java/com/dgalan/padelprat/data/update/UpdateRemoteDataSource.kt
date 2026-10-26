package com.dgalan.padelprat.data.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Última release de GitHub y descarga de su APK. El repositorio es público: no hace falta token. */
class UpdateRemoteDataSource @Inject constructor(@param:GitHubClient private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** La última release publicada (GitHub no cuenta borradores ni *prereleases*), o `null` si no tiene APK. */
    suspend fun latestRelease(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        val call = client.newCall(request)
        // La pantalla de arranque espera a esta comprobación: si GitHub tarda, se entra sin comprobar
        call.timeout().timeout(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val release = try {
                json.decodeFromString<GitHubRelease>(response.body.string())
            } catch (e: SerializationException) {
                throw IOException(e)
            }
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@use null
            AppUpdate(
                version = release.tagName.removePrefix("v"),
                apkUrl = apk.downloadUrl,
                apkSize = apk.size,
                sha256 = apk.digest?.takeIf { it.startsWith(SHA256_PREFIX) }?.removePrefix(SHA256_PREFIX)
            )
        }
    }

    /**
     * Descarga el APK en [target] pasando por un fichero temporal, así que [target] solo existe si la descarga está
     * completa y es correcta (tamaño y, si GitHub lo da, SHA-256).
     */
    suspend fun download(update: AppUpdate, target: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val partial = File(target.parentFile, "${target.name}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(update.apkUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            partial.outputStream().use { output ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        onProgress((downloaded.toFloat() / update.apkSize).coerceAtMost(1f))
                    }
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (partial.length() != update.apkSize || (update.sha256 != null && !hash.equals(update.sha256, true))) {
            partial.delete()
            throw IOException("APK incompleto o distinto del publicado")
        }
        if (!partial.renameTo(target)) throw IOException("No se ha podido guardar el APK")
    }

    @Serializable
    private data class GitHubRelease(@SerialName("tag_name") val tagName: String, val assets: List<GitHubAsset>)

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        val size: Long,
        val digest: String? = null
    )

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/dgalan-dev/PadelPrat/releases/latest"
        const val SHA256_PREFIX = "sha256:"
        const val CHECK_TIMEOUT_SECONDS = 5L
        const val BUFFER_SIZE = 64 * 1024
    }
}
