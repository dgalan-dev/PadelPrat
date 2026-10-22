package com.dgalan.padelprat.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.dgalan.padelprat.feature.booking.BookingScreen
import com.dgalan.padelprat.feature.profile.ProfileScreen
import com.dgalan.padelprat.feature.reservations.ReservationsScreen

/**
 * Pantalla principal con la barra inferior. Las pestañas viven dentro de una sola entrada de la navegación
 * ([MainRoute]), así las pantallas de detalle se abren por encima y tapan la barra.
 *
 * Cada pestaña conserva su estado al cambiar de una a otra. Atrás desde otra pestaña vuelve a la de inicio, y desde
 * la de inicio sale de la app.
 */
@Composable
fun MainScaffold(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(TopLevelDestination.entries.first()) }
    val stateHolder = rememberSaveableStateHolder()
    val haptics = LocalHapticFeedback.current

    BackHandler(enabled = selected != TopLevelDestination.entries.first()) {
        selected = TopLevelDestination.entries.first()
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            // Pestaña activa: indicador en primaryContainer (como el mock) e icono y texto en el azul de la marca
            val scheme = MaterialTheme.colorScheme
            val itemColors = NavigationBarItemDefaults.colors(
                indicatorColor = scheme.primaryContainer,
                selectedIconColor = scheme.primary,
                selectedTextColor = scheme.primary
            )
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = destination == selected
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            // Solo al cambiar de pestaña: volver a pulsar la activa no vibra ni rebota
                            if (!isSelected) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                selected = destination
                            }
                        },
                        icon = { TabIcon(destination, isSelected) },
                        label = { Text(stringResource(destination.label)) },
                        colors = itemColors
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = selected,
            animationSpec = tween(durationMillis = 200),
            modifier = Modifier.padding(innerPadding),
            label = "tab"
        ) { destination ->
            stateHolder.SaveableStateProvider(destination.name) {
                when (destination) {
                    TopLevelDestination.Booking -> BookingScreen()
                    TopLevelDestination.Reservations -> ReservationsScreen()
                    TopLevelDestination.Profile -> ProfileScreen()
                }
            }
        }
    }
}

/** Icono de la pestaña: pasa del contorno al relleno con un fundido y rebota al seleccionarse. */
@Composable
private fun TabIcon(destination: TopLevelDestination, selected: Boolean) {
    val scale = remember { Animatable(1f) }
    // El rebote solo al cambiar la selección, no al aparecer la barra
    var firstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (firstComposition) {
            firstComposition = false
        } else if (selected) {
            scale.animateTo(1.18f, tween(durationMillis = 110))
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    Crossfade(
        targetState = selected,
        animationSpec = tween(durationMillis = 150),
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        label = "tabIcon"
    ) { filled ->
        Icon(
            painter = painterResource(if (filled) destination.selectedIcon else destination.icon),
            contentDescription = null
        )
    }
}
