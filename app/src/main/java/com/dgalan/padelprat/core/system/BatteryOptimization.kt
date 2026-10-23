package com.dgalan.padelprat.core.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri

/**
 * Optimización de batería de la app. Con ella activa, Samsung y otros fabricantes pueden dormir la app y bloquear la
 * alarma de las reservas programadas; sin ella ("Sin restricciones") la alarma llega a su hora. No hace que la app
 * gaste más: solo evita que el sistema la bloquee.
 */
object BatteryOptimization {

    private const val PREFS = "startup"
    private const val KEY_ASKED = "battery_optimization_asked"

    fun isRestricted(context: Context): Boolean =
        !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Solo se pide una vez al abrir la app; después, desde el aviso del formulario de programar. */
    fun wasAsked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED, true) }
    }

    /**
     * Diálogo del sistema para quitar la restricción a esta app. Google Play limita este permiso, pero la app no va a
     * Play (ver docs/plan-reserva-programada.md).
     */
    @SuppressLint("BatteryLife")
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
}
