package com.dgalan.padelprat.feature.reservations

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dgalan.padelprat.BuildConfig
import com.dgalan.padelprat.R
import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.friends.FriendReservations
import com.dgalan.padelprat.data.reservations.Reservation
import com.dgalan.padelprat.data.reservations.ReservationCourt
import com.dgalan.padelprat.data.reservations.ReservationStatus
import com.dgalan.padelprat.data.scheduled.ScheduledBooking
import com.dgalan.padelprat.ui.components.CourtName
import com.dgalan.padelprat.ui.components.MessageState
import com.dgalan.padelprat.ui.components.PadelCard
import com.dgalan.padelprat.ui.components.PillSwitch
import com.dgalan.padelprat.ui.components.ShimmerBox
import com.dgalan.padelprat.ui.components.SwipeableSnackbarHost
import com.dgalan.padelprat.ui.components.Tag
import com.dgalan.padelprat.ui.format.SPANISH
import com.dgalan.padelprat.ui.format.hourText
import com.dgalan.padelprat.ui.format.longDate
import com.dgalan.padelprat.ui.format.openingText
import com.dgalan.padelprat.ui.format.shortName
import com.dgalan.padelprat.ui.theme.PadelPratTheme
import com.dgalan.padelprat.ui.theme.padelColors
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** [onBookClick] lleva a la pestaña Reservar (desde la lista vacía). */
@Composable
fun ReservationsScreen(
    onBookClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReservationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReservationsContent(
        uiState = uiState,
        onBookClick = onBookClick,
        onTabSelect = viewModel::onTabSelect,
        onOwnerFilterSelect = viewModel::onOwnerFilterSelect,
        onCancelClick = viewModel::onCancelClick,
        onScheduleCancel = viewModel::onScheduleCancel,
        onScheduleTest = viewModel::onScheduleTest,
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
    onBookClick: () -> Unit,
    onTabSelect: (ReservationsTab) -> Unit,
    onOwnerFilterSelect: (OwnerFilter) -> Unit,
    onCancelClick: (Reservation) -> Unit,
    onScheduleCancel: (String) -> Unit,
    onScheduleTest: (String) -> Unit,
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
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
            val friend = uiState.friend
            if (friend != null && uiState.tab == ReservationsTab.Upcoming) {
                FriendFilter(friend = friend, selected = uiState.ownerFilter, onSelect = onOwnerFilterSelect)
                Spacer(Modifier.height(12.dp))
            }
            (uiState.content as? ReservationsContent.Loaded)?.savedAt?.let { savedAt ->
                OfflineNotice(savedAt)
                Spacer(Modifier.height(12.dp))
            }
            // Nunca se queda "refrescando": al soltar se recarga y lo que se ve son las siluetas de carga
            PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                when (val content = uiState.content) {
                    ReservationsContent.Loading -> SkeletonList()

                    is ReservationsContent.Error -> {
                        val (title, text) = when (content.error) {
                            ReservationsError.Network -> R.string.error_network_title to R.string.error_network_text

                            ReservationsError.Unknown ->
                                R.string.reservations_error_unknown to R.string.error_unknown_text

                            ReservationsError.WebChanged ->
                                R.string.error_web_changed_title to R.string.error_web_changed_text
                        }
                        ScrollableMessage(
                            icon = R.drawable.ic_error,
                            title = stringResource(title),
                            message = stringResource(text),
                            action = { Button(onClick = onRetry) { Text(stringResource(R.string.booking_retry)) } }
                        )
                    }

                    is ReservationsContent.Loaded ->
                        ReservationList(
                            tab = uiState.tab,
                            content = content,
                            scheduled = uiState.scheduled,
                            friend = uiState.friend,
                            ownerFilter = uiState.ownerFilter,
                            onCancelClick = onCancelClick,
                            onScheduleCancel = onScheduleCancel,
                            onScheduleTest = onScheduleTest,
                            onBookClick = onBookClick
                        )
                }
            }
        }
        SwipeableSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

/** De quién es una reserva, cuando las reservas compartidas están activadas. */
private data class Owner(val name: String, val isFriend: Boolean)

@Composable
private fun ReservationList(
    tab: ReservationsTab,
    content: ReservationsContent.Loaded,
    scheduled: List<ScheduledBooking>,
    friend: FriendReservations?,
    ownerFilter: OwnerFilter,
    onCancelClick: (Reservation) -> Unit,
    onScheduleCancel: (String) -> Unit,
    onScheduleTest: (String) -> Unit,
    onBookClick: () -> Unit
) {
    // Las del amigo solo en Próximas, mezcladas por fecha con las mías
    val sharing = tab == ReservationsTab.Upcoming && friend != null
    val friendName = friend?.name ?: stringResource(R.string.reservations_friend_default)
    val mine = when (tab) {
        ReservationsTab.Upcoming -> if (sharing && ownerFilter == OwnerFilter.Friend) emptyList() else content.upcoming
        ReservationsTab.Past -> content.past
        ReservationsTab.Cancelled -> content.cancelled
    }
    val friends = if (sharing && ownerFilter != OwnerFilter.Mine) {
        val now = LocalDateTime.now(Syltek.ZONE)
        friend.reservations.filter { it.end > now }
    } else {
        emptyList()
    }
    val reservations = (mine.map { it to false } + friends.map { it to true })
        .let { if (sharing) it.sortedBy { (reservation, _) -> reservation.start } else it }
    // La más cercana de las mías va destacada, como en el mock
    val nearest = mine.firstOrNull()
    // Las programadas (solo mías) en Próximas, encima de las reservas reales
    val showScheduled = tab == ReservationsTab.Upcoming && !(sharing && ownerFilter == OwnerFilter.Friend)
    val shownScheduled = if (showScheduled) scheduled else emptyList()
    if (reservations.isEmpty() && shownScheduled.isEmpty() && sharing && ownerFilter == OwnerFilter.Friend) {
        ScrollableMessage(
            icon = R.drawable.ic_ball,
            title = stringResource(R.string.reservations_empty_friend, friendName)
        )
        return
    }
    if (reservations.isEmpty() && shownScheduled.isEmpty()) {
        // En Próximas, el siguiente paso es reservar
        val upcoming = tab == ReservationsTab.Upcoming
        ScrollableMessage(
            icon = R.drawable.ic_ball,
            title = stringResource(tab.emptyRes()),
            message = if (upcoming) stringResource(R.string.reservations_empty_upcoming_text) else null,
            action = if (upcoming) {
                { Button(onClick = onBookClick) { Text(stringResource(R.string.reservations_book)) } }
            } else {
                null
            }
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(shownScheduled, key = { "scheduled-${it.id}" }) { booking ->
            ScheduledCard(
                booking = booking,
                onCancelClick = { onScheduleCancel(booking.id) },
                onTestClick = { onScheduleTest(booking.id) }
            )
        }
        items(
            reservations,
            key = { (reservation, isFriend) -> if (isFriend) "friend-${reservation.id}" else reservation.id }
        ) { (reservation, isFriend) ->
            when (tab) {
                ReservationsTab.Upcoming -> UpcomingCard(
                    reservation = reservation,
                    highlighted = !isFriend && reservation == nearest,
                    owner = when {
                        !sharing -> null
                        isFriend -> Owner(friendName, isFriend = true)
                        else -> Owner(stringResource(R.string.reservations_owner_me), isFriend = false)
                    },
                    onCancelClick = onCancelClick
                )

                ReservationsTab.Past -> PastCard(reservation)

                ReservationsTab.Cancelled -> CancelledCard(reservation)
            }
        }
    }
}

/** [owner], solo con las reservas compartidas activadas. Las del amigo llegan con `canCancel` en `false`. */
@Composable
private fun UpcomingCard(
    reservation: Reservation,
    highlighted: Boolean,
    owner: Owner?,
    onCancelClick: (Reservation) -> Unit
) {
    val lime = MaterialTheme.padelColors.accent
    val onLime = MaterialTheme.padelColors.onAccent
    ReservationCard(border = if (highlighted) BorderStroke(2.dp, lime) else defaultCardBorder()) {
        if (highlighted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                owner?.let { OwnerLabel(it) }
                Tag(text = reservation.start.toLocalDate().dayTag(), containerColor = lime, contentColor = onLime)
                Spacer(Modifier.weight(1f))
                Text(
                    text = reservation.start.timeUntil(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                owner?.let { OwnerLabel(it) }
                Text(
                    text = reservation.start.toLocalDate().longDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = reservation.timeRange(),
            style = if (highlighted) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        reservation.court?.let {
            CourtName(
                name = it.name,
                sponsor = it.sponsor,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val context = LocalContext.current
        val shareText = reservation.shareText()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { context.startActivity(shareIntent(shareText)) }) {
                Text(stringResource(R.string.reservations_share))
            }
            if (reservation.canCancel) {
                // Anular borra la reserva: en rojo, como "Cerrar sesión", para que no se pulse por costumbre
                OutlinedButton(
                    onClick = { onCancelClick(reservation) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.reservations_cancel))
                }
            }
        }
    }
}

/** Círculo con la inicial y el nombre: azul para las mías, lima para las del amigo. */
@Composable
private fun OwnerLabel(owner: Owner) {
    val (container, content) = if (owner.isFriend) {
        MaterialTheme.padelColors.accent to MaterialTheme.padelColors.onAccent
    } else {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .background(container, CircleShape)
        ) {
            Text(
                text = if (owner.isFriend) {
                    owner.name.take(1).uppercase(SPANISH)
                } else {
                    stringResource(R.string.reservations_owner_me_avatar)
                },
                style = MaterialTheme.typography.labelSmall,
                color = content
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = owner.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Filtro de Próximas (todas, mías o del amigo) y cuándo publicó el amigo sus reservas por última vez. */
@Composable
private fun FriendFilter(friend: FriendReservations, selected: OwnerFilter, onSelect: (OwnerFilter) -> Unit) {
    val name = friend.name ?: stringResource(R.string.reservations_friend_default)
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OwnerFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter == selected,
                    onClick = { onSelect(filter) },
                    label = {
                        Text(
                            when (filter) {
                                OwnerFilter.All -> stringResource(R.string.reservations_filter_all)
                                OwnerFilter.Mine -> stringResource(R.string.reservations_filter_mine)
                                OwnerFilter.Friend -> stringResource(R.string.reservations_filter_friend, name)
                            }
                        )
                    }
                )
            }
        }
        friend.updatedAt?.let {
            Text(
                text = stringResource(R.string.reservations_friend_updated, name, it.updatedAgo()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Sobre la copia guardada de las reservas, cuando no hay conexión. */
@Composable
private fun OfflineNotice(savedAt: Instant) {
    Text(
        text = stringResource(R.string.reservations_offline, savedAt.updatedAgo()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

/** "ahora mismo", "hace 5 min", "hace 3 h" o "hace 2 días". */
@Composable
private fun Instant.updatedAgo(): String {
    val minutes = Duration.between(this, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.reservations_updated_now)

        minutes < 60 -> stringResource(R.string.reservations_updated_minutes, minutes.toInt())

        minutes < 24 * 60 -> stringResource(R.string.reservations_updated_hours, (minutes / 60).toInt())

        else -> {
            val days = (minutes / (24 * 60)).toInt()
            pluralStringResource(R.plurals.reservations_updated_days, days, days)
        }
    }
}

/**
 * Texto para compartir, en varias líneas y con emojis pero sin el formato de WhatsApp (`*negrita*`), que en otras
 * apps se vería con los asteriscos. La pista (con su patrocinador, si lo tiene), solo si se ha podido leer.
 */
@Composable
private fun Reservation.shareText(): String {
    val day = start.toLocalDate().longDate().replaceFirstChar { it.lowercase(SPANISH) }
    return listOfNotNull(
        stringResource(R.string.reservations_share_day, day),
        stringResource(
            R.string.reservations_share_time,
            start.toLocalTime().hourText(),
            end.toLocalTime().hourText(),
            durationMinutes
        ),
        court?.let { court ->
            court.sponsor?.let { stringResource(R.string.reservations_share_court_sponsor, court.name, it) }
                ?: stringResource(R.string.reservations_share_court, court.name)
        }
    ).joinToString("\n")
}

/** El menú de compartir del sistema, con el texto ya escrito. */
private fun shareIntent(text: String): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
    null
)

/**
 * Reserva programada: todavía no es una reserva. Se distingue de las reales con el borde discontinuo, el reloj y la
 * etiqueta "Programada" (no lima).
 */
@Composable
private fun ScheduledCard(booking: ScheduledBooking, onCancelClick: () -> Unit, onTestClick: () -> Unit) {
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
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = booking.date.longDate(),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Text(
            text = "${booking.start.hourText()} – ${booking.end.hourText()}",
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface
        )
        booking.alternativeStart?.let { alternative ->
            Text(
                text = stringResource(
                    R.string.reservations_scheduled_alternative,
                    alternative.hourText(),
                    alternative.plusMinutes(booking.durationMinutes.toLong()).hourText()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
        Text(
            text = (booking.courts.map { it.name } + stringResource(R.string.reservations_scheduled_any_court))
                .joinToString(" → "),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.reservations_scheduled_when, booking.opensAt.openingText()),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
        Row {
            TextButton(onClick = onCancelClick, modifier = Modifier.padding(top = 2.dp)) {
                Text(stringResource(R.string.reservations_scheduled_cancel))
            }
            // Solo en debug: prueba de todo el camino sin reservar (ver ReservationsViewModel.onScheduleTest)
            if (BuildConfig.DEBUG) {
                TextButton(onClick = onTestClick, modifier = Modifier.padding(top = 2.dp)) {
                    Text(stringResource(R.string.reservations_scheduled_test))
                }
            }
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
    ReservationCard(color = MaterialTheme.padelColors.cardMuted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = reservation.start.toLocalDate().longDate(),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Tag(
                text = stringResource(R.string.reservations_cancelled_tag),
                containerColor = scheme.errorContainer,
                contentColor = scheme.onErrorContainer
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
    border: BorderStroke? = defaultCardBorder(),
    color: Color = MaterialTheme.padelColors.card,
    content: @Composable () -> Unit
) {
    PadelCard(modifier = modifier, color = color, border = border) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Composable
private fun defaultCardBorder(): BorderStroke? = MaterialTheme.padelColors.cardBorder?.let { BorderStroke(1.dp, it) }

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

/** Siluetas de tarjetas mientras carga. */
@Composable
private fun SkeletonList() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(3) {
            ReservationCard {
                ShimmerBox(Modifier.width(110.dp).height(14.dp))
                ShimmerBox(Modifier.width(180.dp).height(26.dp))
                ShimmerBox(Modifier.width(140.dp).height(14.dp))
            }
        }
    }
}

/** [MessageState] dentro de una lista, para que también se pueda tirar hacia abajo para refrescar. */
@Composable
private fun ScrollableMessage(
    icon: Int,
    title: String,
    message: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { MessageState(icon = icon, title = title, message = message, action = action) }
    }
}

private fun Reservation.timeRange(): String = "${start.toLocalTime().hourText()} – ${end.toLocalTime().hourText()}"

@Composable
private fun Reservation.durationAndTotal(): String = listOfNotNull(
    stringResource(R.string.booking_duration, durationMinutes),
    total
).joinToString(" · ")

/** "Hoy", "Mañana" o "Lun 26". */
@Composable
private fun LocalDate.dayTag(): String {
    val today = LocalDate.now(Syltek.ZONE)
    return when (this) {
        today -> stringResource(R.string.booking_today)

        today.plusDays(1) -> stringResource(R.string.reservations_tomorrow)

        else -> {
            "${dayOfWeek.shortName()} $dayOfMonth"
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
    CancelMessage.ScheduleTestQueued -> R.string.reservations_scheduled_test_queued
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
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
                    upcoming = listOf(reservation, reservation.copy(id = 2, start = start.plusDays(3))),
                    past = emptyList(),
                    cancelled = emptyList()
                ),
                friend = FriendReservations(
                    name = "Marc",
                    updatedAt = Instant.now().minusSeconds(5 * 60),
                    reservations = listOf(
                        reservation.copy(
                            id = 3,
                            start = start.plusDays(1).withHour(20),
                            total = null,
                            canCancel = false,
                            court = ReservationCourt("Pista 5", null)
                        )
                    )
                )
            ),
            onBookClick = {},
            onTabSelect = {},
            onOwnerFilterSelect = {},
            onCancelClick = {},
            onScheduleCancel = {},
            onScheduleTest = {},
            onRefresh = {},
            onRetry = {},
            onMessageShow = {}
        )
    }
}
