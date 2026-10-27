package com.dgalan.padelprat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Redondeos de los mocks. extraSmall: etiquetas; small: chips de hora; medium: avisos y chips de día;
// large: tarjetas; extraLarge (diálogos y hojas): el de Material 3.
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
