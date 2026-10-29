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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dgalan.padelprat.R
import com.dgalan.padelprat.core.system.BatteryOptimization
import com.dgalan.padelprat.data.booking.Court
import com.dgalan.padelprat.data.scheduled.MAX_SCHEDULED_COURTS
import com.dgalan.padelprat.ui.components.CourtName
import com.dgalan.padelprat.ui.format.hourText
import com.dgalan.padelprat.ui.format.openingText
import com.dgalan.padelprat.ui.theme.padelColors
import java.time.Instant
import java.time.LocalTime

/** Formulario para programar la reserva de un día aún cerrado, como elementos de la lista de Reservar. */
fun LazyListScope.scheduleItems(
    form: ScheduleForm,
    onTimeSelect: (LocalTime) -> Unit,
    onCourtToggle: (Int) -> Unit,
    onSave: () -> Unit
) {
    item(key = "schedule-info") { OpensInfo(form.opensAt, Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) }
    item(key = "schedule-battery") { BatteryWarning(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) }
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
                            alternative = time == form.alternativeTime,
                            onClick = { onTimeSelect(time) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(TIME_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            form.selectedTime?.let { selected ->
                Text(
                    text = form.alternativeTime?.let {
                        stringResource(R.string.schedule_alternative_set, selected.hourText(), it.hourText())
                    } ?: stringResource(R.string.schedule_alternative_hint, selected.hourText()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        shape = MaterialTheme.shapes.medium,
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

/**
 * Aviso si la app tiene restricciones de batería, con un botón para quitarlas. Se comprueba al volver a la app, así
 * que desaparece en cuanto se quitan.
 */
@Composable
private fun BatteryWarning(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var restricted by remember { mutableStateOf(BatteryOptimization.isRestricted(context)) }
    LifecycleResumeEffect(Unit) {
        restricted = BatteryOptimization.isRestricted(context)
        onPauseOrDispose {}
    }
    if (!restricted) return

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text = stringResource(R.string.schedule_battery_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(
                onClick = { context.startActivity(BatteryOptimization.requestIntent(context)) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.schedule_battery_fix), color = MaterialTheme.colorScheme.onErrorContainer)
            }
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

/** Hora elegible: la principal va rellena y la alternativa, con borde. */
@Composable
private fun TimeChip(
    time: LocalTime,
    selected: Boolean,
    alternative: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val description = if (alternative) {
        stringResource(R.string.schedule_time_alternative, time.hourText())
    } else {
        time.hourText()
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .background(if (selected) scheme.primary else scheme.surfaceVariant, shape)
            .then(if (alternative) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                this.selected = selected || alternative
                contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = time.hourText(),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                selected -> scheme.onPrimary
                alternative -> scheme.primary
                else -> scheme.onSurfaceVariant
            }
        )
    }
}

/** Pista elegible; las elegidas llevan su número de preferencia (1, 2, 3). */
@Composable
private fun CourtChoice(court: Court, position: Int?, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val description = position?.let { stringResource(R.string.schedule_court_position, court.name, it) } ?: court.name
    val padelColors = MaterialTheme.padelColors
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (position != null) scheme.primaryContainer else padelColors.card,
        // En oscuro las no elegidas van sin borde, como las tarjetas
        border = if (position != null) {
            BorderStroke(1.dp, scheme.primary)
        } else {
            padelColors.cardBorder?.let { BorderStroke(1.dp, it) }
        },
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
            CourtName(
                name = court.name,
                sponsor = court.sponsor,
                alpha = if (enabled) 1f else 0.4f,
                modifier = Modifier.weight(1f)
            )
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
