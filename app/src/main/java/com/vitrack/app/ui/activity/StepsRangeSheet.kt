package com.vitrack.app.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitrack.app.R
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.theme.NumberStyles
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun Long.utcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Picks the Steps card's days: last 7, last 30, or any range up to today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepsRangeSheet(
    current: StepsRange,
    today: LocalDate,
    onApply: (StepsRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val steps = VitrackTheme.metrics.steps
    val (initialStart, initialEnd) = current.bounds(today)
    val todayMillis = today.utcMillis()
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart.utcMillis(),
        initialSelectedEndDateMillis = initialEnd.utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    val start = picker.selectedStartDateMillis?.utcDate()
    val end = picker.selectedEndDateMillis?.utcDate()
    val draft: StepsRange? = when {
        start == null -> null
        else -> rangeFor(start, end ?: start, today)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl)) {
            Text(
                stringResource(R.string.activity_range_title),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = steps.text,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(
                    when {
                        start == null -> "…"
                        end == null -> "${Format.dayMonth(start)} – …"
                        start == end -> Format.dayMonth(start)
                        else -> "${Format.dayMonth(start)} – ${Format.dayMonth(end)}"
                    },
                    style = NumberStyles.Large,
                )
                Text(
                    if (start != null && end != null) {
                        stringResource(R.string.activity_range_days, ChronoUnit.DAYS.between(start, end).toInt() + 1)
                    } else {
                        stringResource(R.string.activity_range_pick_end)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PresetChip(stringResource(R.string.activity_range_7), draft == StepsRange.Last7Days) {
                    val (s, e) = StepsRange.Last7Days.bounds(today)
                    picker.setSelection(s.utcMillis(), e.utcMillis())
                }
                PresetChip(stringResource(R.string.activity_range_30), draft == StepsRange.Last30Days) {
                    val (s, e) = StepsRange.Last30Days.bounds(today)
                    picker.setSelection(s.utcMillis(), e.utcMillis())
                }
                PresetChip(stringResource(R.string.activity_range_custom), draft == null || draft is StepsRange.Custom) {
                    // Start a fresh custom range: the next two taps pick it.
                    picker.setSelection(null, null)
                }
            }
            DateRangePicker(
                state = picker,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    selectedDayContainerColor = steps.strong,
                    selectedDayContentColor = steps.onStrong,
                    dayInSelectionRangeContainerColor = steps.container,
                    dayInSelectionRangeContentColor = MaterialTheme.colorScheme.onSurface,
                    todayDateBorderColor = steps.strong,
                    todayContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.fillMaxWidth().height(380.dp).padding(top = Spacing.s),
            )
            Text(
                stringResource(R.string.activity_range_note, Format.dayMonth(today)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
            Row(Modifier.fillMaxWidth().padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) { Text(stringResource(R.string.weighin_cancel), style = MaterialTheme.typography.titleMedium) }
                Button(
                    onClick = { draft?.let(onApply) },
                    enabled = draft != null,
                    modifier = Modifier.weight(2f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = steps.strong, contentColor = steps.onStrong),
                ) {
                    Text(stringResource(R.string.activity_range_apply), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                }
            }
        }
    }
}

/** Maps a picked range back to a preset when it matches one. */
internal fun rangeFor(start: LocalDate, end: LocalDate, today: LocalDate): StepsRange = when {
    end == today && start == today.minusDays(6) -> StepsRange.Last7Days
    end == today && start == today.minusDays(29) -> StepsRange.Last30Days
    else -> StepsRange.Custom(start, end)
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val steps = VitrackTheme.metrics.steps
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) steps.strong else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (selected) steps.onStrong else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.height(36.dp).semantics { this.selected = selected; role = Role.RadioButton },
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium))
        }
    }
}
