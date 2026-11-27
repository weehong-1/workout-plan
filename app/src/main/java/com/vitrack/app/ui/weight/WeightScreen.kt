package com.vitrack.app.ui.weight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import com.vitrack.app.ui.common.ChoiceDefaults
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.common.MetricCard
import com.vitrack.app.ui.common.MetricIconBadge
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
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val weight = VitrackTheme.metrics.weight
    val listState = rememberLazyListState()
    // The button shrinks to its icon once the list scrolls past the header.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    // History rows slide in, staggered, only while the screen is first appearing;
    // rows scrolled into view later just appear.
    var entering by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(900)
        entering = false
    }
    Box(modifier.fillMaxSize().statusBarsPadding()) {
        when (uiState) {
            WeightUiState.Loading -> ContainedLoadingIndicator(Modifier.align(Alignment.Center))
            is WeightUiState.Empty -> LazyColumn(state = listState, contentPadding = listPadding) {
                item { Header(uiState.displayName) }
                item { EmptyCard() }
            }
            is WeightUiState.Ready -> LazyColumn(
                state = listState,
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Header(uiState.displayName) }
                item { LatestCard(uiState, onSelectPeriod) }
                item {
                    Text(
                        stringResource(R.string.weight_history),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = Spacing.m, bottom = 2.dp).semantics { heading() },
                    )
                }
                // One section per week: its header, then its weigh-ins. The
                // stagger counts across sections so the list cascades top to bottom.
                var order = 0
                uiState.weeks.forEach { group ->
                    val headerOrder = order++
                    item(key = "week-${group.week.weekStart}") {
                        EnterStaggered(index = headerOrder, animate = entering, modifier = Modifier.animateItem()) {
                            WeekHeader(group, uiState.unit, uiState.thisWeekStart)
                        }
                    }
                    group.rows.forEach { row ->
                        val rowOrder = order++
                        item(key = row.entry.date.toEpochDay()) {
                            EnterStaggered(index = rowOrder, animate = entering, modifier = Modifier.animateItem()) {
                                HistoryRowCard(row, uiState.unit, onClick = { onOpenEntry(row.entry.date) })
                            }
                        }
                    }
                }
            }
        }
        if (uiState !is WeightUiState.Loading) {
            ExtendedFloatingActionButton(
                onClick = onLogWeight,
                expanded = fabExpanded,
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        // When collapsed the label is hidden, so the icon carries it.
                        contentDescription = if (fabExpanded) null else stringResource(R.string.weight_log),
                        modifier = Modifier.size(24.dp),
                    )
                },
                text = { Text(stringResource(R.string.weight_log), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)) },
                containerColor = weight.strong,
                contentColor = weight.onStrong,
                shape = RoundedCornerShape(20.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l).height(64.dp),
            )
        }
    }
}

// Bottom padding keeps the last history row clear of the floating button.
private val listPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 104.dp)

/**
 * Fades and slides [content] up into place, each [index] a little after the
 * one before (capped, so long lists don't wait). Shows it at once when
 * [animate] is false.
 */
@Composable
private fun EnterStaggered(index: Int, animate: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(!animate).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = fadeIn(tween(350, delayMillis = 60 * index.coerceAtMost(8))) +
            slideInVertically(
                animationSpec = tween(450, delayMillis = 60 * index.coerceAtMost(8), easing = FastOutSlowInEasing),
                initialOffsetY = { it / 2 },
            ),
    ) {
        content()
    }
}

@Composable
private fun Header(displayName: String?) {
    Column {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.s), horizontalArrangement = Arrangement.End) {
            AccountAvatar(displayName)
        }
        Text(
            stringResource(R.string.weight_title),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.l).semantics { heading() },
        )
    }
}

@Composable
private fun EmptyCard() {
    val weight = VitrackTheme.metrics.weight
    MetricCard(shape = RoundedCornerShape(36.dp), contentPadding = Spacing.xxl, color = weight.hero, modifier = Modifier.fillMaxWidth()) {
        MetricIconBadge(R.drawable.ic_nav_weight, tint = weight.text, container = weight.badge, size = 48.dp, iconSize = 26.dp)
        Text(
            stringResource(R.string.weight_empty_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
    val weight = VitrackTheme.metrics.weight
    val unitLabel = Format.unitLabel(state.unit)
    MetricCard(shape = RoundedCornerShape(36.dp), contentPadding = Spacing.xl, color = weight.hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(Modifier.weight(1f)) {
                val week = state.currentWeek
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    MetricIconBadge(R.drawable.ic_nav_weight, tint = weight.text, container = weight.badge)
                    Text(
                        stringResource(
                            R.string.weight_week_average_label,
                            weekLabel(week.weekStart, state.thisWeekStart),
                            pluralStringResource(R.plurals.weight_weighins, week.count, week.count),
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = weight.text,
                    )
                }
                // The week's average counts to its value, like an odometer.
                CountingWeight(
                    kg = week.averageKg,
                    unit = state.unit,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    stringResource(
                        R.string.weight_latest_line,
                        Format.weight(state.latest.weightKg, state.unit),
                        unitLabel,
                        loggedLabel(state.latest, state.today),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.weeklyChangeKg?.let { WeeklyChangeCookie(it, state.unit) }
        }
        PeriodSelector(
            selected = state.period,
            onSelect = onSelectPeriod,
            colors = ChoiceDefaults.metric(weight, unchecked = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
        WeightTrendChart(
            points = state.chart,
            unit = state.unit,
            start = state.chartStart,
            end = state.today,
            color = weight.graphic,
            ringColor = weight.hero,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

/**
 * A weight that counts to its value: from a little below on first show, then
 * from the old value to the new one whenever it changes. Tabular digits keep
 * the number from jittering while it counts.
 */
@Composable
private fun CountingWeight(kg: Double, unit: WeightUnit, modifier: Modifier = Modifier) {
    val target = unit.fromKg(kg).toFloat()
    val shown = remember { Animatable(target * 0.96f) }
    LaunchedEffect(target) {
        shown.animateTo(target, tween(durationMillis = 1_000, easing = FastOutSlowInEasing))
    }
    val description = "${Format.weight(kg, unit)} ${Format.unitLabel(unit)}"
    ValueWithUnit(
        value = Format.oneDecimal(shown.value.toDouble()),
        unit = Format.unitLabel(unit),
        valueStyle = NumberStyles.Hero.copy(fontSize = 60.sp, lineHeight = 64.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
        unitStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    )
}

/** "This week", "Last week" or "17 – 23 Nov". */
@Composable
private fun weekLabel(weekStart: LocalDate, thisWeekStart: LocalDate): String = when (weekStart) {
    thisWeekStart -> stringResource(R.string.weight_this_week)
    thisWeekStart.minusWeeks(1) -> stringResource(R.string.weight_last_week)
    else -> stringResource(R.string.weight_week_range, Format.dayMonth(weekStart), Format.dayMonth(weekStart.plusDays(6)))
}

/** A week's section header: its name, its average, and its change from the week before. */
@Composable
private fun WeekHeader(group: WeekGroup, unit: WeightUnit, thisWeekStart: LocalDate) {
    val weight = VitrackTheme.metrics.weight
    val unitLabel = Format.unitLabel(unit)
    val label = weekLabel(group.week.weekStart, thisWeekStart)
    val average = Format.weight(group.week.averageKg, unit)
    val description = stringResource(R.string.weight_week_header_description, label, average, unitLabel) +
        (group.changeKg?.let { ". " + changeDescription(it, unit) } ?: "")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.xs, top = Spacing.m, bottom = 2.dp)
            .clearAndSetSemantics { contentDescription = description; heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.weight_week_avg, average, unitLabel),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = weight.text,
        )
        group.changeKg?.let { ChangePill(it, unit) }
    }
}

/** The week's change on a 9-sided cookie in the weight colour. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeeklyChangeCookie(changeKg: Double, unit: WeightUnit) {
    val weight = VitrackTheme.metrics.weight
    val unitLabel = Format.unitLabel(unit)
    val amount = Format.weight(abs(changeKg), unit)
    // Under 0.05 in the shown unit rounds to 0.0: treat as no change.
    val steady = amount == Format.oneDecimal(0.0)
    val description = when {
        steady -> stringResource(R.string.weight_no_change)
        changeKg < 0 -> stringResource(R.string.weight_change_down, amount, unitLabel)
        else -> stringResource(R.string.weight_change_up, amount, unitLabel)
    }
    // Springs in with a quarter turn of the cookie whenever the change is new;
    // only the shape spins, the text just scales so it stays readable.
    val spin = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(changeKg, unit) {
        spin.snapTo(-90f)
        scale.snapTo(0.5f)
        launch { spin.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = spin.value
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(weight.graphic),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        ) {
            Text(
                signed(changeKg, unit, steady),
                style = NumberStyles.Small,
                color = Color.White,
            )
            Text(
                stringResource(R.string.weight_per_week, unitLabel),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

/** "−0.3", "+0.1" or "0.0", with a true minus sign. */
private fun signed(changeKg: Double, unit: WeightUnit, steady: Boolean): String {
    val amount = Format.weight(abs(changeKg), unit)
    return when {
        steady -> amount
        changeKg < 0 -> "−$amount"
        else -> "+$amount"
    }
}

@Composable
private fun HistoryRowCard(row: HistoryRow, unit: WeightUnit, onClick: () -> Unit) {
    val metrics = VitrackTheme.metrics
    val weight = metrics.weight
    val entry = row.entry
    val unitLabel = Format.unitLabel(unit)
    val value = Format.weight(entry.weightKg, unit)
    val description = stringResource(R.string.weight_row_description, value, unitLabel, Format.shortDate(entry.date)) +
        (row.changeKg?.let { ". " + changeDescription(it, unit) } ?: "") +
        if (entry.photos.isNotEmpty()) ". " + stringResource(R.string.weight_has_photo) else ""
    Surface(
        onClick = onClick,
        shape = CardShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description; role = Role.Button },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 68.dp).padding(horizontal = Spacing.m, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(weight.badge), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(entry.date.dayOfMonth.toString(), style = NumberStyles.XSmall.copy(lineHeight = 18.sp), color = weight.text)
                    Text(
                        entry.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold),
                        color = weight.text,
                    )
                }
            }
            ValueWithUnit(value, unitLabel, NumberStyles.Small, unitStyle = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            row.changeKg?.let { ChangePill(it, unit) }
            entry.photos.firstOrNull()?.let { photo ->
                AsyncImage(
                    model = photo.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
    }
}

@Composable
private fun ChangePill(changeKg: Double, unit: WeightUnit) {
    val trend = VitrackTheme.metrics.trend
    val steady = Format.weight(abs(changeKg), unit) == Format.oneDecimal(0.0)
    val (fg, bg) = when {
        steady -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHigh
        changeKg < 0 -> trend.goodText to trend.goodContainer
        else -> trend.upText to trend.upContainer
    }
    Text(
        signed(changeKg, unit, steady),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
        color = fg,
        modifier = Modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = Spacing.xs),
    )
}

@Composable
private fun changeDescription(changeKg: Double, unit: WeightUnit): String {
    val amount = Format.weight(abs(changeKg), unit)
    val unitLabel = Format.unitLabel(unit)
    return when {
        amount == Format.oneDecimal(0.0) -> stringResource(R.string.weight_row_same)
        changeKg < 0 -> stringResource(R.string.weight_row_down, amount, unitLabel)
        else -> stringResource(R.string.weight_row_up, amount, unitLabel)
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
