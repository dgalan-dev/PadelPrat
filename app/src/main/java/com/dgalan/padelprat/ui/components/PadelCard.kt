package com.dgalan.padelprat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dgalan.padelprat.ui.theme.padelColors

/** Tarjeta de la app a todo el ancho: blanca con borde en claro y algo más clara que el fondo en oscuro. */
@Composable
fun PadelCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.padelColors.card,
    border: BorderStroke? = MaterialTheme.padelColors.cardBorder?.let { BorderStroke(1.dp, it) },
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color,
        border = border,
        modifier = Modifier.fillMaxWidth().then(modifier),
        content = content
    )
}
