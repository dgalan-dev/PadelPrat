package com.dgalan.padelprat

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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // La pantalla de arranque se mantiene hasta saber si hay sesión, para no enseñar el login un instante
        splashScreen.setKeepOnScreenCondition { viewModel.sessionState.value == SessionState.Loading }
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
