package com.dgalan.padelprat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dgalan.padelprat.feature.login.LoginScreen

/**
 * La sesión decide la pantalla raíz: al iniciar sesión se pasa a la principal (con la barra inferior) y al cerrarla
 * (o al caducar) se vuelve al login, vaciando la pila para que no se pueda volver atrás.
 *
 * [openReservationsRequests] sube cada vez que hay que ir a Mis reservas (al tocar un aviso).
 */
@Composable
fun AppNavigation(isLoggedIn: Boolean, openReservationsRequests: Int, modifier: Modifier = Modifier) {
    val root = if (isLoggedIn) MainRoute else LoginRoute
    val backStack = rememberNavBackStack(root)

    LaunchedEffect(root) {
        if (backStack.firstOrNull() != root) {
            backStack.clear()
            backStack.add(root)
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<LoginRoute> { LoginScreen() }
            entry<MainRoute> { MainScaffold(openReservationsRequests) }
        }
    )
}
