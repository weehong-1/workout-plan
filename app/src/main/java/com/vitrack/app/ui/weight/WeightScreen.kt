package com.vitrack.app.ui.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitrack.app.R
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.ui.common.AccountAvatar
import com.vitrack.app.ui.common.ChartPeriod
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.common.MetricCard
import com.vitrack.app.ui.common.PeriodSelector
import com.vitrack.app.ui.common.ValueWithUnit
import com.vitrack.app.ui.theme.CardShapes
import com.vitrack.app.ui.theme.NumberStyles
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme
import com.vitrack.app.ui.weight.editor.WeighInEditorViewModel
import com.vitrack.app.ui.weight.editor.WeighInSheet
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun WeightRoute(
    modifier: Modifier = Modifier,
    viewModel: WeightViewModel = hiltViewModel(),
    editorViewModel: WeighInEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()
    WeightScreen(
        uiState = uiState,
        onSelectPeriod = viewModel::selectPeriod,
        onLogWeight = editorViewModel::openNew,
        onOpenEntry = editorViewModel::openExisting,
        modifier = modifier,
    )
    editorState?.let { WeighInSheet(state = it, viewModel = editorViewModel) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeightScreen(
    uiState: WeightUiState,
    onSelectPeriod: (ChartPeriod) -> Unit,
    onLogWeight: () -> Unit,
    onOpenEntry: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().statusBarsPadding()) {
        when (uiState) {
            WeightUiState.Loading -> ContainedLoadingIndicator(Modifier.align(Alignment.Center))
            is WeightUiState.Empty -> LazyColumn(contentPadding = listPadding) {
                item { Header(uiState.displayName) }
                item { EmptyCard() }
            }
            is WeightUiState.Ready -> LazyColumn(
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                item { Header(uiState.displayName) }
                item { LatestCard(uiState, onSelectPeriod) }
                item {
                    Text(
                        stringResource(R.string.weight_history),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.m).semantics { heading() },
                    )
                }
                item { HistoryList(uiState.history, uiState.unit, onOpenEntry) }
            }
        }
        if (uiState !is WeightUiState.Loading) {
            ExtendedFloatingActionButton(
                onClick = onLogWeight,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.weight_log)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            )
        }
    }
}

// Bottom padding keeps the last history row clear of the floating button.
private val listPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 96.dp)

@Composable
private fun Header(displayName: String?) {
    Column {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.s), horizontalArrangement = Arrangement.End) {
            AccountAvatar(displayName)
        }
        Text(
            stringResource(R.string.weight_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.s).semantics { heading() },
        )
    }
}

@Composable
private fun EmptyCard() {
    MetricCard(shape = CardShapes.Hero, contentPadding = Spacing.xxl, modifier = Modifier.fillMaxWidth()) {
        Icon(
            painterResource(R.drawable.ic_nav_weight),
            contentDescription = null,
            tint = VitrackTheme.metrics.weight.text,
            modifier = Modifier.size(32.dp),
        )
        Text(
            stringResource(R.string.weight_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.m),
        )
        Text(
            stringResource(R.string.weight_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun LatestCard(state: WeightUiState.Ready, onSelectPeriod: (ChartPeriod) -> Unit) {
    val metric = VitrackTheme.metrics.weight
    val unitLabel = Format.unitLabel(state.unit)
    MetricCard(shape = CardShapes.Hero, contentPadding = Spacing.xl) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.weight_latest, loggedLabel(state.latest, state.today)),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = metric.text,
                )
                ValueWithUnit(
                    value = Format.weight(state.latest.weightKg, state.unit),
                    unit = unitLabel,
                    valueStyle = NumberStyles.Hero.copy(fontSize = 45.sp, lineHeight = 52.sp),
                    unitStyle = MaterialTheme.typography.titleLarge,
                )
            }
            state.weeklyChangeKg?.let { WeeklyChangeChip(it, state.unit, Modifier.padding(top = Spacing.xs)) }
        }
        PeriodSelector(
            selected = state.period,
            onSelect = onSelectPeriod,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
        WeightTrendChart(
            points = state.chart,
            unit = state.unit,
            start = state.chartStart,
            end = state.today,
            color = metric.graphic,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

@Composable
private fun WeeklyChangeChip(changeKg: Double, unit: WeightUnit, modifier: Modifier = Modifier) {
    val metric = VitrackTheme.metrics.weight
    val amount = Format.weight(abs(changeKg), unit)
    val unitLabel = Format.unitLabel(unit)
    // Under 0.05 in the shown unit rounds to 0.0: treat as no change.
    val steady = amount == Format.oneDecimal(0.0)
    val description = when {
        steady -> stringResource(R.string.weight_no_change)
        changeKg < 0 -> stringResource(R.string.weight_change_down, amount, unitLabel)
        else -> stringResource(R.string.weight_change_up, amount, unitLabel)
    }
    Surface(
        color = metric.container,
        contentColor = metric.text,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 32.dp).padding(start = Spacing.s, end = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (!steady) {
                Icon(
                    painterResource(if (changeKg < 0) R.drawable.ic_trend_down else R.drawable.ic_trend_up),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                if (steady) stringResource(R.string.weight_no_change)
                else stringResource(R.string.weight_change_week, amount, unitLabel),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun HistoryList(
    history: List<WeighIn>,
    unit: WeightUnit,
    onOpenEntry: (LocalDate) -> Unit,
) {
    MetricCard(contentPadding = 0.dp) {
        history.forEachIndexed { index, entry ->
            HistoryRow(entry, unit, onClick = { onOpenEntry(entry.date) })
            if (index < history.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun HistoryRow(entry: WeighIn, unit: WeightUnit, onClick: () -> Unit) {
    val metric = VitrackTheme.metrics.weight
    val unitLabel = Format.unitLabel(unit)
    val weight = Format.weight(entry.weightKg, unit)
    val date = Format.shortDate(entry.date)
    val description = stringResource(R.string.weight_row_description, weight, unitLabel, date) +
        if (entry.photos.isNotEmpty()) ". " + stringResource(R.string.weight_has_photo) else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description; role = Role.Button }
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        val photo = entry.photos.firstOrNull()
        if (photo != null) {
            AsyncImage(
                model = photo.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        } else {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(metric.container),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = metric.text,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            ValueWithUnit(weight, unitLabel, NumberStyles.XSmall, unitStyle = MaterialTheme.typography.bodyMedium)
            Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** "Today, 7:12 AM", "Yesterday" or "Tue, 24 Nov"; time only when logged on that day. */
@Composable
private fun loggedLabel(entry: WeighIn, today: LocalDate): String {
    val day = when (entry.date) {
        today -> stringResource(R.string.weight_today)
        today.minusDays(1) -> stringResource(R.string.weight_yesterday)
        else -> Format.shortDate(entry.date)
    }
    val logged = entry.loggedAt.atZone(ZoneId.systemDefault())
    return if (logged.toLocalDate() == entry.date) {
        stringResource(R.string.weight_day_time, day, Format.time(logged.toLocalTime()))
    } else {
        day
    }
}
