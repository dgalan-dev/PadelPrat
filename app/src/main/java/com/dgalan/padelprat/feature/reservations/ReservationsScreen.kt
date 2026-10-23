package com.dgalan.padelprat.feature.reservations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dgalan.padelprat.R
import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.reservations.Reservation
import com.dgalan.padelprat.data.reservations.ReservationCourt
import com.dgalan.padelprat.data.reservations.ReservationStatus
import com.dgalan.padelprat.data.scheduled.ScheduledBooking
import com.dgalan.padelprat.ui.components.PillSwitch
import com.dgalan.padelprat.ui.theme.PadelPratTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ReservationsScreen(modifier: Modifier = Modifier, viewModel: ReservationsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReservationsContent(
        uiState = uiState,
        onTabSelect = viewModel::onTabSelect,
        onCancelClick = viewModel::onCancelClick,
        onScheduleCancel = viewModel::onScheduleCancel,
        onRefresh = viewModel::onRefresh,
        onRetry = viewModel::onRetry,
        onMessageShow = viewModel::onMessageShow,
        modifier = modifier
    )

    uiState.cancelDialog?.let { dialog ->
        CancelReservationDialog(
            dialog = dialog,
            onConfirm = viewModel::onCancelConfirm,
            onDismiss = viewModel::onCancelDismiss
        )
    }
}

/** Título y selector fijos arriba; tirar hacia abajo recarga solo la lista, así que el indicador sale debajo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservationsContent(
    uiState: ReservationsUiState,
    onTabSelect: (ReservationsTab) -> Unit,
    onCancelClick: (Reservation) -> Unit,
    onScheduleCancel: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMessageShow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.message?.let { stringResource(it.textRes()) }
    val currentOnMessageShow by rememberUpdatedState(onMessageShow)
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            currentOnMessageShow()
        }
    }
    val tabLabels = ReservationsTab.entries.associateWith { stringResource(it.labelRes()) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.reservations_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            Spacer(Modifier.height(16.dp))
            PillSwitch(
                options = ReservationsTab.entries,
                selected = uiState.tab,
                onSelect = onTabSelect,
                label = { tabLabels.getValue(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            // Nunca se queda "refrescando": al soltar se recarga y lo que se ve son las siluetas de carga
            PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                when (val content = uiState.content) {
                    ReservationsContent.Loading -> SkeletonList()

                    is ReservationsContent.Error -> MessageState(
                        message = stringResource(
                            if (content.error == ReservationsError.Network) {
                                R.string.reservations_error_network
                            } else {
                                R.string.reservations_error_unknown
                            }
                        ),
                        action = { Button(onClick = onRetry) { Text(stringResource(R.string.booking_retry)) } }
                    )

                    is ReservationsContent.Loaded ->
                        ReservationList(uiState.tab, content, uiState.scheduled, onCancelClick, onScheduleCancel)
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ReservationList(
    tab: ReservationsTab,
    content: ReservationsContent.Loaded,
    scheduled: List<ScheduledBooking>,
    onCancelClick: (Reservation) -> Unit,
    onScheduleCancel: (String) -> Unit
) {
    val reservations = when (tab) {
        ReservationsTab.Upcoming -> content.upcoming
        ReservationsTab.Past -> content.past
        ReservationsTab.Cancelled -> content.cancelled
    }
    // Las programadas solo en Próximas, encima de las reservas reales
    val shownScheduled = if (tab == ReservationsTab.Upcoming) scheduled else emptyList()
    if (reservations.isEmpty() && shownScheduled.isEmpty()) {
        MessageState(message = stringResource(tab.emptyRes()))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(shownScheduled, key = { "scheduled-${it.id}" }) { booking ->
            ScheduledCard(booking, onCancelClick = { onScheduleCancel(booking.id) })
        }
        itemsIndexed(reservations, key = { _, reservation -> reservation.id }) { index, reservation ->
            when (tab) {
                // La más cercana va destacada, como en el mock
                ReservationsTab.Upcoming -> UpcomingCard(reservation, highlighted = index == 0, onCancelClick)

                ReservationsTab.Past -> PastCard(reservation)

                ReservationsTab.Cancelled -> CancelledCard(reservation)
            }
        }
    }
}

@Composable
private fun UpcomingCard(reservation: Reservation, highlighted: Boolean, onCancelClick: (Reservation) -> Unit) {
    val (lime, onLime) = limeColors()
    ReservationCard(border = if (highlighted) BorderStroke(2.dp, lime) else null) {
        if (highlighted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(text = reservation.start.toLocalDate().dayTag(), background = lime, content = onLime)
                Spacer(Modifier.weight(1f))
                Text(
                    text = reservation.start.timeUntil(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = reservation.start.toLocalDate().longDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = reservation.timeRange(),
            style = if (highlighted) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        reservation.court?.let { CourtLine(it) }
        if (reservation.canCancel) {
            OutlinedButton(onClick = { onCancelClick(reservation) }, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.reservations_cancel))
            }
        }
    }
}

/**
 * Reserva programada: todavía no es una reserva. Se distingue de las reales con el borde discontinuo, el reloj y la
 * etiqueta "Programada" (no lima).
 */
@Composable
private fun ScheduledCard(booking: ScheduledBooking, onCancelClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dashColor = scheme.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = dashColor,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                )
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Tag(
                text = stringResource(R.string.reservations_scheduled_tag),
                background = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = booking.date.longDate(),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Text(
            text = "${booking.start.format(TIME_FORMAT)} – ${booking.end.format(TIME_FORMAT)}",
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface
        )
        Text(
            text = booking.courts.joinToString(" → ") { it.name },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.reservations_scheduled_when, booking.opensAt.openingText()),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
        TextButton(onClick = onCancelClick, modifier = Modifier.padding(top = 2.dp)) {
            Text(stringResource(R.string.reservations_scheduled_cancel))
        }
    }
}

@Composable
private fun PastCard(reservation: Reservation) {
    ReservationCard {
        Text(
            text = reservation.start.toLocalDate().longDate(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = reservation.timeRange(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = reservation.durationAndTotal(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Anuladas en gris, con su etiqueta, para que no se confundan con las activas. */
@Composable
private fun CancelledCard(reservation: Reservation) {
    val scheme = MaterialTheme.colorScheme
    ReservationCard(color = scheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = reservation.start.toLocalDate().longDate(),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Tag(
                text = stringResource(R.string.reservations_cancelled_tag),
                background = scheme.errorContainer,
                content = scheme.onErrorContainer
            )
        }
        Text(
            text = reservation.timeRange(),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReservationCard(
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = color,
        border = border ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

/** Pista con su patrocinador al lado, más pequeño y en azul, como en la parrilla. */
@Composable
private fun CourtLine(court: ReservationCourt) {
    Row {
        Text(
            text = court.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline()
        )
        court.sponsor?.let {
            Spacer(Modifier.width(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

@Composable
private fun Tag(text: String, background: Color, content: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/** Lima de bola. En oscuro tertiaryContainer es un oliva apagado; tertiary mantiene el lima. */
@Composable
private fun limeColors() = MaterialTheme.colorScheme.let { scheme ->
    if (isSystemInDarkTheme()) {
        scheme.tertiary to scheme.onTertiary
    } else {
        scheme.tertiaryContainer to
            scheme.onTertiaryContainer
    }
}

/** Confirmación antes de anular, como pide la web. Mientras se anula no se puede cerrar ni volver a pulsar. */
@Composable
private fun CancelReservationDialog(dialog: CancelDialog, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val reservation = dialog.reservation
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !dialog.inProgress,
            dismissOnClickOutside = !dialog.inProgress
        ),
        title = { Text(stringResource(R.string.reservations_cancel_title)) },
        text = {
            Text(
                stringResource(
                    R.string.reservations_cancel_text,
                    reservation.start.toLocalDate().longDate(),
                    reservation.timeRange()
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.inProgress) {
                if (dialog.inProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.reservations_cancel_confirm), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !dialog.inProgress) {
                Text(stringResource(R.string.reservations_cancel_back))
            }
        }
    )
}

/** Siluetas de tarjetas mientras carga, con un brillo que las recorre. */
@Composable
private fun SkeletonList() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest
    // El valor se lee al dibujar: el brillo solo repinta, no recompone
    val placeholder = Modifier.drawBehind {
        val x = shimmer * size.width * 3f - size.width
        drawRoundRect(
            brush = Brush.linearGradient(listOf(base, highlight, base), Offset(x, 0f), Offset(x + size.width, 0f)),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(3) {
            ReservationCard {
                Box(Modifier.width(110.dp).height(14.dp).then(placeholder))
                Box(Modifier.width(180.dp).height(26.dp).then(placeholder))
                Box(Modifier.width(140.dp).height(14.dp).then(placeholder))
            }
        }
    }
}

/** Mensaje centrado. Usa una lista para que también se pueda tirar hacia abajo para refrescar. */
@Composable
private fun MessageState(message: String, action: (@Composable () -> Unit)? = null) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}

private val SPANISH = Locale.forLanguageTag("es-ES")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SPANISH)

private val OPENING_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'a las' H:mm", SPANISH)

/** "el sábado 24 a las 9:00". */
private fun Instant.openingText(): String = atZone(Syltek.ZONE).format(OPENING_FORMAT)

private fun Reservation.timeRange(): String = "${start.format(TIME_FORMAT)} – ${end.format(TIME_FORMAT)}"

@Composable
private fun Reservation.durationAndTotal(): String = listOfNotNull(
    stringResource(R.string.booking_duration, durationMinutes),
    total
).joinToString(" · ")

/** "Sábado, 24 de octubre". */
private fun LocalDate.longDate(): String = format(LONG_DATE).replaceFirstChar { it.titlecase(SPANISH) }

/** "Hoy", "Mañana" o "Lun 26". */
@Composable
private fun LocalDate.dayTag(): String {
    val today = LocalDate.now(Syltek.ZONE)
    return when (this) {
        today -> stringResource(R.string.booking_today)

        today.plusDays(1) -> stringResource(R.string.reservations_tomorrow)

        else -> {
            val day = dayOfWeek.getDisplayName(TextStyle.SHORT, SPANISH).removeSuffix(".")
                .replaceFirstChar { it.titlecase(SPANISH) }
            "$day $dayOfMonth"
        }
    }
}

/** "en 45 min", "en 3 h" o "en 2 días". */
@Composable
private fun LocalDateTime.timeUntil(): String {
    val minutes = Duration.between(LocalDateTime.now(Syltek.ZONE), this).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 60 -> stringResource(R.string.reservations_in_minutes, minutes.toInt())

        minutes < 24 * 60 -> stringResource(R.string.reservations_in_hours, (minutes / 60).toInt())

        else -> {
            val days = Duration.between(LocalDate.now(Syltek.ZONE).atStartOfDay(), toLocalDate().atStartOfDay())
                .toDays().toInt()
            pluralStringResource(R.plurals.reservations_in_days, days, days)
        }
    }
}

private fun ReservationsTab.labelRes(): Int = when (this) {
    ReservationsTab.Upcoming -> R.string.reservations_tab_upcoming
    ReservationsTab.Past -> R.string.reservations_tab_past
    ReservationsTab.Cancelled -> R.string.reservations_tab_cancelled
}

private fun ReservationsTab.emptyRes(): Int = when (this) {
    ReservationsTab.Upcoming -> R.string.reservations_empty_upcoming
    ReservationsTab.Past -> R.string.reservations_empty_past
    ReservationsTab.Cancelled -> R.string.reservations_empty_cancelled
}

private fun CancelMessage.textRes(): Int = when (this) {
    CancelMessage.Cancelled -> R.string.reservations_cancel_done
    CancelMessage.NotCancelled -> R.string.reservations_cancel_not_done
    CancelMessage.Uncertain -> R.string.reservations_cancel_uncertain
    CancelMessage.ScheduleCancelled -> R.string.reservations_scheduled_cancelled
}

@Preview(showBackground = true)
@Composable
private fun ReservationsContentPreview() {
    val start = LocalDate.now(Syltek.ZONE).plusDays(1).atTime(18, 30)
    val reservation = Reservation(
        id = 1,
        status = ReservationStatus.Booked,
        start = start,
        durationMinutes = 90,
        total = "0,00 €",
        canCancel = true,
        upcoming = true,
        court = ReservationCourt("Pista 2", "San Miguel Magna")
    )
    PadelPratTheme {
        ReservationsContent(
            uiState = ReservationsUiState(
                content = ReservationsContent.Loaded(
                    upcoming = listOf(reservation, reservation.copy(id = 2, start = start.plusDays(2))),
                    past = emptyList(),
                    cancelled = emptyList()
                )
            ),
            onTabSelect = {},
            onCancelClick = {},
            onScheduleCancel = {},
            onRefresh = {},
            onRetry = {},
            onMessageShow = {}
        )
    }
}
