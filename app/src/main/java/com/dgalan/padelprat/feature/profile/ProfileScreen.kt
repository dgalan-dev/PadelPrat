package com.dgalan.padelprat.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dgalan.padelprat.BuildConfig
import com.dgalan.padelprat.R
import com.dgalan.padelprat.data.profile.Profile
import com.dgalan.padelprat.ui.components.MessageState
import com.dgalan.padelprat.ui.components.PadelCard
import com.dgalan.padelprat.ui.theme.PadelPratTheme

@Composable
fun ProfileScreen(modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // La Activity: el selector de cuentas de Google se muestra encima
    val context = LocalContext.current
    ProfileContent(
        uiState = uiState,
        onRetry = viewModel::onRetry,
        onMatchRemindersChange = viewModel::onMatchRemindersChange,
        onCancelRemindersChange = viewModel::onCancelRemindersChange,
        onSharingEnable = { viewModel.onSharingEnable(context) },
        onSharingDisable = viewModel::onSharingDisable,
        onSharingCheck = viewModel::onSharingCheck,
        onFriendAlertsChange = viewModel::onFriendAlertsChange,
        onLogoutClick = viewModel::onLogoutClick,
        modifier = modifier
    )
}

/** Datos de "Mi Cuenta" de la web, solo de lectura: para cambiarlos hay que ir a la web. */
@Composable
private fun ProfileContent(
    uiState: ProfileUiState,
    onRetry: () -> Unit,
    onMatchRemindersChange: (Boolean) -> Unit,
    onCancelRemindersChange: (Boolean) -> Unit,
    onSharingEnable: () -> Unit,
    onSharingDisable: () -> Unit,
    onSharingCheck: () -> Unit,
    onFriendAlertsChange: (Boolean) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(24.dp))

        val profile = uiState.profile
        when {
            profile != null -> ProfileDetails(profile)

            uiState.isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            uiState.error != null -> ProfileError(uiState.error, onRetry)
        }

        Spacer(Modifier.height(24.dp))
        Reminders(
            matchReminders = uiState.matchReminders,
            cancelReminders = uiState.cancelReminders,
            onMatchRemindersChange = onMatchRemindersChange,
            onCancelRemindersChange = onCancelRemindersChange
        )

        Spacer(Modifier.height(24.dp))
        Sharing(
            state = uiState.sharing,
            error = uiState.sharingError,
            friendAlerts = uiState.friendAlerts,
            onEnable = onSharingEnable,
            onDisable = onSharingDisable,
            onCheck = onSharingCheck,
            onFriendAlertsChange = onFriendAlertsChange
        )

        Spacer(Modifier.height(32.dp))
        // Secundario: no es lo que se viene a hacer aquí, y se confirma antes
        OutlinedButton(
            onClick = { confirmLogout = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_logout),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.profile_logout), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.profile_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.profile_logout_title)) },
            text = { Text(stringResource(R.string.profile_logout_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogoutClick()
                    }
                ) {
                    Text(stringResource(R.string.profile_logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(stringResource(R.string.profile_logout_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProfileDetails(profile: Profile) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.initials,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                profile.email?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Solo los datos que el usuario tiene rellenados en la web
        val address = listOfNotNull(
            profile.address,
            listOfNotNull(profile.postalCode, profile.city).joinToString(" ").ifEmpty { null }
        ).joinToString(", ").ifEmpty { null }
        val rows = listOfNotNull(
            profile.phone?.let { Triple(R.drawable.ic_phone, R.string.profile_phone, it) },
            address?.let { Triple(R.drawable.ic_place, R.string.profile_address, it) }
        )
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.profile_contact),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            PadelCard {
                Column {
                    rows.forEachIndexed { index, (icon, label, value) ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(icon = icon, label = stringResource(label), value = value)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.profile_edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Los avisos de las próximas reservas, que se pueden desactivar por separado. */
@Composable
private fun Reminders(
    matchReminders: Boolean,
    cancelReminders: Boolean,
    onMatchRemindersChange: (Boolean) -> Unit,
    onCancelRemindersChange: (Boolean) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.profile_reminders),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        PadelCard {
            Column {
                SwitchRow(
                    title = stringResource(R.string.profile_reminder_match),
                    text = stringResource(R.string.profile_reminder_match_text),
                    checked = matchReminders,
                    onCheckedChange = onMatchRemindersChange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = stringResource(R.string.profile_reminder_cancel),
                    text = stringResource(R.string.profile_reminder_cancel_text),
                    checked = cancelReminders,
                    onCheckedChange = onCancelRemindersChange
                )
            }
        }
    }
}

/** Reservas compartidas con el amigo: se activan con la cuenta de Google y el alta se hace en la consola. */
@Composable
private fun Sharing(
    state: SharingState,
    error: SharingError?,
    friendAlerts: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onCheck: () -> Unit,
    onFriendAlertsChange: (Boolean) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.profile_sharing),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        PadelCard {
            Column {
                val text = when (state) {
                    SharingState.Off -> stringResource(R.string.profile_sharing_off_text)

                    SharingState.Loading -> stringResource(R.string.profile_sharing_loading_text)

                    is SharingState.Active -> stringResource(
                        R.string.profile_sharing_active_text,
                        state.email.orEmpty()
                    )

                    is SharingState.Pending -> stringResource(
                        R.string.profile_sharing_pending_text,
                        state.email.orEmpty()
                    )

                    is SharingState.Unknown -> stringResource(
                        R.string.profile_sharing_unknown_text,
                        state.email.orEmpty()
                    )
                }
                SwitchRow(
                    title = stringResource(R.string.profile_sharing_switch),
                    text = text,
                    checked = state != SharingState.Off,
                    onCheckedChange = { if (it) onEnable() else onDisable() },
                    enabled = state != SharingState.Loading
                )
                if (state is SharingState.Active) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        title = stringResource(R.string.profile_sharing_alerts),
                        text = stringResource(R.string.profile_sharing_alerts_text),
                        checked = friendAlerts,
                        onCheckedChange = onFriendAlertsChange
                    )
                }
                if (state is SharingState.Pending || state is SharingState.Unknown) {
                    TextButton(onClick = onCheck, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                        Text(stringResource(R.string.profile_sharing_check))
                    }
                }
                error?.let {
                    Text(
                        text = stringResource(it.textRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                }
            }
        }
    }
}

private fun SharingError.textRes(): Int = when (this) {
    SharingError.NoGoogleAccount -> R.string.profile_sharing_error_no_account
    SharingError.Network -> R.string.profile_sharing_error_network
    SharingError.Unknown -> R.string.profile_sharing_error_unknown
}

@Composable
private fun SwitchRow(
    title: String,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun InfoRow(icon: Int, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ProfileError(error: ProfileError, onRetry: () -> Unit) {
    val (title, text) = when (error) {
        ProfileError.Network -> R.string.error_network_title to R.string.error_network_text
        ProfileError.Unknown -> R.string.profile_error_unknown to R.string.error_unknown_text
        ProfileError.WebChanged -> R.string.error_web_changed_title to R.string.error_web_changed_text
    }
    MessageState(
        icon = R.drawable.ic_error,
        title = stringResource(title),
        message = stringResource(text),
        action = { Button(onClick = onRetry) { Text(stringResource(R.string.booking_retry)) } }
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileContentPreview() {
    PadelPratTheme {
        ProfileContent(
            uiState = ProfileUiState(
                profile = Profile(
                    name = "Marc Jugador",
                    email = "marc@ejemplo.com",
                    phone = "600 000 000",
                    address = null,
                    postalCode = "08820",
                    city = "El Prat de Llobregat"
                ),
                isLoading = false
            ),
            onRetry = {},
            onMatchRemindersChange = {},
            onCancelRemindersChange = {},
            onSharingEnable = {},
            onSharingDisable = {},
            onSharingCheck = {},
            onFriendAlertsChange = {},
            onLogoutClick = {}
        )
    }
}
