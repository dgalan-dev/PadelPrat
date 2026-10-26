package com.dgalan.padelprat.core.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Permiso "Instalar apps desconocidas" de esta app, necesario para instalar sus propias actualizaciones (la app no va a
 * Google Play). Desde Android 8 se da por app y solo hace falta concederlo una vez.
 */
object UnknownSources {

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Pantalla de Ajustes donde se concede, ya abierta en esta app. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
}
