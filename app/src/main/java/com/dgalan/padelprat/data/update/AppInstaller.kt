package com.dgalan.padelprat.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

sealed interface InstallEvent {
    /** Android pide al usuario que confirme la instalación con el diálogo de [intent]. */
    data class NeedsConfirmation(val intent: Intent) : InstallEvent

    /** El usuario ha cancelado el diálogo del sistema. */
    data object Cancelled : InstallEvent

    data object Failed : InstallEvent
}

/**
 * Instala el APK con [PackageInstaller]. Si sale bien, Android cierra la app y la sustituye por la nueva versión, así
 * que no hay evento de éxito. La primera vez Android siempre pide confirmación; en Android 12+ las siguientes pueden
 * hacerse sin preguntar, porque la app ya es la instaladora de sí misma.
 */
@Singleton
class AppInstaller @Inject constructor(@ApplicationContext private val context: Context) {

    private val eventChannel = Channel<InstallEvent>(Channel.BUFFERED)
    val events: Flow<InstallEvent> = eventChannel.receiveAsFlow()

    /** `false` si no se ha podido ni empezar la instalación. */
    suspend fun install(apk: File): Boolean = withContext(Dispatchers.IO) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        try {
            val sessionId = packageInstaller.createSession(params)
            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite(APK_NAME, 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                session.commit(statusReceiver().intentSender)
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    internal fun onStatus(intent: Intent) {
        val event = when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { InstallEvent.NeedsConfirmation(it) }
                    ?: InstallEvent.Failed

            PackageInstaller.STATUS_SUCCESS -> return

            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallEvent.Cancelled

            else -> InstallEvent.Failed
        }
        eventChannel.trySend(event)
    }

    /** Android rellena el estado en el intent, así que tiene que ser mutable (es explícito, a nuestro receptor). */
    private fun statusReceiver(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, UpdateInstallReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    )

    private companion object {
        const val APK_NAME = "base.apk"
    }
}
