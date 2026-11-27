package com.vitrack.app.ui.strain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitrack.app.R
import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.activity.MAX_STRAIN
import com.vitrack.app.data.activity.Workout
import com.vitrack.app.ui.common.BarChart
import com.vitrack.app.ui.common.BarSpec
import com.vitrack.app.ui.common.ChartPeriod
import com.vitrack.app.ui.common.PeriodSelector
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.common.MetricCard
import com.vitrack.app.ui.common.ValueWithUnit
import com.vitrack.app.ui.theme.CardShapes
import com.vitrack.app.ui.theme.NumberStyles
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun StrainRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StrainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StrainScreen(uiState = uiState, onBack = onBack, onSelectPeriod = viewModel::selectPeriod, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StrainScreen(
    uiState: StrainUiState,
    onBack: () -> Unit,
    onSelectPeriod: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.metric_strain)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        when (uiState) {
            StrainUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
            is StrainUiState.Ready -> StrainContent(uiState, onSelectPeriod, padding)
        }
    }
}

@Composable
private fun StrainContent(
    state: StrainUiState.Ready,
    onSelectPeriod: (ChartPeriod) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item { StrainRing(state.today.strain) }
        item { StatsGrid(state.today) }
        item { HistoryCard(state, onSelectPeriod) }
        if (state.workouts.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.strain_todays_workouts),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
                )
            }
            item { WorkoutList(state.workouts) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StrainRing(strain: Double) {
    val metric = VitrackTheme.metrics.strain
    val stroke = with(LocalDensity.current) { Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round) }
    val level = stringResource(
        when (strainLevel(strain)) {
            StrainLevel.Light -> R.string.strain_level_light
            StrainLevel.Moderate -> R.string.strain_level_moderate
            StrainLevel.Strenuous -> R.string.strain_level_strenuous
            StrainLevel.AllOut -> R.string.strain_level_all_out
        },
    )
    val description = stringResource(R.string.strain_ring_description, Format.oneDecimal(strain), level)
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator(
            progress = { (strain / MAX_STRAIN).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.size(200.dp),
            color = metric.graphic,
            trackColor = metric.track,
            stroke = stroke,
            trackStroke = stroke,
            wavelength = 36.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Format.oneDecimal(strain), style = NumberStyles.Hero)
            Text(
                stringResource(R.string.strain_of_max, level),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsGrid(today: DailyActivity) {
    val metrics = VitrackTheme.metrics
    val stats = listOf(
        Triple(stringResource(R.string.strain_calories), Format.grouped(today.calories) to "kcal", metrics.strain.text),
        Triple(stringResource(R.string.metric_steps), Format.grouped(today.steps) to stringResource(R.string.unit_steps), metrics.steps.text),
        Triple(stringResource(R.string.strain_avg_hr), today.averageHeartRate.toString() to "bpm", metrics.heart.text),
        Triple(stringResource(R.string.strain_max_hr), today.maxHeartRate.toString() to "bpm", metrics.heart.text),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { (label, value, color) ->
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        shape = CardShapes.Stat,
                        contentPadding = Spacing.m,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = color,
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                        ValueWithUnit(
                            value = value.first,
                            unit = value.second,
                            valueStyle = NumberStyles.Medium,
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(state: StrainUiState.Ready, onSelectPeriod: (ChartPeriod) -> Unit) {
    val metric = VitrackTheme.metrics.strain
    MetricCard {
        PeriodSelector(selected = state.period, onSelect = onSelectPeriod)
        val bars = state.bars.mapIndexed { i, bar ->
            BarSpec(
                value = bar.strain.toFloat(),
                label = when (state.period) {
                    ChartPeriod.Week -> Format.dayLetter(bar.start)
                    // Label every 5th day back from today, so today is always labelled.
                    ChartPeriod.Month -> if ((state.bars.lastIndex - i) % 5 == 0) bar.start.dayOfMonth.toString() else ""
                    ChartPeriod.Year -> Format.monthLetter(bar.start)
                },
                highlighted = bar.isCurrent,
                valueLabel = if (state.period == ChartPeriod.Week) Format.oneDecimal(bar.strain) else null,
            )
        }
        BarChart(
            bars = bars,
            color = metric.graphic,
            maxValue = MAX_STRAIN.toFloat(),
            chartHeight = 110.dp,
            barSpacing = when (state.period) {
                ChartPeriod.Week -> 10.dp
                ChartPeriod.Month -> 3.dp
                ChartPeriod.Year -> 6.dp
            },
            contentDescription = stringResource(R.string.strain_chart_description),
            modifier = Modifier.padding(top = Spacing.l),
        )
    }
}

@Composable
private fun WorkoutList(workouts: List<Workout>) {
    MetricCard(contentPadding = 0.dp) {
        workouts.forEachIndexed { index, workout ->
            Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(workout.name, style = MaterialTheme.typography.titleMedium)
                    ValueWithUnit(
                        value = Format.oneDecimal(workout.strain),
                        unit = stringResource(R.string.activity_strain_unit),
                        valueStyle = NumberStyles.XSmall,
                        unitStyle = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    stringResource(
                        R.string.strain_workout_meta,
                        Format.time(workout.start), workout.durationMinutes, workout.averageHeartRate, workout.maxHeartRate,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index < workouts.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Preview(name = "Strain · dark", widthDp = 412, heightDp = 1000)
@Composable
private fun StrainScreenPreview() {
    val today = LocalDate.of(2026, 11, 26)
    val strain = listOf(13.2, 15.8, 6.4, 10.9, 14.1, 8.7, 11.8)
    VitrackTheme(darkTheme = true) {
        StrainScreen(
            uiState = StrainUiState.Ready(
                today = DailyActivity(today, 86, 64, 11.8, 7_235, 2_150, 74, 152),
                period = ChartPeriod.Week,
                bars = strain.mapIndexed { i, s -> StrainBar(today.minusDays((6 - i).toLong()), s, i == 6) },
                workouts = listOf(
                    Workout("Outdoor cycling", LocalTime.of(7, 10), 35, 138, 152, 9.4, emptyList()),
                    Workout("Walking", LocalTime.of(12, 40), 22, 101, 118, 3.1, emptyList()),
                ),
            ),
            onBack = {},
            onSelectPeriod = {},
        )
    }
}
