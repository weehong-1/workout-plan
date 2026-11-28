package com.vitrack.app.ui.strain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitrack.app.R
import com.vitrack.app.ui.common.ConnectWhoopPrompt
import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.activity.MAX_STRAIN
import com.vitrack.app.data.activity.Workout
import com.vitrack.app.data.activity.WorkoutKind
import com.vitrack.app.ui.activity.workoutIcon
import com.vitrack.app.ui.common.BarChart
import com.vitrack.app.ui.common.BarSpec
import com.vitrack.app.ui.common.ChartPeriod
import com.vitrack.app.ui.common.ChoiceDefaults
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.common.MetricCard
import com.vitrack.app.ui.common.MetricIconBadge
import com.vitrack.app.ui.common.PeriodSelector
import com.vitrack.app.ui.common.SegmentedBar
import com.vitrack.app.ui.common.ValueWithUnit
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
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.metric_strain),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        if (uiState is StrainUiState.Ready) {
                            Text(
                                Format.dayHeading(uiState.today.date),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
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
            StrainUiState.NotConnected -> ConnectWhoopPrompt(Modifier.padding(padding))
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
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.xs, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item { HeroCard(state) }
        item { StepsCard(state) }
        item { HistoryCard(state, onSelectPeriod) }
        if (state.workouts.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.strain_todays_workouts),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = Spacing.m).semantics { heading() },
                )
            }
            state.workouts.forEach { workout -> item { WorkoutCard(workout) } }
        }
    }
}

/** Ring, level pill and comparison, then calories and heart rate, on the strain colour. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(state: StrainUiState.Ready) {
    val metrics = VitrackTheme.metrics
    val strain = metrics.strain
    val today = state.today
    val level = stringResource(
        when (strainLevel(today.strain)) {
            StrainLevel.Light -> R.string.strain_level_light
            StrainLevel.Moderate -> R.string.strain_level_moderate
            StrainLevel.Strenuous -> R.string.strain_level_strenuous
            StrainLevel.AllOut -> R.string.strain_level_all_out
        },
    )
    val ringDescription = stringResource(R.string.strain_ring_description, Format.oneDecimal(today.strain), level)
    val stroke = with(LocalDensity.current) { Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round) }
    MetricCard(shape = RoundedCornerShape(36.dp), contentPadding = Spacing.xl, color = strain.hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Box(Modifier.clearAndSetSemantics { contentDescription = ringDescription }, contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { (today.strain / MAX_STRAIN).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.size(168.dp),
                    color = strain.graphic,
                    trackColor = strain.badge,
                    stroke = stroke,
                    trackStroke = stroke,
                    wavelength = 32.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Format.oneDecimal(today.strain), style = NumberStyles.Hero.copy(fontSize = 44.sp, lineHeight = 48.sp))
                    Text(
                        stringResource(R.string.strain_of_max),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(level, strain.strong, strain.onStrong)
                val prefix = stringResource(
                    when (state.comparedToAverage) {
                        Comparison.Above -> R.string.strain_avg_above
                        Comparison.Below -> R.string.strain_avg_below
                        Comparison.Level -> R.string.strain_avg_level
                    },
                )
                Text(
                    buildAnnotatedString {
                        append("$prefix ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(Format.oneDecimal(state.weeklyAverageStrain)) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            MiniStat(stringResource(R.string.strain_calories), Format.grouped(today.calories), "kcal", strain.text, Modifier.weight(1f))
            MiniStat(stringResource(R.string.strain_avg_hr_short), today.averageHeartRate.toString(), "bpm", metrics.heart.text, Modifier.weight(1f))
            MiniStat(stringResource(R.string.strain_max_hr_short), today.maxHeartRate.toString(), "bpm", metrics.heart.text, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, unit: String, labelColor: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = labelColor)
            ValueWithUnit(value, unit, NumberStyles.Small.copy(fontSize = 20.sp), unitStyle = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
        color = content,
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = Spacing.m, vertical = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepsCard(state: StrainUiState.Ready) {
    val steps = VitrackTheme.metrics.steps
    val description = stringResource(
        R.string.activity_steps_progress, Format.grouped(state.today.steps), Format.grouped(state.stepGoal),
    )
    MetricCard(shape = RoundedCornerShape(28.dp), contentPadding = Spacing.l, color = steps.hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricIconBadge(R.drawable.ic_nav_activity, tint = steps.text, container = steps.badge, size = 36.dp, iconSize = 20.dp)
            Text(
                stringResource(R.string.metric_steps),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = steps.text,
                modifier = Modifier.weight(1f),
            )
            ValueWithUnit(Format.grouped(state.today.steps), "/ ${Format.grouped(state.stepGoal)}", NumberStyles.Medium)
        }
        val stroke = with(LocalDensity.current) { Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round) }
        LinearWavyProgressIndicator(
            progress = { state.stepProgress },
            color = steps.graphic,
            trackColor = steps.badge,
            stroke = stroke,
            trackStroke = stroke,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).semantics { contentDescription = description },
        )
    }
}

@Composable
private fun HistoryCard(state: StrainUiState.Ready, onSelectPeriod: (ChartPeriod) -> Unit) {
    val metric = VitrackTheme.metrics.strain
    MetricCard(shape = RoundedCornerShape(28.dp)) {
        PeriodSelector(selected = state.period, onSelect = onSelectPeriod, colors = ChoiceDefaults.metric(metric))
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
            chartHeight = 120.dp,
            barSpacing = when (state.period) {
                ChartPeriod.Week -> 8.dp
                ChartPeriod.Month -> 3.dp
                ChartPeriod.Year -> 6.dp
            },
            barShape = CircleShape,
            highlightedValueColor = metric.onStrong,
            contentDescription = stringResource(R.string.strain_chart_description),
            modifier = Modifier.padding(top = Spacing.l),
        )
    }
}

@Composable
private fun WorkoutCard(workout: Workout) {
    val strain = VitrackTheme.metrics.strain
    val heart = VitrackTheme.metrics.heart.graphic
    MetricCard(contentPadding = 0.dp) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                MetricIconBadge(workoutIcon(workout.kind), tint = strain.text, container = strain.badge, size = 40.dp, iconSize = 20.dp)
                Column(Modifier.weight(1f)) {
                    Text(workout.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        stringResource(R.string.activity_workout_meta, Format.time(workout.start), workout.durationMinutes, workout.averageHeartRate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val strainDescription = stringResource(R.string.strain_workout_value, Format.oneDecimal(workout.strain))
                Text(
                    Format.oneDecimal(workout.strain),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = strain.onStrong,
                    modifier = Modifier
                        .background(strain.strong, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clearAndSetSemantics { contentDescription = strainDescription },
                )
            }
            SegmentedBar(
                segments = workout.zones.mapIndexed { i, share -> share to heart.copy(alpha = 0.3f + i * 0.17f) },
                contentDescription = stringResource(R.string.activity_zones),
                height = 10.dp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Preview(name = "Strain · dark", widthDp = 412, heightDp = 1070)
@Composable
private fun StrainScreenPreview() {
    val today = LocalDate.of(2026, 11, 26)
    val strain = listOf(13.2, 15.8, 6.4, 10.9, 14.1, 8.7, 11.8)
    VitrackTheme(darkTheme = true) {
        StrainScreen(
            uiState = StrainUiState.Ready(
                today = DailyActivity(today, 86, 64, 11.8, 7_235, 2_150, 74, 152),
                weeklyAverageStrain = 11.56,
                stepGoal = 10_000,
                period = ChartPeriod.Week,
                bars = strain.mapIndexed { i, s -> StrainBar(today.minusDays((6 - i).toLong()), s, i == 6) },
                workouts = listOf(
                    Workout(WorkoutKind.Cycling, "Outdoor cycling", LocalTime.of(7, 10), 35, 138, 152, 9.4, listOf(0.10f, 0.22f, 0.38f, 0.22f, 0.08f)),
                    Workout(WorkoutKind.Walking, "Walking", LocalTime.of(12, 40), 22, 101, 118, 3.1, listOf(0.45f, 0.40f, 0.15f, 0f, 0f)),
                ),
            ),
            onBack = {},
            onSelectPeriod = {},
        )
    }
}
