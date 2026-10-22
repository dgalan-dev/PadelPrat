package com.dgalan.padelprat.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dgalan.padelprat.R
import com.dgalan.padelprat.ui.theme.PadelPratTheme

@Composable
fun ProfileScreen(modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
    ProfileContent(onLogoutClick = viewModel::onLogoutClick, modifier = modifier)
}

/** Perfil: de momento solo el título y cerrar sesión. */
@Composable
private fun ProfileContent(onLogoutClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedButton(onClick = onLogoutClick) {
            Text(stringResource(R.string.profile_logout))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileContentPreview() {
    PadelPratTheme {
        ProfileContent(onLogoutClick = {})
    }
}
