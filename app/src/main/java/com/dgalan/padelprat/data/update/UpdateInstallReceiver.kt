package com.dgalan.padelprat.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Recibe de [android.content.pm.PackageInstaller] cómo va la instalación y se lo pasa a [AppInstaller]. */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {

    @Inject lateinit var installer: AppInstaller

    override fun onReceive(context: Context, intent: Intent) {
        installer.onStatus(intent)
    }
}
