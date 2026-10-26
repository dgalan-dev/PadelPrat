package com.dgalan.padelprat.data.update

import android.content.Context
import com.dgalan.padelprat.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Actualización obligatoria: al abrir la app se mira la última release de GitHub y, si es más nueva que la instalada,
 * no se deja usar la app hasta instalarla. Si no se puede comprobar (sin conexión, GitHub caído) se entra igual, para
 * que la app nunca se quede bloqueada.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val remoteDataSource: UpdateRemoteDataSource
) {

    private val directory = File(context.cacheDir, "update")

    /** La actualización pendiente, o `null` si la app está al día o no se ha podido comprobar. */
    suspend fun check(): AppUpdate? {
        // En debug la firma no es la de las releases y Android no dejaría instalar encima
        if (BuildConfig.DEBUG) return null
        val latest = try {
            remoteDataSource.latestRelease()
        } catch (_: IOException) {
            return null
        }
        val update = latest?.takeIf { isNewerVersion(it.version, BuildConfig.VERSION_NAME) }
        // Ya actualizada: se borra el APK que se descargó para instalar esta versión
        if (update == null) withContext(Dispatchers.IO) { directory.deleteRecursively() }
        return update
    }

    /** El APK descargado y comprobado, o `null` si la descarga ha fallado. Si ya estaba descargado, no se repite. */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File? {
        val apk = File(directory, "PadelPrat-${update.version}.apk")
        return try {
            withContext(Dispatchers.IO) {
                if (!apk.exists()) {
                    directory.mkdirs()
                    remoteDataSource.download(update, apk, onProgress)
                }
            }
            apk
        } catch (_: IOException) {
            null
        }
    }
}
