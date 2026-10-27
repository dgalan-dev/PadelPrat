package com.dgalan.padelprat.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Colores propios de la app que no tiene el esquema de Material 3, sacados de sus tonos. Se leen con
 * `MaterialTheme.padelColors`.
 */
@Immutable
data class PadelColors(
    /** Huecos libres de la parrilla. */
    val available: Color,
    val onAvailable: Color,
    /** Lima de bola intenso, para lo que tiene que destacar: la etiqueta "Disponible" o la próxima reserva. */
    val accent: Color,
    val onAccent: Color,
    /** Tarjetas: en oscuro, más claras que el fondo y sin borde, para que no parezcan hundidas. */
    val card: Color,
    val cardBorder: Color?,
    /** Tarjetas apagadas (p. ej. las reservas anuladas). */
    val cardMuted: Color,
    /** Cabecera fija con contenido pasando por detrás; distinta de [card] para que no se fundan. */
    val pinnedHeader: Color
)

internal fun lightPadelColors(scheme: ColorScheme) = PadelColors(
    available = scheme.tertiaryContainer,
    onAvailable = scheme.onTertiaryContainer,
    accent = scheme.tertiaryContainer,
    onAccent = scheme.onTertiaryContainer,
    card = scheme.surfaceContainerLowest,
    cardBorder = scheme.outlineVariant,
    cardMuted = scheme.surfaceContainer,
    pinnedHeader = scheme.surfaceContainer
)

// En oscuro tertiaryContainer es un oliva apagado y tertiary, un lima que deslumbra en una parrilla llena de huecos:
// los chips llevan un velo de lima sobre la tarjeta con el texto en lima, y el lima intenso queda para lo que destaca
internal fun darkPadelColors(scheme: ColorScheme) = PadelColors(
    available = scheme.tertiary.copy(alpha = 0.16f).compositeOver(scheme.surfaceContainer),
    onAvailable = scheme.tertiary,
    accent = scheme.tertiary,
    onAccent = scheme.onTertiary,
    card = scheme.surfaceContainer,
    cardBorder = null,
    cardMuted = scheme.surfaceContainerLow,
    pinnedHeader = scheme.surfaceContainerHigh
)

internal val LocalPadelColors = staticCompositionLocalOf<PadelColors> { error("Sin PadelPratTheme") }

val MaterialTheme.padelColors: PadelColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPadelColors.current
