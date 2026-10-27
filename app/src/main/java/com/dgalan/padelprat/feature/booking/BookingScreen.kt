package com.dgalan.padelprat.feature.booking

import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dgalan.padelprat.R
import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.booking.BOOKING_DURATIONS
import com.dgalan.padelprat.data.booking.Court
import com.dgalan.padelprat.data.booking.CourtSlots
import com.dgalan.padelprat.data.booking.DayPart
import com.dgalan.padelprat.data.booking.Slot
import com.dgalan.padelprat.data.booking.SlotState
import com.dgalan.padelprat.ui.components.PillSwitch
import com.dgalan.padelprat.ui.theme.PadelPratTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun BookingScreen(modifier: Modifier = Modifier, viewModel: BookingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = (uiState.message as? BookingMessage.Scheduled)?.let {
        stringResource(R.string.schedule_saved, it.opensAt.openingText())
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShow()
        }
    }

    Box(modifier = modifier) {
        BookingContent(
            uiState = uiState,
            onDaySelect = viewModel::onDaySelect,
            onDayPartSelect = viewModel::onDayPartSelect,
            onDurationSelect = viewModel::onDurationSelect,
            onSlotClick = viewModel::onSlotClick,
            onScheduleTimeSelect = viewModel::onScheduleTimeSelect,
            onScheduleCourtToggle = viewModel::onScheduleCourtToggle,
            onScheduleSave = viewModel::onScheduleSave,
            onRefresh = viewModel::onRefresh,
            onRetry = viewModel::onRetry
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    uiState.sheet?.let { sheet ->
        ReservationBottomSheet(
            sheet = sheet,
            limitReached = uiState.reservationLimitReached,
            onConfirmClick = viewModel::onConfirmClick,
            onDismiss = viewModel::onSheetDismiss
        )
    }
}

/**
 * Una sola lista: el título se va al hacer scroll y los filtros se quedan fijos arriba (cabecera fija), con una línea
 * debajo cuando ya hay contenido pasando por detrás. Tirar hacia abajo recarga las pistas, así que el indicador sale
 * justo debajo de los filtros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingContent(
    uiState: BookingUiState,
    onDaySelect: (LocalDate) -> Unit,
    onDayPartSelect: (DayPart) -> Unit,
    onDurationSelect: (Int) -> Unit,
    onSlotClick: (courtId: Int, start: Instant) -> Unit,
    onScheduleTimeSelect: (LocalTime) -> Unit,
    onScheduleCourtToggle: (Int) -> Unit,
    onScheduleSave: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Cada día empieza arriba: el estado de scroll es nuevo en el mismo instante en que cambia el contenido. Si se
    // arrastrara la posición del día anterior, la lista saltaba al cargar el nuevo (con solo 3 siluetas no cabía).
    val listState = remember(uiState.selectedDay) { LazyListState() }
    val filtersPinned by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    // Entrada de las tarjetas: solo las que se componen en el primer frame tras cargar, que son las que se ven. Las que
    // llegan después con el scroll aparecen sin más. Se hace una vez por día y filtros (al volver a uno ya visto no se
    // repite) y otra vez tras cada recarga (siluetas).
    val entranceKey = EntranceKey(uiState.selectedDay, uiState.dayPart, uiState.durationMinutes)
    val playedEntrances = remember { mutableStateSetOf<EntranceKey>() }
    if (uiState.content is BookingContent.Loading) SideEffect { playedEntrances.remove(entranceKey) }
    val entranceOpen = uiState.content is BookingContent.Loaded && entranceKey !in playedEntrances
    if (entranceOpen) {
        LaunchedEffect(entranceKey) {
            // Al llegar el siguiente frame, el anterior ya ha medido la lista y compuesto las tarjetas visibles
            withFrameNanos {}
            playedEntrances.add(entranceKey)
        }
    }
    // Pistas desplegadas (con las horas no disponibles a la vista); al cambiar de día o de filtros se pliegan todas
    val expandedCourts = remember(entranceKey) { mutableStateSetOf<Int>() }
    val pullState = rememberPullToRefreshState()
    // Borde inferior de los filtros dentro de la lista, para colocar ahí el indicador de refresco
    var filtersBottom by remember { mutableIntStateOf(0) }

    // Nunca se queda "refrescando": al soltar se recarga y lo que se ve son las siluetas de carga
    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        state = pullState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = false,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, filtersBottom) }
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "title") {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    // "Hola, Marc", como en el mock; hasta que llegan los datos del perfil, solo el título
                    uiState.firstName?.let {
                        Text(
                            text = stringResource(R.string.booking_greeting, it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.booking_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            stickyHeader(key = "filters") {
                Filters(
                    uiState = uiState,
                    pinned = filtersPinned,
                    onDaySelect = onDaySelect,
                    onDayPartSelect = onDayPartSelect,
                    onDurationSelect = onDurationSelect,
                    modifier = Modifier.onGloballyPositioned { filtersBottom = it.boundsInParent().bottom.toInt() }
                )
            }
            if (uiState.reservationLimitReached) {
                item(key = "limit") {
                    LimitBanner(Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
                }
            }
            when (val content = uiState.content) {
                BookingContent.Loading -> items(SKELETON_CARDS, key = { "skeleton$it" }) {
                    SkeletonCourtCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
                }

                is BookingContent.Error -> item(key = "error") { ErrorState(content.error, onRetry) }

                is BookingContent.Loaded -> if (content.courts.isEmpty()) {
                    item(key = "empty") { EmptyState(uiState.dayPart, uiState.durationMinutes) }
                } else {
                    courtItems(
                        courts = content.courts,
                        selectedSlot = uiState.sheet?.selectedSlot,
                        entranceOpen = entranceOpen,
                        expandedCourts = expandedCourts,
                        onSlotClick = onSlotClick
                    )
                }

                is BookingContent.Schedule ->
                    scheduleItems(content.form, onScheduleTimeSelect, onScheduleCourtToggle, onScheduleSave)
            }
        }
    }
}

private data class EntranceKey(val day: LocalDate, val dayPart: DayPart, val durationMinutes: Int)

private const val SKELETON_CARDS = 3

/** Aviso de que se ha llegado al máximo de reservas del club: se puede mirar, pero no reservar otra. */
@Composable
private fun LimitBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.reservation_limit_reached,
                Syltek.MAX_ACTIVE_RESERVATIONS,
                Syltek.MAX_ACTIVE_RESERVATIONS
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

private fun LazyListScope.courtItems(
    courts: List<CourtSlots>,
    selectedSlot: SelectedSlot?,
    entranceOpen: Boolean,
    expandedCourts: MutableSet<Int>,
    onSlotClick: (courtId: Int, start: Instant) -> Unit
) {
    itemsIndexed(courts, key = { _, courtSlots -> courtSlots.court.id }) { index, courtSlots ->
        // Tras las siluetas la lista siempre está arriba, así que el índice es también el orden en pantalla
        EntranceAnimation(
            delayMillis = index * 50,
            animate = entranceOpen,
            // Sin animateItem: con la recolocación animada, al desplegar una tarjeta las de debajo iban por detrás de su
            // crecimiento y se montaban encima; y el fundido de salida dibujaba las que se iban por encima del título.
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
        ) {
            val courtId = courtSlots.court.id
            CourtCard(
                courtSlots = courtSlots,
                selectedSlot = selectedSlot,
                expanded = courtId in expandedCourts,
                onExpandToggle = { if (!expandedCourts.remove(courtId)) expandedCourts.add(courtId) },
                onSlotClick = onSlotClick
            )
        }
    }
}

/** Aparece con un fundido y subiendo un poco. */
@Composable
private fun EntranceAnimation(
    delayMillis: Int,
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Cuenta el valor de animate al entrar en la composición: al frame siguiente pasa a false y la entrada sigue
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, delayMillis = delayMillis, easing = FastOutSlowInEasing)
            )
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 24.dp.toPx()
        }
    ) {
        content()
    }
}

@Composable
private fun Filters(
    uiState: BookingUiState,
    pinned: Boolean,
    onDaySelect: (LocalDate) -> Unit,
    onDayPartSelect: (DayPart) -> Unit,
    onDurationSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dividerAlpha by animateFloatAsState(if (pinned) 1f else 0f, label = "filtersDivider")
    val dayPartLabels = DayPart.entries.associateWith { stringResource(it.label()) }
    val durationLabel = stringResource(R.string.booking_duration)
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Spacer(Modifier.height(16.dp))
        DaySelector(
            days = uiState.days,
            selectedDay = uiState.selectedDay,
            isLocked = uiState::isLocked,
            onDaySelect = onDaySelect
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            PillSwitch(
                options = DAY_PART_ORDER,
                selected = uiState.dayPart,
                onSelect = onDayPartSelect,
                label = { dayPartLabels.getValue(it) },
                modifier = Modifier.weight(1f)
            )
            PillSwitch(
                options = BOOKING_DURATIONS,
                selected = uiState.durationMinutes,
                onSelect = onDurationSelect,
                label = { minutes -> durationLabel.format(minutes) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.graphicsLayer { alpha = dividerAlpha }
        )
        Spacer(Modifier.height(5.dp))
    }
}

/** La tarde primero: es cuando más se juega. */
private val DAY_PART_ORDER = listOf(DayPart.Afternoon, DayPart.Morning)

@Composable
private fun DaySelector(
    days: List<LocalDate>,
    selectedDay: LocalDate,
    isLocked: (LocalDate) -> Boolean,
    onDaySelect: (LocalDate) -> Unit
) {
    val today = LocalDate.now(Syltek.ZONE)
    LazyRow(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days, key = { it.toEpochDay() }) { day ->
            DayChip(
                day = day,
                isToday = day == today,
                selected = day == selectedDay,
                locked = isLocked(day),
                onClick = { onDaySelect(day) }
            )
        }
    }
}

/** Los días aún cerrados (solo se pueden programar) van más apagados y con un reloj en la esquina. */
@Composable
private fun DayChip(day: LocalDate, isToday: Boolean, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = if (isToday) stringResource(R.string.booking_today) else day.dayOfWeek.shortName()
    val background = when {
        selected -> scheme.primary
        locked -> scheme.surfaceContainerHigh
        else -> scheme.surfaceVariant
    }
    val color = when {
        selected -> scheme.onPrimary
        locked -> scheme.onSurfaceVariant.copy(alpha = 0.7f)
        else -> scheme.onSurfaceVariant
    }
    val lockedDescription = stringResource(R.string.schedule_day_locked)
    Box(
        modifier = Modifier
            .width(64.dp)
            .background(background, RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                if (locked) stateDescription = lockedDescription
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = color)
        }
        if (locked) {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(12.dp)
            )
        }
    }
}

/**
 * Plegada solo enseña las horas que se pueden reservar (y las propias); al tocarla se despliega con todas, también las
 * no disponibles, en su orden.
 */
@Composable
private fun CourtCard(
    courtSlots: CourtSlots,
    selectedSlot: SelectedSlot?,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onSlotClick: (courtId: Int, start: Instant) -> Unit
) {
    val court = courtSlots.court
    val freeSlots = courtSlots.slots.count { it.state == SlotState.Free }
    val slots = if (expanded) courtSlots.slots else courtSlots.slots.filter { it.state != SlotState.Unavailable }
    val toggleLabel = stringResource(if (expanded) R.string.booking_court_collapse else R.string.booking_court_expand)
    // Las horas solo se animan justo después de desplegar o plegar: la lista vuelve a componer las tarjetas al hacer
    // scroll, y sin esto cada vez que una entraba en pantalla sus horas aparecían con fundido y deslizándose
    var animateSlots by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        delay(SLOT_ANIMATION_WINDOW_MILLIS)
        animateSlots = false
    }
    val onClick = {
        animateSlots = true
        onExpandToggle()
    }
    CourtCardSurface(clickModifier = Modifier.clickable(onClickLabel = toggleLabel, onClick = onClick)) {
        CourtHeader(court = court, freeSlots = freeSlots, expanded = expanded)
        SlotGrid(slots, animate = animateSlots) { slot, modifier ->
            SlotChip(
                slot = slot,
                selected = selectedSlot == SelectedSlot(court.id, slot.start),
                onClick = { onSlotClick(court.id, slot.start) },
                modifier = modifier
            )
        }
    }
}

/**
 * Cuadrícula de 3 columnas sin scroll propio (va dentro de la lista). Todas las horas en un solo contenedor y con su
 * hora como clave: al desplegar o plegar, las que se quedan se deslizan a su nuevo sitio y las que llegan aparecen con
 * un fundido.
 */
@Composable
private fun SlotGrid(slots: List<Slot>, animate: Boolean, chip: @Composable (Slot, Modifier) -> Unit) {
    BoxWithConstraints {
        val gap = 6.dp
        // En píxeles enteros y redondeando hacia abajo: con decimales, las 3 podían pasarse un píxel y la tercera
        // bajaba a la fila siguiente
        val chipWidth = with(LocalDensity.current) {
            ((constraints.maxWidth - gap.roundToPx() * (SLOT_COLUMNS - 1)) / SLOT_COLUMNS).toDp()
        }
        LookaheadScope {
            FlowRow(
                maxItemsInEachRow = SLOT_COLUMNS,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                slots.forEach { slot ->
                    key(slot.start) {
                        val alpha = remember { Animatable(if (animate) 0f else 1f) }
                        LaunchedEffect(Unit) { alpha.animateTo(1f) }
                        chip(
                            slot,
                            Modifier
                                .animateBounds(
                                    lookaheadScope = this@LookaheadScope,
                                    boundsTransform = { _, _ -> if (animate) slotBoundsSpec else snap() }
                                )
                                .width(chipWidth)
                                .graphicsLayer { this.alpha = alpha.value }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourtCardSurface(
    modifier: Modifier = Modifier,
    // Va dentro del recorte de la forma, para que la onda del toque no se salga de las esquinas
    clickModifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(clickModifier)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun CourtHeader(court: Court, freeSlots: Int, expanded: Boolean) {
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "courtArrow")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(
                if (court.covered) R.drawable.ic_court_covered else R.drawable.ic_court_uncovered
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Patrocinador junto al nombre, más pequeño y en azul; si no cabe se corta él, nunca el nombre
            Row {
                Text(
                    text = court.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline()
                )
                court.sponsor?.let { sponsor ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = sponsor,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .alignByBaseline()
                    )
                }
            }
            Text(
                text = stringResource(if (court.covered) R.string.booking_covered else R.string.booking_uncovered),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        if (freeSlots > 0) FreeSlotsTag(freeSlots)
        Spacer(Modifier.width(4.dp))
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = arrowRotation }
        )
    }
}

/** Etiqueta lima con los huecos libres, como la etiqueta "Disponible" del mock. */
@Composable
private fun FreeSlotsTag(count: Int) {
    val (background, content) = freeColors()
    Text(
        text = pluralStringResource(R.plurals.booking_free_slots, count, count),
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/** Lima de bola. En oscuro tertiaryContainer es un oliva apagado; tertiary mantiene el lima. */
@Composable
internal fun freeColors() = MaterialTheme.colorScheme.let { scheme ->
    if (isSystemInDarkTheme()) {
        scheme.tertiary to scheme.onTertiary
    } else {
        scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
}

private const val SLOT_COLUMNS = 3

/** Margen tras desplegar o plegar en el que las horas se animan; cubre el inicio de sus animaciones. */
private const val SLOT_ANIMATION_WINDOW_MILLIS = 600L

private val slotBoundsSpec =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold)
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun SlotChip(slot: Slot, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val transparent = scheme.surface.copy(alpha = 0f)
    // Las no disponibles van sin fondo, solo con la hora tachada, para que destaque lo libre
    val (background, content) = when {
        selected -> scheme.primary to scheme.onPrimary
        slot.state == SlotState.Free -> freeColors()
        slot.state == SlotState.Mine -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> transparent to scheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val time = slot.time.format(TIME_FORMAT)
    val description = stringResource(slot.state.descriptionRes(), time)

    Box(
        modifier = modifier
            .height(40.dp)
            .background(background, RoundedCornerShape(14.dp))
            .then(
                if (slot.state ==
                    SlotState.Free
                ) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            textDecoration = if (slot.state == SlotState.Unavailable) TextDecoration.LineThrough else null,
            textAlign = TextAlign.Center
        )
    }
}

/** Silueta de una tarjeta de pista mientras carga, con un brillo que la recorre. */
@Composable
private fun SkeletonCourtCard(modifier: Modifier = Modifier) {
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
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(x, 0f),
                end = Offset(x + size.width, 0f)
            ),
            cornerRadius = CornerRadius(12.dp.toPx())
        )
    }

    CourtCardSurface(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).background(base, CircleShape))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(96.dp).height(18.dp).then(placeholder))
        }
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(SLOT_COLUMNS) { Box(Modifier.weight(1f).height(40.dp).then(placeholder)) }
            }
        }
    }
}

@Composable
private fun EmptyState(dayPart: DayPart, durationMinutes: Int) {
    MessageState(
        message = stringResource(
            if (dayPart == DayPart.Morning) R.string.booking_empty_morning else R.string.booking_empty_afternoon,
            durationMinutes
        )
    )
}

@Composable
private fun ErrorState(error: BookingError, onRetry: () -> Unit) {
    MessageState(
        message = stringResource(
            if (error == BookingError.Network) R.string.booking_error_network else R.string.booking_error_unknown
        ),
        action = {
            Button(onClick = onRetry) { Text(stringResource(R.string.booking_retry)) }
        }
    )
}

@Composable
private fun MessageState(message: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

private val SPANISH = Locale.forLanguageTag("es-ES")

/** "Vie", "Sáb"…: la abreviatura del sistema puede traer punto final ("vie."). */
private fun DayOfWeek.shortName(): String =
    getDisplayName(TextStyle.SHORT, SPANISH).removeSuffix(".").replaceFirstChar { it.titlecase(SPANISH) }

private fun DayPart.label(): Int = when (this) {
    DayPart.Morning -> R.string.booking_morning
    DayPart.Afternoon -> R.string.booking_afternoon
}

private fun SlotState.descriptionRes(): Int = when (this) {
    SlotState.Free -> R.string.booking_slot_free
    SlotState.Unavailable -> R.string.booking_slot_unavailable
    SlotState.Mine -> R.string.booking_slot_mine
}

@Preview(showBackground = true)
@Composable
private fun BookingContentPreview() {
    val today = LocalDate.now(Syltek.ZONE)
    val court = Court(id = 1, name = "Pista 1", sponsor = "Solán de Cabras", covered = false, periods = emptyList())
    val slots = listOf(
        LocalTime.of(17, 0) to SlotState.Unavailable,
        LocalTime.of(17, 30) to SlotState.Free,
        LocalTime.of(18, 0) to SlotState.Free,
        LocalTime.of(18, 30) to SlotState.Mine
    ).map { (time, state) ->
        Slot(today.atTime(time).atZone(Syltek.ZONE).toInstant(), time, state, maxMinutes = 90)
    }
    PadelPratTheme {
        BookingContent(
            uiState = BookingUiState(
                days = (0L until 8L).map(today::plusDays),
                selectedDay = today,
                dayPart = DayPart.Afternoon,
                lastOpenDay = today.plusDays(7),
                content = BookingContent.Loaded(listOf(CourtSlots(court, slots)))
            ),
            onDaySelect = {},
            onDayPartSelect = {},
            onDurationSelect = {},
            onSlotClick = { _, _ -> },
            onScheduleTimeSelect = {},
            onScheduleCourtToggle = {},
            onScheduleSave = {},
            onRefresh = {},
            onRetry = {}
        )
    }
}
