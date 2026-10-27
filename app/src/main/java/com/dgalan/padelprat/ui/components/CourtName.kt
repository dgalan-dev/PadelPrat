package com.dgalan.padelprat.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Nombre de la pista con el patrocinador al lado, más pequeño y en azul. Si no cabe se corta el patrocinador, nunca
 * el nombre. [alpha] apaga los dos (pistas que no se pueden elegir).
 */
@Composable
fun CourtName(
    name: String,
    sponsor: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    alpha: Float = 1f
) {
    Row(modifier = modifier) {
        Text(
            text = name,
            style = style,
            color = color.copy(alpha = color.alpha * alpha),
            maxLines = 1,
            modifier = Modifier.alignByBaseline()
        )
        sponsor?.let {
            Spacer(Modifier.width(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline()
            )
        }
    }
}
