package com.dgalan.padelprat.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dgalan.padelprat.R
import com.dgalan.padelprat.core.network.Syltek
import com.dgalan.padelprat.data.booking.Court
import com.dgalan.padelprat.data.scheduled.MAX_SCHEDULED_COURTS
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formulario para programar la reserva de un día aún cerrado, como elementos de la lista de Reservar. */
fun LazyListScope.scheduleItems(
    form: ScheduleForm,
    onTimeSelect: (LocalTime) -> Unit,
    onCourtToggle: (Int) -> Unit,
    onSave: () -> Unit
) {
    item(key = "schedule-info") { OpensInfo(form.opensAt, Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) }
    item(key = "schedule-times") {
        Section(title = stringResource(R.string.schedule_time_title)) {
            if (form.times.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_no_times),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            form.times.chunked(TIME_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { time ->
                        TimeChip(
                            time = time,
                            selected = time == form.selectedTime,
                            onClick = { onTimeSelect(time) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(TIME_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    item(key = "schedule-courts") {
        Section(
            title = pluralStringResource(R.plurals.schedule_courts_title, MAX_SCHEDULED_COURTS, MAX_SCHEDULED_COURTS)
        ) {
            form.courts.forEach { court ->
                CourtChoice(
                    court = court,
                    position = form.selectedCourts.indexOf(court.id).takeIf { it >= 0 }?.plus(1),
                    enabled = court.id in form.selectedCourts || form.selectedCourts.size < MAX_SCHEDULED_COURTS,
                    onClick = { onCourtToggle(court.id) }
                )
            }
        }
    }
    item(key = "schedule-save") {
        Button(
            onClick = onSave,
            enabled = form.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(48.dp)
        ) {
            Text(stringResource(R.string.schedule_save), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "Este día todavía no se puede reservar. Se abre el sábado 24 a las 9:00." */
@Composable
private fun OpensInfo(opensAt: Instant, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.schedule_opens_info, opensAt.openingText()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun TimeChip(time: LocalTime, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (selected) scheme.primary else scheme.surfaceVariant, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = time.format(TIME_FORMAT),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
        )
    }
}

/** Pista elegible; las elegidas llevan su número de preferencia (1, 2, 3). */
@Composable
private fun CourtChoice(court: Court, position: Int?, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val description = position?.let { stringResource(R.string.schedule_court_position, court.name, it) } ?: court.name
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (position != null) scheme.primaryContainer else scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, if (position != null) scheme.primary else scheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = position != null
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Icon(
                painter = painterResource(
                    if (court.covered) R.drawable.ic_court_covered else R.drawable.ic_court_uncovered
                ),
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Row(modifier = Modifier.weight(1f)) {
                Text(
                    text = court.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
                    modifier = Modifier.alignByBaseline()
                )
                court.sponsor?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
            // Círculo con el orden de preferencia, o vacío si no está elegida
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .then(
                        if (position != null) {
                            Modifier.background(scheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.5.dp, scheme.outline.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (position != null) {
                    Text(position.toString(), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
                }
            }
        }
    }
}

private const val TIME_COLUMNS = 4
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val SPANISH = Locale.forLanguageTag("es-ES")
private val OPENING_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'a las' H:mm", SPANISH)

/** "el sábado 24 a las 9:00". */
internal fun Instant.openingText(): String = atZone(Syltek.ZONE).format(OPENING_FORMAT)
