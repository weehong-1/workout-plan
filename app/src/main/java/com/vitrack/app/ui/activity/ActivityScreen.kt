package com.vitrack.app.ui.activity

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitrack.app.R
import com.vitrack.app.data.activity.MAX_STRAIN
import com.vitrack.app.data.activity.Workout
import com.vitrack.app.data.activity.WorkoutKind
import com.vitrack.app.ui.common.AccountAvatar
import com.vitrack.app.ui.common.BarChart
import com.vitrack.app.ui.common.BarSpec
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.common.MetricCard
import com.vitrack.app.ui.common.MetricIconBadge
import com.vitrack.app.ui.common.SegmentedBar
import com.vitrack.app.ui.common.Sparkline
import com.vitrack.app.ui.common.ValueWithUnit
import com.vitrack.app.ui.theme.CardShapes
import com.vitrack.app.ui.theme.MetricColor
import com.vitrack.app.ui.theme.NumberStyles
import com.vitrack.app.ui.theme.SleepStageColors
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme

@Composable
fun ActivityRoute(
    onOpenStrain: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ActivityScreen(
        uiState = uiState,
        onOpenStrain = onOpenStrain,
        onSelectStepsRange = viewModel::selectStepsRange,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityScreen(
    uiState: ActivityUiState,
    onOpenStrain: () -> Unit,
    onSelectStepsRange: (StepsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        ActivityUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ContainedLoadingIndicator()
        }
        is ActivityUiState.Ready -> ActivityContent(uiState, onOpenStrain, onSelectStepsRange, modifier)
    }
}

@Composable
private fun ActivityContent(
    state: ActivityUiState.Ready,
    onOpenStrain: () -> Unit,
    onSelectStepsRange: (StepsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRangeSheet by remember { mutableStateOf(false) }
    // Content stops at the status bar so it never scrolls under the clock.
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item { Header(state) }
        item { TodayCard(state, onOpenStrain) }
        item {
            Text(
                stringResource(R.string.activity_highlights),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xs).semantics { heading() },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SleepCard(state, Modifier.weight(1f))
                HeartCard(state, Modifier.weight(1f))
            }
        }
        item { StepsCard(state, onOpenRange = { showRangeSheet = true }) }
        if (state.workouts.isNotEmpty()) {
            item { WorkoutsCard(state, onOpenDetails = onOpenStrain) }
        }
    }
    if (showRangeSheet) {
        StepsRangeSheet(
            current = state.stepsRange,
            today = state.date,
            onApply = { onSelectStepsRange(it); showRangeSheet = false },
            onDismiss = { showRangeSheet = false },
        )
    }
}

@Composable
private fun Header(state: ActivityUiState.Ready) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The day picker is not built yet.
            IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
                Icon(
                    painterResource(R.drawable.ic_calendar),
                    contentDescription = stringResource(R.string.activity_pick_day),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AccountAvatar(state.displayName)
        }
        Text(
            Format.dayHeading(state.date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = state.displayName?.let { stringResource(R.string.activity_greeting_named, it) }
                ?: stringResource(R.string.activity_greeting),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = Spacing.s).semantics { heading() },
        )
    }
}

/** Steps progress on top, then the sleep, recovery and strain dials. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TodayCard(state: ActivityUiState.Ready, onOpenStrain: () -> Unit) {
    val metrics = VitrackTheme.metrics
    MetricCard(shape = CardShapes.Hero, contentPadding = Spacing.xl) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(stringResource(R.string.metric_steps), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = metrics.steps.text)
            ValueWithUnit(
                value = Format.grouped(state.steps),
                unit = "/ ${Format.grouped(state.stepGoal)}",
                valueStyle = NumberStyles.Small,
            )
        }
        val stepsDescription = stringResource(
            R.string.activity_steps_progress, Format.grouped(state.steps), Format.grouped(state.stepGoal),
        )
        LinearWavyProgressIndicator(
            progress = { state.stepProgress },
            color = metrics.steps.graphic,
            trackColor = metrics.steps.track,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).semantics { contentDescription = stepsDescription },
        )
        Text(
            stringResource(R.string.activity_today_summary, Format.grouped(state.calories), state.averageHeartRate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s),
        )
        HorizontalDivider(Modifier.padding(top = Spacing.l, bottom = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val sleepLabel = stringResource(R.string.metric_sleep)
            val recoveryLabel = stringResource(R.string.metric_recovery)
            MetricDial(
                label = sleepLabel,
                value = "${state.sleepPerformance}%",
                progress = state.sleepPerformance / 100f,
                metric = metrics.sleep,
                description = stringResource(R.string.activity_dial_percent, sleepLabel, state.sleepPerformance),
                modifier = Modifier.weight(1f),
            )
            MetricDial(
                label = recoveryLabel,
                value = "${state.recovery}%",
                progress = state.recovery / 100f,
                metric = metrics.recovery,
                description = stringResource(R.string.activity_dial_percent, recoveryLabel, state.recovery),
                modifier = Modifier.weight(1f),
            )
            MetricDial(
                label = stringResource(R.string.metric_strain),
                value = Format.oneDecimal(state.strain),
                progress = (state.strain / MAX_STRAIN).toFloat(),
                metric = metrics.strain,
                description = stringResource(R.string.activity_dial_strain, Format.oneDecimal(state.strain)),
                onClick = onOpenStrain,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MetricDial(
    label: String,
    value: String,
    progress: Float,
    metric: MetricColor,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    onClick: (() -> Unit)? = null,
) {
    val stroke = with(LocalDensity.current) { Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round) }
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(size),
                color = metric.graphic,
                trackColor = metric.track,
                stroke = stroke,
                trackStroke = stroke,
                wavelength = 22.dp,
            )
            Text(value, style = NumberStyles.Medium)
        }
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = metric.text)
    }
}

@Composable
private fun HighlightHeader(icon: Int, title: String, metric: MetricColor) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MetricIconBadge(icon = icon, tint = metric.text, container = metric.container)
        Text(title, style = MaterialTheme.typography.titleMedium, color = metric.text)
    }
}

@Composable
private fun HeartCard(state: ActivityUiState.Ready, modifier: Modifier = Modifier) {
    val heart = VitrackTheme.metrics.heart
    MetricCard(modifier = modifier.height(160.dp)) {
        HighlightHeader(R.drawable.ic_heart, stringResource(R.string.metric_heart), heart)
        Spacer(Modifier.weight(1f))
        ValueWithUnit(
            value = state.heart.restingBpm.toString(),
            unit = stringResource(R.string.activity_bpm_resting),
            valueStyle = NumberStyles.Large,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(R.string.activity_hrv, state.heart.hrvMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Sparkline(
                values = state.heart.restingLast7Days,
                color = heart.graphic,
                contentDescription = stringResource(R.string.activity_resting_trend),
                modifier = Modifier.width(64.dp).height(22.dp),
            )
        }
    }
}

@Composable
private fun SleepCard(state: ActivityUiState.Ready, modifier: Modifier = Modifier) {
    val sleep = VitrackTheme.metrics.sleep
    val stages = state.sleep.stages
    val hours = state.sleep.durationMinutes / 60
    val minutes = state.sleep.durationMinutes % 60
    MetricCard(modifier = modifier.height(160.dp)) {
        HighlightHeader(R.drawable.ic_sleep, stringResource(R.string.metric_sleep), sleep)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            ValueWithUnit(hours.toString(), "h", NumberStyles.Large)
            Spacer(Modifier.width(Spacing.xs))
            ValueWithUnit(minutes.toString(), "min", NumberStyles.Large)
        }
        SegmentedBar(
            segments = listOf(
                stages.awake to SleepStageColors.Awake,
                stages.light to SleepStageColors.Light,
                stages.deep to SleepStageColors.Deep,
                stages.rem to SleepStageColors.Rem,
            ),
            contentDescription = stringResource(
                R.string.activity_sleep_stages,
                (stages.awake * 100).toInt(), (stages.light * 100).toInt(),
                (stages.deep * 100).toInt(), (stages.rem * 100).toInt(),
            ),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun StepsCard(state: ActivityUiState.Ready, onOpenRange: () -> Unit) {
    val steps = VitrackTheme.metrics.steps
    val days = state.stepsDays
    val long = days.size > 7
    val maxValue = maxOf(state.stepGoal * 1.2f, days.maxOfOrNull { it.steps }?.toFloat() ?: 0f)
    MetricCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.metric_steps),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        stringResource(R.string.activity_average) + " ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(Format.grouped(state.averageSteps), style = NumberStyles.XSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
                }
            }
            RangeChip(rangeLabel(state.stepsRange), onClick = onOpenRange)
        }
        BarChart(
            bars = days.map { BarSpec(it.steps.toFloat(), Format.dayLetter(it.date), it.highlighted) },
            color = steps.graphic,
            maxValue = maxValue,
            goal = state.stepGoal.toFloat(),
            barSpacing = if (long) 3.dp else 10.dp,
            showLabels = !long,
            contentDescription = stringResource(R.string.activity_steps_chart, Format.grouped(state.stepGoal)),
            modifier = Modifier.padding(top = 14.dp),
        )
        if (long && days.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Format.dayMonth(days.first().date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.dayMonth(days.last().date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            stringResource(R.string.activity_synced, Format.time(state.syncedAt)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun RangeChip(label: String, onClick: () -> Unit) {
    val steps = VitrackTheme.metrics.steps
    val description = stringResource(R.string.activity_range_chip, label)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = steps.container,
        contentColor = steps.text,
        modifier = Modifier.height(40.dp).clearAndSetSemantics { contentDescription = description; role = Role.Button },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
            Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun rangeLabel(range: StepsRange): String = when (range) {
    StepsRange.Last7Days -> stringResource(R.string.activity_range_7)
    StepsRange.Last30Days -> stringResource(R.string.activity_range_30)
    is StepsRange.Custom -> if (range.start == range.end) {
        Format.dayMonth(range.start)
    } else {
        "${Format.dayMonth(range.start)} – ${Format.dayMonth(range.end)}"
    }
}

@Composable
private fun WorkoutsCard(state: ActivityUiState.Ready, onOpenDetails: () -> Unit) {
    MetricCard(contentPadding = Spacing.l) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(R.string.strain_todays_workouts),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.activity_workouts_summary, state.workouts.size, state.workoutMinutes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.workouts.forEach { workout ->
            WorkoutRow(workout)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Surface(
            onClick = onOpenDetails,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(40.dp),
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.activity_workout_details), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(16.dp))
            }
        }
    }
}

@Composable
private fun WorkoutRow(workout: Workout) {
    val strain = VitrackTheme.metrics.strain
    val heart = VitrackTheme.metrics.heart.graphic
    Column(Modifier.padding(top = 10.dp, bottom = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            MetricIconBadge(
                icon = workoutIcon(workout.kind),
                tint = strain.text,
                container = strain.container,
                size = 40.dp,
                iconSize = 20.dp,
                shape = RoundedCornerShape(14.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(workout.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
                Text(
                    stringResource(R.string.activity_workout_meta, Format.time(workout.start), workout.durationMinutes, workout.averageHeartRate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.oneDecimal(workout.strain), style = NumberStyles.Small.copy(fontSize = 20.sp))
                Text(stringResource(R.string.activity_strain_unit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SegmentedBar(
            segments = workout.zones.mapIndexed { i, share -> share to heart.copy(alpha = 0.3f + i * 0.17f) },
            contentDescription = stringResource(R.string.activity_zones),
            height = 6.dp,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

internal fun workoutIcon(kind: WorkoutKind): Int = when (kind) {
    WorkoutKind.Cycling -> R.drawable.ic_cycling
    WorkoutKind.Walking, WorkoutKind.Other -> R.drawable.ic_nav_activity
}

@Preview(name = "Activity · dark", widthDp = 412, heightDp = 1320)
@Composable
private fun ActivityScreenDarkPreview() {
    VitrackTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ActivityScreen(uiState = PreviewActivityState, onOpenStrain = {}, onSelectStepsRange = {})
        }
    }
}

@Preview(name = "Activity · light", widthDp = 412, heightDp = 1320)
@Composable
private fun ActivityScreenLightPreview() {
    VitrackTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ActivityScreen(uiState = PreviewActivityState, onOpenStrain = {}, onSelectStepsRange = {})
        }
    }
}
