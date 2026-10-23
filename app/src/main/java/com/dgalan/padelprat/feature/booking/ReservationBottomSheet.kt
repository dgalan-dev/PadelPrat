package com.dgalan.padelprat.feature.booking

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dgalan.padelprat.R
import com.dgalan.padelprat.core.network.Syltek
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Hoja desde abajo con los datos del hueco elegido. Abrirla no bloquea la pista: solo se reserva al pulsar
 * "Confirmar reserva". Mientras se envía no se puede cerrar, para no dejar una reserva a medias ni lanzar otra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationBottomSheet(sheet: ReservationSheet, onConfirmClick: () -> Unit, onDismiss: () -> Unit) {
    val isBooking by rememberUpdatedState(sheet.status == ReservationStatus.Booking)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !isBooking }
    )
    val scope = rememberCoroutineScope()
    // Los botones cierran con la animación de bajada y después avisan
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AnimatedContent(
            targetState = sheet.status is ReservationStatus.Success,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "reservationSheet"
        ) { success ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            ) {
                val status = sheet.status
                if (success && status is ReservationStatus.Success) {
                    SuccessContent(sheet, status.total, onDone = close)
                } else {
                    DetailsContent(sheet, onConfirmClick = onConfirmClick, onClose = close)
                }
            }
        }
    }
}

@Composable
private fun DetailsContent(
    sheet: ReservationSheet,
    onConfirmClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = sheet.status
    val booking = status == ReservationStatus.Booking
    val end = sheet.slot.time.plusMinutes(sheet.durationMinutes.toLong())
    Column(modifier = modifier.fillMaxWidth()) {
        AvailableTag()
        Spacer(Modifier.height(8.dp))
        Text(
            text = sheet.slot.time.format(TIME_FORMAT),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.reservation_until, end.format(TIME_FORMAT), sheet.durationMinutes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        InfoRow(icon = R.drawable.ic_nav_booking, title = sheet.longDate())
        Spacer(Modifier.height(12.dp))
        CourtInfoRow(sheet)
        Spacer(Modifier.height(24.dp))

        if (status is ReservationStatus.Failed) {
            Text(
                text = stringResource(status.error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(Modifier.height(16.dp))
        }

        // Solo se ofrece reintentar si seguro que no se ha creado nada; si la reserva puede existir o ha quedado a
        // medias, otro intento podría duplicarla
        val canConfirm = status !is ReservationStatus.Failed ||
            status.error == ReservationError.Network ||
            status.error == ReservationError.Unknown
        if (canConfirm) {
            Button(
                onClick = onConfirmClick,
                enabled = !booking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (booking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.reservation_confirm), style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = onClose,
            enabled = !booking,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = stringResource(if (canConfirm) R.string.reservation_change else R.string.reservation_close),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SuccessContent(sheet: ReservationSheet, total: String?, onDone: () -> Unit) {
    val end = sheet.slot.time.plusMinutes(sheet.durationMinutes.toLong())
    // El círculo con el ✓ aparece creciendo con un rebote
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val (background, content) = freeColors()

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.reservation_success_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.reservation_success_time,
                sheet.longDate(),
                sheet.slot.time.format(TIME_FORMAT),
                end.format(TIME_FORMAT)
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = listOfNotNull(sheet.court.name, sheet.court.sponsor).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (total != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.reservation_total, total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(stringResource(R.string.reservation_done), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AvailableTag() {
    val (background, content) = freeColors()
    Text(
        text = stringResource(R.string.reservation_available),
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun InfoRow(icon: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfoIcon(icon)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Pista con su patrocinador al lado (como en las tarjetas) y el tipo debajo. */
@Composable
private fun CourtInfoRow(sheet: ReservationSheet) {
    val court = sheet.court
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfoIcon(if (court.covered) R.drawable.ic_court_covered else R.drawable.ic_court_uncovered)
        Spacer(Modifier.width(12.dp))
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = court.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alignByBaseline()
                )
                court.sponsor?.let {
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
            Text(
                text = stringResource(if (court.covered) R.string.booking_covered else R.string.booking_uncovered),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoIcon(icon: Int) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val SPANISH = Locale.forLanguageTag("es-ES")
private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SPANISH)

/** "Viernes, 23 de octubre". */
private fun ReservationSheet.longDate(): String =
    slot.start.atZone(Syltek.ZONE).format(LONG_DATE).replaceFirstChar { it.titlecase(SPANISH) }

private fun ReservationError.messageRes(): Int = when (this) {
    ReservationError.SlotUnavailable -> R.string.reservation_error_slot_unavailable
    ReservationError.Network -> R.string.reservation_error_network
    ReservationError.Uncertain -> R.string.reservation_error_uncertain
    ReservationError.Incomplete -> R.string.reservation_error_incomplete
    ReservationError.Unknown -> R.string.reservation_error_unknown
}
