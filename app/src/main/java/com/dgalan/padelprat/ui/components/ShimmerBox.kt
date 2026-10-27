package com.dgalan.padelprat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dgalan.padelprat.ui.theme.padelColors

/**
 * Hueco de una silueta de carga, con un brillo que lo recorre. Va sobre una [PadelCard]: el brillo es del color de
 * la tarjeta. Todos empiezan en el mismo frame, así que los de una misma pantalla brillan a la vez.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, cornerRadius: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.padelColors.card
    // El valor se lee al dibujar: el brillo solo repinta, no recompone
    Box(
        modifier = modifier.drawBehind {
            val x = shimmer * size.width * 3f - size.width
            drawRoundRect(
                brush = Brush.linearGradient(listOf(base, highlight, base), Offset(x, 0f), Offset(x + size.width, 0f)),
                cornerRadius = CornerRadius(cornerRadius.toPx())
            )
        }
    )
}
