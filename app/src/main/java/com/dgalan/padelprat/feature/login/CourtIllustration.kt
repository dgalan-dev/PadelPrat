package com.dgalan.padelprat.feature.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp

// Proporción de la pista dibujada (ancho / alto) y anchura del fondo respecto al frente, para la perspectiva
private const val COURT_ASPECT = 1.6f
private const val BACK_WIDTH = 0.62f

// Altura de las paredes en el fondo, relativa al alto de la pista (hacia delante crecen con la perspectiva)
private const val WALL_HEIGHT = 0.22f

// Profundidades aproximadas con la perspectiva: la red no queda en la mitad exacta porque el fondo se ve más corto
private const val NET_DEPTH = 0.42f
private const val BACK_SERVICE_DEPTH = 0.17f
private const val FRONT_SERVICE_DEPTH = 0.72f

// Tramos de cristal de las paredes laterales, junto a cada fondo; en medio va malla metálica
private const val SIDE_GLASS_DEPTH = 0.2f

/**
 * Pista de pádel en perspectiva, con paredes de cristal y malla, y una bola que pelotea de un campo a otro. Ocupa el
 * máximo que cabe manteniendo la proporción y queda centrada.
 */
@Composable
fun CourtIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    // En claro, pista azul (primary aclarado un poco) con líneas blancas como una real; en oscuro, primary sería
    // demasiado claro
    val courtColor = if (dark) scheme.primaryContainer else lerp(scheme.primary, scheme.primaryContainer, 0.25f)
    val lineColor = if (dark) scheme.onPrimaryContainer.copy(alpha = 0.6f) else scheme.onPrimary
    val netColor = if (dark) scheme.onPrimaryContainer.copy(alpha = 0.7f) else scheme.onPrimary.copy(alpha = 0.85f)
    // Las paredes quedan sobre el fondo de la pantalla, así que se tiñen con el azul de la pista
    val wallColors = WallColors(
        glass = courtColor.copy(alpha = 0.18f),
        frame = if (dark) scheme.onPrimaryContainer.copy(alpha = 0.45f) else courtColor,
        mesh = if (dark) scheme.onPrimaryContainer.copy(alpha = 0.25f) else courtColor.copy(alpha = 0.45f)
    )
    val ballColor = if (dark) scheme.tertiary else scheme.tertiaryContainer
    val shadowColor = scheme.onSurface.copy(alpha = 0.2f)

    val transition = rememberInfiniteTransition(label = "court")
    // Recorrido de la bola entre los dos campos: 0 = bote en el campo de delante, 1 = bote en el del fondo
    val rally by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rally"
    )

    // La pista y la red no cambian: se calculan y se graban una sola vez en capas (se rehacen solo si cambia el
    // tamaño o el tema). En cada fotograma solo se repintan esas capas ya grabadas, la sombra y la bola.
    Spacer(
        modifier = modifier.drawWithCache {
            val courtWidth = minOf(size.width, size.height / (1f + WALL_HEIGHT) * COURT_ASPECT)
            val courtHeight = courtWidth / COURT_ASPECT
            val totalHeight = courtHeight * (1f + WALL_HEIGHT)
            val court = Court(
                origin = Offset(
                    x = (size.width - courtWidth) / 2,
                    y = (size.height - totalHeight) / 2 + courtHeight * WALL_HEIGHT
                ),
                size = Size(courtWidth, courtHeight)
            )
            val courtLayer = obtainGraphicsLayer().apply {
                record {
                    drawWalls(court, wallColors)
                    val outline = court.outline()
                    drawPath(outline, courtColor)
                    drawCourtLines(court, outline, lineColor)
                }
            }
            val netLayer = obtainGraphicsLayer().apply {
                record { drawNet(court, netColor) }
            }
            val ball = Ball(court)

            onDrawBehind {
                val position = ball.position(rally)
                drawLayer(courtLayer)
                // Con la bola en el campo del fondo, la red queda delante de ella
                if (position.depth < NET_DEPTH) {
                    drawBall(ball, position, rally, ballColor, shadowColor)
                    drawLayer(netLayer)
                } else {
                    drawLayer(netLayer)
                    drawBall(ball, position, rally, ballColor, shadowColor)
                }
            }
        }
    )
}

/** Trapecio de la pista: el fondo (depth = 0) es más estrecho que el frente (depth = 1). */
private class Court(val origin: Offset, val size: Size) {
    /** Punto del suelo: [across] de 0 (izquierda) a 1 (derecha) y [depth] de 0 (fondo) a 1 (frente). */
    fun point(across: Float, depth: Float): Offset {
        val width = size.width * scale(depth)
        val left = origin.x + (size.width - width) / 2
        return Offset(left + width * across, origin.y + size.height * depth)
    }

    /** Escala de la perspectiva en esa profundidad: 1 en el frente y [BACK_WIDTH] en el fondo. */
    fun scale(depth: Float) = lerp(BACK_WIDTH, 1f, depth)

    /** Altura de las paredes en esa profundidad, que crece hacia delante con la perspectiva. */
    fun wallHeight(depth: Float) = size.height * WALL_HEIGHT * scale(depth) / BACK_WIDTH

    fun outline() = Path().apply {
        moveTo(point(0f, 0f))
        lineTo(point(1f, 0f))
        lineTo(point(1f, 1f))
        lineTo(point(0f, 1f))
        close()
    }
}

private fun Path.moveTo(offset: Offset) = moveTo(offset.x, offset.y)

private fun Path.lineTo(offset: Offset) = lineTo(offset.x, offset.y)

private fun Offset.up(amount: Float) = copy(y = y - amount)

private fun DrawScope.drawCourtLines(court: Court, outline: Path, color: Color) {
    val stroke = 2.5f * density
    val line = { from: Offset, to: Offset -> drawLine(color, from, to, strokeWidth = stroke, cap = StrokeCap.Round) }

    drawPath(outline, color, style = Stroke(width = stroke))
    line(court.point(0f, BACK_SERVICE_DEPTH), court.point(1f, BACK_SERVICE_DEPTH))
    line(court.point(0f, FRONT_SERVICE_DEPTH), court.point(1f, FRONT_SERVICE_DEPTH))
    line(court.point(0.5f, BACK_SERVICE_DEPTH), court.point(0.5f, FRONT_SERVICE_DEPTH))
}

private class WallColors(val glass: Color, val frame: Color, val mesh: Color)

/**
 * Paredes como en una pista real: cristal en todo el fondo con una franja de malla encima, y en los laterales
 * cristal junto a cada fondo y malla metálica en el tramo central.
 */
private fun DrawScope.drawWalls(court: Court, colors: WallColors) {
    val frame = 1.5f * density

    // Fondo: cristal hasta el 75 % de la altura y malla en el resto, con montantes que separan los paneles
    val backLeft = court.point(0f, 0f)
    val backRight = court.point(1f, 0f)
    val backHeight = court.wallHeight(0f)
    val glassTop = backHeight * 0.75f
    drawRect(colors.glass, Offset(backLeft.x, backLeft.y - glassTop), Size(backRight.x - backLeft.x, glassTop))
    drawMesh(backLeft.up(glassTop), backRight.up(glassTop), backHeight - glassTop, backHeight - glassTop, colors.mesh)
    for (i in 0..4) {
        val x = lerp(backLeft.x, backRight.x, i / 4f)
        drawLine(colors.frame, Offset(x, backLeft.y), Offset(x, backLeft.y - backHeight), strokeWidth = frame)
    }
    drawLine(colors.frame, backLeft.up(glassTop), backRight.up(glassTop), strokeWidth = frame)
    drawLine(colors.frame, backLeft.up(backHeight), backRight.up(backHeight), strokeWidth = frame)

    // Laterales
    for (across in listOf(0f, 1f)) {
        val glassSections = listOf(0f to SIDE_GLASS_DEPTH, 1f - SIDE_GLASS_DEPTH to 1f)
        for ((from, to) in glassSections) {
            drawSideGlass(court, across, from, to, colors)
        }
        val meshFrom = court.point(across, SIDE_GLASS_DEPTH)
        val meshTo = court.point(across, 1f - SIDE_GLASS_DEPTH)
        drawMesh(
            meshFrom,
            meshTo,
            court.wallHeight(SIDE_GLASS_DEPTH),
            court.wallHeight(1f - SIDE_GLASS_DEPTH),
            colors.mesh
        )
        drawLine(
            colors.frame,
            meshFrom.up(court.wallHeight(SIDE_GLASS_DEPTH)),
            meshTo.up(court.wallHeight(1f - SIDE_GLASS_DEPTH)),
            strokeWidth = frame
        )
    }
}

private fun DrawScope.drawSideGlass(court: Court, across: Float, from: Float, to: Float, colors: WallColors) {
    val bottomFrom = court.point(across, from)
    val bottomTo = court.point(across, to)
    val topFrom = bottomFrom.up(court.wallHeight(from))
    val topTo = bottomTo.up(court.wallHeight(to))
    val panel = Path().apply {
        moveTo(bottomFrom)
        lineTo(bottomTo)
        lineTo(topTo)
        lineTo(topFrom)
        close()
    }
    drawPath(panel, colors.glass)
    drawPath(panel, colors.frame, style = Stroke(width = 1.5f * density))
}

/**
 * Malla metálica en un tramo de pared que va de [from] a [to] (puntos del suelo) con alturas [heightFrom] y
 * [heightTo]: montantes verticales y alambres horizontales siguiendo la perspectiva.
 */
private fun DrawScope.drawMesh(from: Offset, to: Offset, heightFrom: Float, heightTo: Float, color: Color) {
    val stroke = 0.6f * density
    val columns = 14
    for (i in 0..columns) {
        val t = i / columns.toFloat()
        val base = Offset(lerp(from.x, to.x, t), lerp(from.y, to.y, t))
        drawLine(color, base, base.up(lerp(heightFrom, heightTo, t)), strokeWidth = stroke)
    }
    val rows = 4
    for (j in 1 until rows) {
        val f = j / rows.toFloat()
        drawLine(color, from.up(heightFrom * f), to.up(heightTo * f), strokeWidth = stroke)
    }
}

private fun DrawScope.drawNet(court: Court, color: Color) {
    val left = court.point(0f, NET_DEPTH)
    val right = court.point(1f, NET_DEPTH)
    val netHeight = court.size.height * 0.12f
    val top = left.y - netHeight
    val bottom = left.y
    val stroke = 2f * density

    // Velo translúcido para que la red se lea como un plano
    drawRect(
        color = color.copy(alpha = color.alpha * 0.12f),
        topLeft = Offset(left.x, top),
        size = Size(right.x - left.x, netHeight)
    )

    // Malla cuadriculada: celdas cuadradas, con tantas columnas como quepan a lo ancho
    val meshColor = color.copy(alpha = color.alpha * 0.35f)
    val meshStroke = stroke * 0.4f
    val rows = 5
    val cell = netHeight / rows
    for (row in 1 until rows) {
        val y = top + cell * row
        drawLine(meshColor, Offset(left.x, y), Offset(right.x, y), strokeWidth = meshStroke)
    }
    val columns = ((right.x - left.x) / cell).toInt()
    for (column in 1 until columns) {
        val x = lerp(left.x, right.x, column / columns.toFloat())
        drawLine(meshColor, Offset(x, top), Offset(x, bottom), strokeWidth = meshStroke)
    }

    // Cuerda inferior, cinta superior más gruesa y postes
    drawLine(meshColor, Offset(left.x, bottom), Offset(right.x, bottom), strokeWidth = meshStroke * 2)
    drawLine(color, Offset(left.x, top), Offset(right.x, top), strokeWidth = stroke * 1.5f, cap = StrokeCap.Round)
    drawLine(color, Offset(left.x, bottom), Offset(left.x, top), strokeWidth = stroke * 1.5f, cap = StrokeCap.Round)
    drawLine(color, Offset(right.x, bottom), Offset(right.x, top), strokeWidth = stroke * 1.5f, cap = StrokeCap.Round)
}

/** Dónde está la bola en un momento del peloteo: punto del suelo bajo ella, profundidad y altura sobre el suelo. */
private class BallPosition(val ground: Offset, val depth: Float, val height: Float, val scale: Float)

/**
 * Geometría de la bola calculada una vez, con la bola centrada en el origen; al dibujarla solo se desplaza y se
 * escala según la profundidad.
 */
private class Ball(private val court: Court) {
    val radius = court.size.height * 0.035f
    private val maxHeight = court.size.height * 0.32f
    val clip = Path().apply { addOval(Rect(Offset.Zero, radius)) }
    val seam = Stroke(width = radius * 0.14f, cap = StrokeCap.Round)
    val seamSize = Size(radius * 2, radius * 2)
    val leftSeamTopLeft = Offset(-radius * 2.2f, -radius)
    val rightSeamTopLeft = Offset(radius * 0.2f, -radius)

    /** Vuela en parábola del bote de delante (a la izquierda) al bote del fondo (a la derecha) y vuelve. */
    fun position(rally: Float): BallPosition {
        val depth = lerp(0.84f, 0.16f, rally)
        val across = lerp(0.36f, 0.64f, rally)
        val scale = court.scale(depth)
        val arc = 4f * rally * (1f - rally)
        return BallPosition(court.point(across, depth), depth, maxHeight * arc * scale, scale)
    }
}

private val SeamColor = Color.White.copy(alpha = 0.8f)

private fun DrawScope.drawBall(ball: Ball, position: BallPosition, rally: Float, ballColor: Color, shadowColor: Color) {
    val radius = ball.radius
    val ground = position.ground

    // La sombra sigue a la bola por el suelo y se encoge y se aclara cuando sube
    val lift = (position.height / (radius * 10f)).coerceIn(0f, 1f)
    val shadowScale = position.scale * (1f - 0.5f * lift)
    drawOval(
        color = shadowColor.copy(alpha = shadowColor.alpha * (1f - 0.5f * lift)),
        topLeft = Offset(ground.x - radius * shadowScale, ground.y - radius * 0.35f * shadowScale),
        size = Size(radius * 2 * shadowScale, radius * 0.7f * shadowScale)
    )

    translate(left = ground.x, top = ground.y - radius * position.scale - position.height) {
        // Más pequeña al fondo por la perspectiva, y girando mientras vuela
        scale(position.scale, pivot = Offset.Zero) {
            drawCircle(ballColor, radius, Offset.Zero)
            rotate(degrees = rally * 540f, pivot = Offset.Zero) {
                clipPath(ball.clip) {
                    drawArc(SeamColor, -55f, 110f, false, ball.leftSeamTopLeft, ball.seamSize, style = ball.seam)
                    drawArc(SeamColor, 125f, 110f, false, ball.rightSeamTopLeft, ball.seamSize, style = ball.seam)
                }
            }
        }
    }
}
