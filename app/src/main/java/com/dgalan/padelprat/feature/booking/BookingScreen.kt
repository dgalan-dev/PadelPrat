package com.dgalan.padelprat.feature.booking

import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dgalan.padelprat.R
import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.booking.BOOKING_DURATIONS
import com.dgalan.padelprat.data.booking.Court
import com.dgalan.padelprat.data.booking.CourtAtTime
import com.dgalan.padelprat.data.booking.CourtSlots
import com.dgalan.padelprat.data.booking.DayPart
import com.dgalan.padelprat.data.booking.MyBooking
import com.dgalan.padelprat.data.booking.Slot
import com.dgalan.padelprat.data.booking.SlotCalculator
import com.dgalan.padelprat.data.booking.SlotState
import com.dgalan.padelprat.data.booking.TimeSlots
import com.dgalan.padelprat.ui.components.CourtName
import com.dgalan.padelprat.ui.components.MessageState
import com.dgalan.padelprat.ui.components.PadelCard
import com.dgalan.padelprat.ui.components.PillSwitch
import com.dgalan.padelprat.ui.components.ShimmerBox
import com.dgalan.padelprat.ui.components.SwipeableSnackbarHost
import com.dgalan.padelprat.ui.components.Tag
import com.dgalan.padelprat.ui.format.hourText
import com.dgalan.padelprat.ui.format.openingText
import com.dgalan.padelprat.ui.format.shortName
import com.dgalan.padelprat.ui.theme.PadelPratTheme
import com.dgalan.padelprat.ui.theme.padelColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

/** [onReservationsClick] lleva a la pestaña Mis reservas (desde el aviso de programada o el de límite). */
@Composable
fun BookingScreen(
    onReservationsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = (uiState.message as? BookingMessage.Scheduled)?.let {
        stringResource(R.string.schedule_saved, it.opensAt.openingText())
    }
    val messageAction = stringResource(R.string.booking_see_reservations)
    val currentOnReservationsClick by rememberUpdatedState(onReservationsClick)
    LaunchedEffect(message) {
        if (message != null) {
            val result = snackbarHostState.showSnackbar(message, actionLabel = messageAction)
            viewModel.onMessageShow()
            if (result == SnackbarResult.ActionPerformed) currentOnReservationsClick()
        }
    }

    Box(modifier = modifier) {
        BookingContent(
            uiState = uiState,
            onDaySelect = viewModel::onDaySelect,
            onDayPartSelect = viewModel::onDayPartSelect,
            onDurationSelect = viewModel::onDurationSelect,
            onViewSelect = viewModel::onViewSelect,
            onSlotClick = viewModel::onSlotClick,
            onScheduleTimeSelect = viewModel::onScheduleTimeSelect,
            onScheduleCourtToggle = viewModel::onScheduleCourtToggle,
            onScheduleSave = viewModel::onScheduleSave,
            onRefresh = viewModel::onRefresh,
            onRetry = viewModel::onRetry,
            onReservationsClick = onReservationsClick
        )
        SwipeableSnackbarHost(
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
 * Una sola lista: el título se va al hacer scroll y los filtros se quedan fijos arriba (cabecera fija), que cambia de
 * tono cuando ya hay contenido pasando por detrás, como la barra superior de Material 3 (también por debajo de la
 * barra de estado). Tirar hacia abajo recarga las pistas, así que el indicador sale justo debajo de los
 * filtros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingContent(
    uiState: BookingUiState,
    onDaySelect: (LocalDate) -> Unit,
    onDayPartSelect: (DayPart) -> Unit,
    onDurationSelect: (Int) -> Unit,
    onViewSelect: (BookingView) -> Unit,
    onSlotClick: (courtId: Int, start: Instant) -> Unit,
    onScheduleTimeSelect: (LocalTime) -> Unit,
    onScheduleCourtToggle: (Int) -> Unit,
    onScheduleSave: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onReservationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Cada día (y cada vista) empieza arriba: el estado de scroll es nuevo en el mismo instante en que cambia el
    // contenido. Si se arrastrara la posición anterior, la lista saltaba al cargar el nuevo (con solo 3 siluetas no
    // cabía).
    val listState = remember(uiState.selectedDay, uiState.view) { LazyListState() }
    val filtersPinned by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val headerColor by animateColorAsState(
        targetValue = if (filtersPinned) MaterialTheme.padelColors.pinnedHeader else MaterialTheme.colorScheme.surface,
        label = "headerColor"
    )

    // Entrada de las tarjetas: solo las que se componen en el primer frame tras cargar, que son las que se ven. Las que
    // llegan después con el scroll aparecen sin más. Se hace una vez por día y filtros (al volver a uno ya visto no se
    // repite) y otra vez tras cada recarga (siluetas).
    val entranceKey = EntranceKey(uiState.selectedDay, uiState.dayPart, uiState.durationMinutes, uiState.view)
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

    Column(modifier = modifier.fillMaxSize()) {
        // Fondo de la barra de estado, del mismo color que la cabecera
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .drawBehind { drawRect(headerColor) }
        )
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
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "title") {
                    Title(
                        firstName = uiState.firstName,
                        // Los días cerrados enseñan el formulario de programar, que no tiene vistas
                        view = uiState.view.takeUnless { uiState.isLocked(uiState.selectedDay) },
                        onViewSelect = onViewSelect,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    )
                }
                stickyHeader(key = "filters") {
                    Filters(
                        uiState = uiState,
                        background = { headerColor },
                        onDaySelect = onDaySelect,
                        onDayPartSelect = onDayPartSelect,
                        onDurationSelect = onDurationSelect,
                        modifier = Modifier.onGloballyPositioned { filtersBottom = it.boundsInParent().bottom.toInt() }
                    )
                }
                if (uiState.reservationLimitReached) {
                    item(key = "limit") {
                        LimitBanner(onReservationsClick, Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
                    }
                }
                when (val content = uiState.content) {
                    BookingContent.Loading -> if (
                        uiState.view == BookingView.Times && !uiState.isLocked(uiState.selectedDay)
                    ) {
                        item(key = "skeleton-times") {
                            SkeletonTimeList(Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
                        }
                    } else {
                        items(SKELETON_CARDS, key = { "skeleton$it" }) {
                            SkeletonCourtCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
                        }
                    }

                    is BookingContent.Error -> item(key = "error") { ErrorState(content.error, onRetry) }

                    is BookingContent.Loaded -> if (content.courts.isEmpty()) {
                        item(key = "empty") { EmptyState(uiState.dayPart, uiState.durationMinutes) }
                    } else if (uiState.view == BookingView.Times) {
                        timeItems(
                            times = content.times,
                            selectedSlot = uiState.sheet?.selectedSlot,
                            entranceOpen = entranceOpen,
                            onSlotClick = onSlotClick,
                            onMineClick = onReservationsClick
                        )
                    } else {
                        courtItems(
                            courts = content.courts,
                            selectedSlot = uiState.sheet?.selectedSlot,
                            entranceOpen = entranceOpen,
                            expandedCourts = expandedCourts,
                            onSlotClick = onSlotClick,
                            onMineClick = onReservationsClick
                        )
                    }

                    is BookingContent.Schedule ->
                        scheduleItems(content.form, onScheduleTimeSelect, onScheduleCourtToggle, onScheduleSave)
                }
            }
        }
    }
}

private data class EntranceKey(
    val day: LocalDate,
    val dayPart: DayPart,
    val durationMinutes: Int,
    val view: BookingView
)

/** "Hola, Marc" y el título, con el selector de vista (por pista o por hora) a la derecha si hay [view]. */
@Composable
private fun Title(
    firstName: String?,
    view: BookingView?,
    onViewSelect: (BookingView) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewLabels = BookingView.entries.associateWith { stringResource(it.label()) }
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            // Como en el mock; hasta que llegan los datos del perfil, solo el título
            firstName?.let {
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
        if (view != null) {
            Spacer(Modifier.width(12.dp))
            PillSwitch(
                options = BookingView.entries,
                selected = view,
                onSelect = onViewSelect,
                label = { viewLabels.getValue(it) },
                icon = { it.icon() },
                modifier = Modifier.width(96.dp)
            )
        }
    }
}

private const val SKELETON_CARDS = 3

/** Aviso de que se ha llegado al máximo de reservas del club: se puede mirar, pero no reservar otra. */
@Composable
private fun LimitBanner(onReservationsClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text = pluralStringResource(
                    R.plurals.reservation_limit_reached,
                    Syltek.MAX_ACTIVE_RESERVATIONS,
                    Syltek.MAX_ACTIVE_RESERVATIONS
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            TextButton(onClick = onReservationsClick, modifier = Modifier.align(Alignment.End)) {
                Text(
                    text = stringResource(R.string.booking_limit_see_reservations),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

private fun LazyListScope.courtItems(
    courts: List<CourtSlots>,
    selectedSlot: SelectedSlot?,
    entranceOpen: Boolean,
    expandedCourts: MutableSet<Int>,
    onSlotClick: (courtId: Int, start: Instant) -> Unit,
    onMineClick: () -> Unit
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
                onSlotClick = onSlotClick,
                onMineClick = onMineClick
            )
        }
    }
}

/**
 * Vista por hora: una sola tarjeta con una fila por hora de inicio, para ver la franja entera de un vistazo. Va en un
 * único elemento de la lista porque son pocas filas (una franja tiene como mucho unas 20 horas).
 */
private fun LazyListScope.timeItems(
    times: List<TimeSlots>,
    selectedSlot: SelectedSlot?,
    entranceOpen: Boolean,
    onSlotClick: (courtId: Int, start: Instant) -> Unit,
    onMineClick: () -> Unit
) {
    if (times.isEmpty()) return
    item(key = "times") {
        EntranceAnimation(
            delayMillis = 0,
            animate = entranceOpen,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
        ) {
            PadelCard {
                Column {
                    times.forEachIndexed { index, timeSlots ->
                        key(timeSlots.start) {
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            TimeRow(
                                timeSlots = timeSlots,
                                selectedSlot = selectedSlot,
                                onSlotClick = onSlotClick,
                                onMineClick = onMineClick
                            )
                        }
                    }
                }
            }
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
    background: () -> Color,
    onDaySelect: (LocalDate) -> Unit,
    onDayPartSelect: (DayPart) -> Unit,
    onDurationSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayPartLabels = DayPart.entries.associateWith { stringResource(it.label()) }
    val durationLabel = stringResource(R.string.booking_duration)
    // El color se lee al dibujar: la animación de la cabecera solo repinta
    Column(modifier = modifier.drawBehind { drawRect(background()) }) {
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
            .background(background, MaterialTheme.shapes.medium)
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
 * Plegada solo enseña las horas que se pueden reservar y las reservas propias; al tocarla se despliega con todas, también
 * las no disponibles, en su orden.
 */
@Composable
private fun CourtCard(
    courtSlots: CourtSlots,
    selectedSlot: SelectedSlot?,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onSlotClick: (courtId: Int, start: Instant) -> Unit,
    onMineClick: () -> Unit
) {
    val court = courtSlots.court
    val freeSlots = courtSlots.slots.count { it.state == SlotState.Free }
    val slots = if (expanded) courtSlots.slots else courtSlots.slots.filter { it.state != SlotState.Unavailable }
    val items = remember(slots, courtSlots.mine) {
        (slots.map(GridItem::Hour) + courtSlots.mine.map(GridItem::Mine)).sortedBy { it.start }
    }
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
        SlotGrid(items, animate = animateSlots) { item, modifier ->
            when (item) {
                is GridItem.Hour -> SlotChip(
                    slot = item.slot,
                    selected = selectedSlot == SelectedSlot(court.id, item.slot.start),
                    onClick = { onSlotClick(court.id, item.slot.start) },
                    modifier = modifier
                )

                is GridItem.Mine -> MineChip(
                    item.booking,
                    showCourt = false,
                    onClick = onMineClick,
                    modifier = modifier
                )
            }
        }
    }
}

/** Lo que va en la cuadrícula de una pista: una hora, o una reserva propia, que ocupa una fila entera. */
private sealed interface GridItem {
    val start: Instant

    data class Hour(val slot: Slot) : GridItem {
        override val start: Instant get() = slot.start
    }

    data class Mine(val booking: MyBooking) : GridItem {
        override val start: Instant get() = booking.start
    }
}

/**
 * Cuadrícula de 3 columnas sin scroll propio (va dentro de la lista). Todas las horas en un solo contenedor y con su
 * hora como clave: al desplegar o plegar, las que se quedan se deslizan a su nuevo sitio y las que llegan aparecen con
 * un fundido.
 */
@Composable
private fun SlotGrid(items: List<GridItem>, animate: Boolean, chip: @Composable (GridItem, Modifier) -> Unit) {
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
                items.forEach { item ->
                    key(item.start) {
                        val alpha = remember { Animatable(if (animate) 0f else 1f) }
                        LaunchedEffect(Unit) { alpha.animateTo(1f) }
                        chip(
                            item,
                            Modifier
                                .animateBounds(
                                    lookaheadScope = this@LookaheadScope,
                                    boundsTransform = { _, _ -> if (animate) slotBoundsSpec else snap() }
                                )
                                .then(if (item is GridItem.Mine) Modifier.fillMaxWidth() else Modifier.width(chipWidth))
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
    PadelCard(modifier = modifier.clip(MaterialTheme.shapes.large).then(clickModifier)) {
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
        CourtIcon(court, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            CourtName(name = court.name, sponsor = court.sponsor, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(if (court.covered) R.string.booking_covered else R.string.booking_uncovered),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        if (freeSlots > 0) FreeTag(pluralStringResource(R.plurals.booking_free_slots, freeSlots, freeSlots))
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

/** Etiqueta en lima intenso con los huecos libres, como la etiqueta "Disponible" del mock. */
@Composable
private fun FreeTag(text: String) {
    Tag(
        text = text,
        containerColor = MaterialTheme.padelColors.accent,
        contentColor = MaterialTheme.padelColors.onAccent
    )
}

@Composable
private fun CourtIcon(court: Court, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(if (court.covered) R.drawable.ic_court_covered else R.drawable.ic_court_uncovered),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

private const val SLOT_COLUMNS = 3

/** Alto de los chips de la parrilla: la zona táctil mínima de Material. */
private val CHIP_HEIGHT = 48.dp

/** Margen tras desplegar o plegar en el que las horas se animan; cubre el inicio de sus animaciones. */
private const val SLOT_ANIMATION_WINDOW_MILLIS = 600L

private val slotBoundsSpec =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold)

/** Fondo y texto de un hueco: el elegido en azul y los libres en lima. */
@Composable
private fun slotColors(state: SlotState, selected: Boolean): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when {
        selected -> scheme.primary to scheme.onPrimary

        state == SlotState.Free -> MaterialTheme.padelColors.available to MaterialTheme.padelColors.onAvailable

        // Las no disponibles van sin fondo, solo con la hora tachada, para que destaque lo libre
        else -> Color.Transparent to scheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
}

/** Chip de la parrilla; solo los libres se pueden tocar. [description] es lo que lee TalkBack. */
@Composable
private fun GridChip(
    state: SlotState,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Color) -> Unit
) {
    val (background, contentColor) = slotColors(state, selected)
    Box(
        modifier = modifier
            .height(CHIP_HEIGHT)
            .background(background, MaterialTheme.shapes.small)
            .then(if (state == SlotState.Free) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        content(contentColor)
    }
}

@Composable
private fun SlotChip(slot: Slot, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val time = slot.time.hourText()
    GridChip(
        state = slot.state,
        selected = selected,
        description = stringResource(slot.state.descriptionRes(), time),
        onClick = onClick,
        modifier = modifier
    ) { color ->
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            textDecoration = if (slot.state == SlotState.Unavailable) TextDecoration.LineThrough else null,
            textAlign = TextAlign.Center
        )
    }
}

/** Fila de la vista por hora: la hora con las pistas libres a la izquierda, y sus chips y las reservas propias. */
@Composable
private fun TimeRow(
    timeSlots: TimeSlots,
    selectedSlot: SelectedSlot?,
    onSlotClick: (courtId: Int, start: Instant) -> Unit,
    onMineClick: () -> Unit
) {
    val free = timeSlots.courts.count { it.state == SlotState.Free }
    TimeRowLayout(
        time = {
            Text(
                text = timeSlots.time.hourText(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (free > 0) {
                Text(
                    text = pluralStringResource(R.plurals.booking_free_courts, free, free),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { chipWidth ->
        timeSlots.mine.forEach { booking ->
            MineChip(booking, showCourt = true, onClick = onMineClick, modifier = Modifier.fillMaxWidth())
        }
        timeSlots.courts.forEach { courtAtTime ->
            CourtChip(
                courtAtTime = courtAtTime,
                selected = selectedSlot == SelectedSlot(courtAtTime.court.id, timeSlots.start),
                onClick = { onSlotClick(courtAtTime.court.id, timeSlots.start) },
                modifier = Modifier.width(chipWidth)
            )
        }
    }
}

/**
 * Estructura de una fila de la vista por hora, compartida con su silueta para que al cargar no salte nada: la columna
 * de la hora a la izquierda y los chips en una cuadrícula. [chips] recibe el ancho de cada chip.
 */
@Composable
private fun TimeRowLayout(time: @Composable () -> Unit, chips: @Composable (chipWidth: Dp) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.width(56.dp).padding(top = 2.dp)) {
            time()
        }
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val gap = 6.dp
            // Tantas columnas como quepan, todas del mismo ancho y en píxeles enteros, para que los chips queden
            // alineados de una hora a otra y ninguno salte de fila por un píxel
            val chipWidth = with(LocalDensity.current) {
                val gapPx = gap.roundToPx()
                val columns = ((constraints.maxWidth + gapPx) / (COURT_CHIP_MIN_WIDTH.roundToPx() + gapPx))
                    .coerceAtLeast(1)
                ((constraints.maxWidth - gapPx * (columns - 1)) / columns).toDp()
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                chips(chipWidth)
            }
        }
    }
}

/** Ancho mínimo de los chips de la vista por hora: los 48 dp de zona táctil que pide Material (5 por fila en un móvil). */
private val COURT_CHIP_MIN_WIDTH = 48.dp

/** Pista libre en la vista por hora: solo su número ("3") y el icono de cubierta; TalkBack lee "Pista 3, libre". */
@Composable
private fun CourtChip(courtAtTime: CourtAtTime, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val court = courtAtTime.court
    GridChip(
        state = courtAtTime.state,
        selected = selected,
        description = stringResource(courtAtTime.state.descriptionRes(), court.name),
        onClick = onClick,
        modifier = modifier
    ) { color ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            CourtIcon(court, tint = color, size = 14.dp)
            Spacer(Modifier.width(2.dp))
            Text(
                text = court.number(),
                style = MaterialTheme.typography.labelLarge,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** "3" de "Pista 3"; si el nombre no lleva número, el nombre entero. */
private fun Court.number(): String = Regex("""\d+""").find(name)?.value ?: name

@Composable
private fun MineChip(booking: MyBooking, showCourt: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val start = booking.time.hourText()
    val end = booking.endTime.hourText()
    val text = if (showCourt) {
        stringResource(R.string.booking_mine_court, booking.court.name, start, end)
    } else {
        stringResource(R.string.booking_mine, start, end)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(CHIP_HEIGHT)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, scheme.primary, MaterialTheme.shapes.small)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.booking_limit_see_reservations),
                onClick = onClick
            )
            .padding(horizontal = 12.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Silueta de la vista por hora mientras carga: la tarjeta con unas filas de distinto número de pistas. */
@Composable
private fun SkeletonTimeList(modifier: Modifier = Modifier) {
    PadelCard(modifier = modifier) {
        Column {
            SKELETON_TIME_CHIPS.forEachIndexed { index, chipCount ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TimeRowLayout(
                    time = {
                        ShimmerBox(Modifier.width(44.dp).height(18.dp), cornerRadius = 9.dp)
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(Modifier.width(36.dp).height(12.dp), cornerRadius = 6.dp)
                    }
                ) { chipWidth ->
                    repeat(chipCount) {
                        ShimmerBox(Modifier.width(chipWidth).height(CHIP_HEIGHT), cornerRadius = 12.dp)
                    }
                }
            }
        }
    }
}

/** Pistas de cada fila de la silueta de la vista por hora. */
private val SKELETON_TIME_CHIPS = listOf(4, 2, 6, 3, 5)

/** Silueta de una tarjeta de pista mientras carga. */
@Composable
private fun SkeletonCourtCard(modifier: Modifier = Modifier) {
    CourtCardSurface(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(Modifier.size(20.dp), cornerRadius = 10.dp)
            Spacer(Modifier.width(10.dp))
            ShimmerBox(Modifier.width(96.dp).height(18.dp), cornerRadius = 12.dp)
        }
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(SLOT_COLUMNS) { ShimmerBox(Modifier.weight(1f).height(CHIP_HEIGHT), cornerRadius = 12.dp) }
            }
        }
    }
}

@Composable
private fun EmptyState(dayPart: DayPart, durationMinutes: Int) {
    MessageState(
        icon = R.drawable.ic_ball,
        title = stringResource(R.string.booking_empty_title),
        message = stringResource(
            if (dayPart == DayPart.Morning) R.string.booking_empty_morning else R.string.booking_empty_afternoon,
            durationMinutes
        )
    )
}

@Composable
private fun ErrorState(error: BookingError, onRetry: () -> Unit) {
    val (title, text) = when (error) {
        BookingError.Network -> R.string.error_network_title to R.string.error_network_text
        BookingError.Unknown -> R.string.booking_error_unknown to R.string.error_unknown_text
        BookingError.WebChanged -> R.string.error_web_changed_title to R.string.error_web_changed_text
    }
    MessageState(
        icon = R.drawable.ic_error,
        title = stringResource(title),
        message = stringResource(text),
        action = {
            Button(onClick = onRetry) { Text(stringResource(R.string.booking_retry)) }
        }
    )
}

private fun DayPart.label(): Int = when (this) {
    DayPart.Morning -> R.string.booking_morning
    DayPart.Afternoon -> R.string.booking_afternoon
}

private fun BookingView.label(): Int = when (this) {
    BookingView.Courts -> R.string.booking_view_courts
    BookingView.Times -> R.string.booking_view_times
}

private fun BookingView.icon(): Int = when (this) {
    BookingView.Courts -> R.drawable.ic_court_covered
    BookingView.Times -> R.drawable.ic_schedule
}

/** Sirve para la hora ("18:30, libre") y para la pista en la vista por hora ("Pista 3, libre"). */
private fun SlotState.descriptionRes(): Int = when (this) {
    SlotState.Free -> R.string.booking_slot_free
    SlotState.Unavailable -> R.string.booking_slot_unavailable
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
        LocalTime.of(20, 0) to SlotState.Free
    ).map { (time, state) ->
        Slot(today.atTime(time).atZone(Syltek.ZONE).toInstant(), time, state, maxMinutes = 90)
    }
    val mine =
        MyBooking(
            court,
            today.atTime(18, 30).atZone(Syltek.ZONE).toInstant(),
            LocalTime.of(18, 30),
            LocalTime.of(20, 0)
        )
    PadelPratTheme {
        BookingContent(
            uiState = BookingUiState(
                days = (0L until 8L).map(today::plusDays),
                selectedDay = today,
                dayPart = DayPart.Afternoon,
                lastOpenDay = today.plusDays(7),
                content = listOf(CourtSlots(court, slots, listOf(mine))).let {
                    BookingContent.Loaded(it, SlotCalculator.byTime(it))
                }
            ),
            onDaySelect = {},
            onDayPartSelect = {},
            onDurationSelect = {},
            onViewSelect = {},
            onSlotClick = { _, _ -> },
            onScheduleTimeSelect = {},
            onScheduleCourtToggle = {},
            onScheduleSave = {},
            onRefresh = {},
            onRetry = {},
            onReservationsClick = {}
        )
    }
}
