package com.dgalan.padelprat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dgalan.padelprat.navigation.AppNavigation
import com.dgalan.padelprat.ui.theme.PadelPratTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PadelPratTheme {
                AppNavigation()
            }
        }
    }
}
