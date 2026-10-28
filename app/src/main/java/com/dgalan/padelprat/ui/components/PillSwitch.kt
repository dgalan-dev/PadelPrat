package com.dgalan.padelprat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dgalan.padelprat.ui.theme.PadelPratTheme

/**
 * Selector de opciones como el de los mocks ("Próximas / Pasadas"): una pista gris redondeada con la opción activa
 * en una píldora azul que se desliza al cambiar. Con [icon], cada opción es un icono y [label] queda como su
 * descripción para TalkBack.
 */
@Composable
fun <T> PillSwitch(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> Int)? = null
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier
            .height(48.dp)
            .background(scheme.surfaceVariant, CircleShape)
            .padding(3.dp)
    ) {
        val optionWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = optionWidth * options.indexOf(selected).coerceAtLeast(0),
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "pillOffset"
        )
        Box(
            modifier = Modifier
                // Lambda: la animación solo recoloca la píldora, sin recalcular el layout
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(optionWidth)
                .fillMaxHeight()
                .background(scheme.primary, CircleShape)
        )
        Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { option ->
                val isSelected = option == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                    label = "pillText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) }),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Icon(
                            painter = painterResource(icon(option)),
                            contentDescription = label(option),
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.labelLarge,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PillSwitchPreview() {
    PadelPratTheme {
        PillSwitch(
            options = listOf("Próximas", "Pasadas"),
            selected = "Próximas",
            onSelect = {},
            label = { it },
            modifier = Modifier.padding(16.dp)
        )
    }
}
