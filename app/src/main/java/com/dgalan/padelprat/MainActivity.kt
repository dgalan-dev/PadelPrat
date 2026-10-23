package com.dgalan.padelprat

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dgalan.padelprat.navigation.AppNavigation
import com.dgalan.padelprat.ui.theme.PadelPratTheme
import dagger.hilt.android.AndroidEntryPoint

private const val SPLASH_EXIT_DURATION_MS = 250L
private const val SPLASH_EXIT_ICON_SCALE = 1.3f

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // La pantalla de arranque se mantiene hasta saber si hay sesión, para no enseñar el login un instante
        splashScreen.setKeepOnScreenCondition { viewModel.sessionState.value == SessionState.Loading }
        // Android 12+: en lugar del corte seco, la bola crece un poco mientras la pantalla de arranque se desvanece.
        // En versiones anteriores se mantiene la salida normal, sin animación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { provider ->
                provider.iconView.animate()
                    .scaleX(SPLASH_EXIT_ICON_SCALE)
                    .scaleY(SPLASH_EXIT_ICON_SCALE)
                    .setDuration(SPLASH_EXIT_DURATION_MS)
                    .start()
                provider.view.animate()
                    .alpha(0f)
                    .setDuration(SPLASH_EXIT_DURATION_MS)
                    .withEndAction { provider.remove() }
                    .start()
            }
        }
        enableEdgeToEdge()
        setContent {
            PadelPratTheme {
                val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
                if (sessionState != SessionState.Loading) {
                    AppNavigation(isLoggedIn = sessionState == SessionState.LoggedIn)
                }
            }
        }
    }
}
