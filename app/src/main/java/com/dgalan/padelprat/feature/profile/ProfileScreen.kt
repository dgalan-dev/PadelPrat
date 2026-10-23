package com.dgalan.padelprat.feature.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.dgalan.padelprat.ui.theme.PadelPratTheme

@Composable
fun ProfileScreen(modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(
        uiState = uiState,
        onRetry = viewModel::onRetry,
        onLogoutClick = viewModel::onLogoutClick,
        modifier = modifier
    )
}

/** Datos de "Mi Cuenta" de la web, solo de lectura: para cambiarlos hay que ir a la web. */
@Composable
private fun ProfileContent(
    uiState: ProfileUiState,
    onRetry: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onLogoutClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
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
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
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
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(
                if (error == ProfileError.Network) R.string.profile_error_network else R.string.profile_error_unknown
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.booking_retry)) }
    }
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
            onLogoutClick = {}
        )
    }
}
