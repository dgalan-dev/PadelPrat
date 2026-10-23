package com.dgalan.padelprat.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.dgalan.padelprat.R
import com.dgalan.padelprat.core.system.BatteryOptimization

private enum class StartupStep { Notifications, Battery, Done }

/**
 * Permisos que necesitan las reservas programadas, pedidos al entrar en la pantalla principal y uno detrás de otro:
 * primero las notificaciones (Android 13+) y después quitar la optimización de batería, este solo una vez. Si se
 * deniegan, la app funciona igual; solo las reservas programadas pueden no avisar o no llegar a su hora.
 */
@Composable
fun StartupPermissions() {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(StartupStep.Notifications) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        step = StartupStep.Battery
    }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        step = StartupStep.Done
    }

    LaunchedEffect(step) {
        when (step) {
            StartupStep.Notifications -> {
                val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needed) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    step =
                        StartupStep.Battery
                }
            }

            StartupStep.Battery -> {
                if (!BatteryOptimization.isRestricted(context) || BatteryOptimization.wasAsked(context)) {
                    step = StartupStep.Done
                }
            }

            StartupStep.Done -> Unit
        }
    }

    if (step == StartupStep.Battery && BatteryOptimization.isRestricted(context) &&
        !BatteryOptimization.wasAsked(context)
    ) {
        AlertDialog(
            onDismissRequest = {
                BatteryOptimization.markAsked(context)
                step = StartupStep.Done
            },
            title = { Text(stringResource(R.string.battery_dialog_title)) },
            text = { Text(stringResource(R.string.battery_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    BatteryOptimization.markAsked(context)
                    batteryLauncher.launch(BatteryOptimization.requestIntent(context))
                }) { Text(stringResource(R.string.battery_dialog_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    BatteryOptimization.markAsked(context)
                    step = StartupStep.Done
                }) { Text(stringResource(R.string.battery_dialog_later)) }
            }
        )
    }
}
