package com.dgalan.padelprat.feature.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dgalan.padelprat.R
import com.dgalan.padelprat.core.system.UnknownSources
import com.dgalan.padelprat.data.update.AppUpdate
import com.dgalan.padelprat.ui.theme.PadelPratTheme

/**
 * Pantalla que sustituye a toda la app mientras haya una versión nueva sin instalar: no tiene "Más tarde" ni se puede
 * cerrar. Las reservas programadas siguen funcionando aunque se vea esta pantalla.
 */
@Composable
fun ForceUpdateScreen(
    update: AppUpdate,
    modifier: Modifier = Modifier,
    viewModel: ForceUpdateViewModel = hiltViewModel<ForceUpdateViewModel, ForceUpdateViewModel.Factory>(
        key = update.version,
        creationCallback = { factory -> factory.create(update) }
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // El permiso se puede quitar o dar desde Ajustes en cualquier momento, así que se revisa al volver a la pantalla
    var canInstall by remember { mutableStateOf(UnknownSources.canInstall(context)) }
    LifecycleResumeEffect(Unit) {
        canInstall = UnknownSources.canInstall(context)
        onPauseOrDispose {}
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        canInstall = UnknownSources.canInstall(context)
        if (canInstall) viewModel.onUpdateClick()
    }
    val confirmationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    LaunchedEffect(uiState.confirmation) {
        uiState.confirmation?.let { intent ->
            viewModel.onConfirmationShown()
            confirmationLauncher.launch(intent)
        }
    }

    ForceUpdateContent(
        uiState = uiState,
        needsPermission = !canInstall,
        onUpdateClick = {
            if (canInstall) {
                viewModel.onUpdateClick()
            } else {
                permissionLauncher.launch(UnknownSources.settingsIntent(context))
            }
        },
        modifier = modifier
    )
}

@Composable
private fun ForceUpdateContent(
    uiState: ForceUpdateUiState,
    needsPermission: Boolean,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Ball()
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.update_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.update_text, uiState.newVersion, uiState.currentVersion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        val message = when {
            uiState.isScheduledBookingRunning -> R.string.update_waiting_scheduled
            uiState.error == UpdateError.Download -> R.string.update_error_download
            uiState.error == UpdateError.Install -> R.string.update_error_install
            needsPermission && uiState.step == UpdateStep.Ready -> R.string.update_permission_hint
            else -> null
        }
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        Spacer(Modifier.height(32.dp))
        when (val step = uiState.step) {
            is UpdateStep.Downloading -> {
                LinearProgressIndicator(progress = { step.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.update_downloading, step.percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            UpdateStep.Installing -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.update_installing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            UpdateStep.Ready -> Button(
                onClick = onUpdateClick,
                enabled = uiState.canUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.update_button), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** El círculo lima con la bola, como en el login. */
@Composable
private fun Ball(modifier: Modifier = Modifier) {
    // En oscuro tertiaryContainer es un oliva apagado; tertiary mantiene el lima
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .size(72.dp)
            .background(if (dark) scheme.tertiary else scheme.tertiaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ball),
            contentDescription = null,
            tint = if (dark) scheme.onTertiary else scheme.onTertiaryContainer,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Preview
@Composable
private fun ForceUpdateContentPreview() {
    PadelPratTheme {
        ForceUpdateContent(
            uiState = ForceUpdateUiState(currentVersion = "1.0.0", newVersion = "1.1.0"),
            needsPermission = true,
            onUpdateClick = {}
        )
    }
}

@Preview
@Composable
private fun ForceUpdateContentDownloadingPreview() {
    PadelPratTheme {
        ForceUpdateContent(
            uiState = ForceUpdateUiState(
                currentVersion = "1.0.0",
                newVersion = "1.1.0",
                step = UpdateStep.Downloading(42)
            ),
            needsPermission = false,
            onUpdateClick = {}
        )
    }
}
