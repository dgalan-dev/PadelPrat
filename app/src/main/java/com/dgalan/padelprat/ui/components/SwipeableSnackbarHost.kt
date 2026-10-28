package com.dgalan.padelprat.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

/** [SnackbarHost] cuyas snackbars se pueden descartar deslizándolas hacia cualquier lado. */
@Composable
fun SwipeableSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        // Un estado nuevo por snackbar: si no, la siguiente aparecería ya deslizada fuera
        key(data) {
            SwipeToDismissBox(
                state = rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                onDismiss = { data.dismiss() }
            ) {
                Snackbar(data)
            }
        }
    }
}
