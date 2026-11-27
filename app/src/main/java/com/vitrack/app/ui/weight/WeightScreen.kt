package com.vitrack.app.ui.weight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitrack.app.R
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.data.weight.WeightPeriod
import com.vitrack.app.data.weight.WeightZoom
import com.vitrack.app.ui.common.AccountAvatar
import com.vitrack.app.ui.common.ChoiceDefaults
import com.vitrack.app.ui.common.ConnectedChoiceRow
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.common.MetricCard
import com.vitrack.app.ui.common.MetricIconBadge
import com.vitrack.app.ui.common.rememberReducedMotion
import com.vitrack.app.ui.theme.CardShapes
import com.vitrack.app.ui.theme.NumberStyles
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme
import com.vitrack.app.ui.weight.editor.WeighInEditorViewModel
import com.vitrack.app.ui.weight.editor.WeighInSheet
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        onSelectZoom = viewModel::selectZoom,
        onLogWeight = editorViewModel::openNew,
        onOpenEntry = editorViewModel::openExisting,
        modifier = modifier,
    )
    editorState?.let { WeighInSheet(state = it, viewModel = editorViewModel) }
}

/** History rows shown before "Show more", per level. */
private fun initialRows(zoom: WeightZoom) = when (zoom) {
    WeightZoom.Weeks -> 4
    WeightZoom.Months -> 6
    WeightZoom.Years -> 5
}

// Lazy list positions: header, hero card, History title, then the rows.
private const val FIRST_ROW_INDEX = 3

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeightScreen(
    uiState: WeightUiState,
    onSelectZoom: (WeightZoom) -> Unit,
    onLogWeight: () -> Unit,
    onOpenEntry: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weight = VitrackTheme.metrics.weight
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    // The button shrinks to its icon once the list scrolls past the header.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    // Cards rise in, staggered, only while the screen is first appearing.
    var entering by remember { mutableStateOf(!reducedMotion) }
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
            is WeightUiState.Ready -> {
                val zoom = uiState.zoom
                var shown by rememberSaveable(zoom) { mutableIntStateOf(initialRows(zoom)) }
                // The newest card starts open; one card is open at a time.
                var open by rememberSaveable(zoom) { mutableStateOf<Long?>(uiState.history.firstOrNull()?.period?.start?.toEpochDay()) }
                val rows = uiState.history.take(shown)
                LazyColumn(
                    state = listState,
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { Header(uiState.displayName) }
                    item { HeroCard(uiState, onSelectZoom) }
                    item {
                        HistoryTitle(uiState) { targetIndex ->
                            shown = maxOf(shown, targetIndex + 1)
                            open = uiState.history[targetIndex].period.start.toEpochDay()
                            scope.launch { listState.animateScrollToItem(FIRST_ROW_INDEX + targetIndex) }
                        }
                    }
                    itemsIndexed(rows, key = { _, row -> "${row.period.zoom}-${row.period.start}" }) { index, row ->
                        val key = row.period.start.toEpochDay()
                        EnterStaggered(index = index, animate = entering, modifier = Modifier.animateItem()) {
                            PeriodCard(
                                row = row,
                                unit = uiState.unit,
                                expanded = open == key,
                                onToggle = { open = if (open == key) null else key },
                                onOpenEntry = onOpenEntry,
                            )
                        }
                    }
                    item(key = "more-$zoom") {
                        ListFooter(
                            zoom = zoom,
                            remaining = uiState.history.size - rows.size,
                            onMore = { shown += 4 },
                        )
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
                text = { Text(stringResource(R.string.weight_log), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                containerColor = weight.strong,
                contentColor = weight.onStrong,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            )
        }
    }
}

// Bottom padding keeps the last card clear of the floating button.
private val listPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 104.dp)

/**
 * Fades and slides [content] up into place, each [index] a little after the
 * one before (capped, so long lists don't wait). Shows it at once when
 * [animate] is false.
 */
@Composable
private fun EnterStaggered(index: Int, animate: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(!animate).apply { targetState = true } }
    val delayMs = 60 * index.coerceAtMost(8)
    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = fadeIn(tween(350, delayMillis = delayMs)) +
            slideInVertically(tween(500, delayMillis = delayMs, easing = FastOutSlowInEasing)) { it / 4 },
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
            style = MaterialTheme.typography.displaySmall,
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

/** The headline average with its change badge, the level picker, the chart and its trend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(state: WeightUiState.Ready, onSelectZoom: (WeightZoom) -> Unit) {
    val weight = VitrackTheme.metrics.weight
    val unitLabel = Format.unitLabel(state.unit)
    MetricCard(
        shape = RoundedCornerShape(36.dp),
        contentPadding = 0.dp,
        color = weight.hero,
    ) {
        Column(Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 18.dp, bottom = Spacing.l)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        MetricIconBadge(R.drawable.ic_nav_weight, tint = weight.text, container = weight.badge)
                        Text(
                            heroLabel(state),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = weight.text,
                        )
                    }
                    CountingWeight(
                        kg = state.current.averageKg,
                        fromKg = state.previous?.averageKg,
                        unit = state.unit,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                    Text(
                        stringResource(
                            R.string.weight_latest_line,
                            Format.weight(state.latest.weightKg, state.unit),
                            unitLabel,
                            loggedLabel(state.latest, state.today),
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.changeKg?.let { ChangeCookie(it, state.unit, versusLabel(state)) }
            }
            ConnectedChoiceRow(
                options = WeightZoom.entries,
                selected = state.zoom,
                onSelect = onSelectZoom,
                label = {
                    stringResource(
                        when (it) {
                            WeightZoom.Weeks -> R.string.weight_zoom_weeks
                            WeightZoom.Months -> R.string.weight_zoom_months
                            WeightZoom.Years -> R.string.weight_zoom_years
                        },
                    )
                },
                colors = ChoiceDefaults.metric(weight, unchecked = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            WeightPeriodChart(
                periods = state.chart,
                description = chartDescription(state),
                ringColor = weight.hero,
                modifier = Modifier.padding(top = 14.dp),
            )
            if (state.chart.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(axisLabel(state, state.chart.first()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(axisLabel(state, state.chart.last()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.trend?.let { TrendChip(it, state.unit) }
                LegendChip()
            }
        }
    }
}

/**
 * The average counts to its value, like an odometer: from the previous
 * period's average on first show, then from what is shown to each new value.
 * Tabular digits keep it from jittering. Still with reduced motion.
 */
@Composable
private fun CountingWeight(kg: Double, fromKg: Double?, unit: WeightUnit, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val target = unit.fromKg(kg).toFloat()
    val shown = remember { Animatable(if (reducedMotion) target else unit.fromKg(fromKg ?: (kg * 1.02)).toFloat()) }
    LaunchedEffect(target) {
        if (reducedMotion) shown.snapTo(target) else shown.animateTo(target, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val description = "${Format.weight(kg, unit)} ${Format.unitLabel(unit)}"
    Row(modifier.clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.Bottom) {
        Text(
            Format.oneDecimal(shown.value.toDouble()),
            style = NumberStyles.Hero.copy(fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
        )
        Text(
            " ${Format.unitLabel(unit)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/** The change from the previous period on a 9-sided cookie: green when down, red when up. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChangeCookie(changeKg: Double, unit: WeightUnit, versus: String) {
    val trend = VitrackTheme.metrics.trend
    val reducedMotion = rememberReducedMotion()
    val unitLabel = Format.unitLabel(unit)
    val amount = Format.weight(abs(changeKg), unit)
    val steady = amount == Format.oneDecimal(0.0)
    val fill = when {
        steady -> VitrackTheme.metrics.weight.strong
        changeKg < 0 -> trend.goodStrong
        else -> trend.upStrong
    }
    val description = when {
        steady -> stringResource(R.string.weight_cookie_same, versus)
        changeKg < 0 -> stringResource(R.string.weight_cookie_down, amount, unitLabel, versus)
        else -> stringResource(R.string.weight_cookie_up, amount, unitLabel, versus)
    }
    // Springs in with a small turn whenever the change is new; the text only scales.
    val spin = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(changeKg, unit) {
        if (reducedMotion) return@LaunchedEffect
        spin.snapTo(-20f)
        scale.snapTo(0f)
        launch { spin.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
        delay(300)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    Box(Modifier.size(92.dp).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(92.dp)
                .graphicsLayer {
                    rotationZ = spin.value
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(fill),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        ) {
            Text(signed(changeKg, unit, steady), style = NumberStyles.Small, color = Color.White)
            Text(versus, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TrendChip(trend: Trend, unit: WeightUnit) {
    val colors = VitrackTheme.metrics.trend
    val unitLabel = Format.unitLabel(unit)
    val (down, text) = when (trend) {
        is Trend.Streak -> trend.down to pluralStringResource(
            if (trend.down) R.plurals.weight_streak_down else R.plurals.weight_streak_up, trend.weeks, trend.weeks,
        )
        is Trend.OverMonths -> (trend.changeKg <= 0) to stringResource(
            if (trend.changeKg <= 0) R.string.weight_over_months_down else R.string.weight_over_months_up,
            Format.weight(abs(trend.changeKg), unit), unitLabel, trend.months,
        )
        is Trend.FromYear -> (trend.changeKg <= 0) to stringResource(
            if (trend.changeKg <= 0) R.string.weight_from_peak else R.string.weight_from_low,
            Format.weight(abs(trend.changeKg), unit), unitLabel, trend.year,
        )
    }
    Row(
        modifier = Modifier
            .height(32.dp)
            .background(if (down) colors.goodContainer else colors.upContainer, CircleShape)
            .padding(start = Spacing.s, end = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painterResource(if (down) R.drawable.ic_trend_down else R.drawable.ic_trend_up),
            contentDescription = null,
            tint = if (down) colors.goodText else colors.upText,
            modifier = Modifier.size(16.dp),
        )
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = if (down) colors.goodText else colors.upText)
    }
}

@Composable
private fun LegendChip() {
    val colors = VitrackTheme.metrics.trend
    Row(
        modifier = Modifier
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(colors.goodDot, CircleShape))
        Text(stringResource(R.string.weight_legend_lower), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.padding(start = Spacing.xs).size(8.dp).background(colors.upDot, CircleShape))
        Text(stringResource(R.string.weight_legend_higher), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "History" with a chip that jumps to a month (Weeks) or a year (Months). */
@Composable
private fun HistoryTitle(state: WeightUiState.Ready, onJump: (rowIndex: Int) -> Unit) {
    // Jump targets: the first row of each month (Weeks) or year (Months).
    val targets: List<Pair<String, Int>> = when (state.zoom) {
        WeightZoom.Weeks -> state.history.withIndex()
            .distinctBy { (_, row) -> row.period.start.withDayOfMonth(1) }
            .map { (i, row) -> monthYear(row.period.start) to i }
        WeightZoom.Months -> state.history.withIndex()
            .distinctBy { (_, row) -> row.period.start.year }
            .map { (i, row) -> row.period.start.year.toString() to i }
        WeightZoom.Years -> emptyList()
    }
    var menuOpen by remember { mutableStateOf(false) }
    var label by rememberSaveable(state.zoom) { mutableStateOf<String?>(null) }
    val chipLabel = label ?: targets.firstOrNull()?.first ?: stringResource(R.string.weight_all_years)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.weight_history),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Box {
            val jumpDescription = stringResource(R.string.weight_jump_description, chipLabel)
            Surface(
                onClick = { if (targets.size > 1) menuOpen = true },
                enabled = targets.size > 1,
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.height(36.dp).semantics { contentDescription = jumpDescription },
            ) {
                Row(Modifier.padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(chipLabel, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    if (targets.size > 1) {
                        Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, modifier = Modifier.padding(start = Spacing.xs).size(18.dp))
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                targets.forEach { (name, index) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            menuOpen = false
                            label = name
                            onJump(index)
                        },
                    )
                }
            }
        }
    }
}

/** A History card: badge, average, count and change; opens to its details. */
@Composable
private fun PeriodCard(
    row: PeriodRow,
    unit: WeightUnit,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenEntry: (LocalDate) -> Unit,
) {
    val weight = VitrackTheme.metrics.weight
    val period = row.period
    val unitLabel = Format.unitLabel(unit)
    val average = Format.weight(period.averageKg, unit)
    val meta = periodMeta(period, unit)
    val (top, sub) = badgeLines(period)
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val stateText = stringResource(if (expanded) R.string.weight_expanded else R.string.weight_collapsed)
    val description = stringResource(R.string.weight_period_description, periodName(period), average, unitLabel, meta) +
        (row.changeKg?.let { ". " + changeDescription(it, unit) } ?: "")
    Surface(
        shape = CardShapes.Card,
        color = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .clearAndSetSemantics {
                        contentDescription = description
                        stateDescription = stateText
                        role = Role.Button
                    }
                    .heightIn(min = 68.dp)
                    .padding(horizontal = Spacing.m, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Column(
                    Modifier.size(width = 52.dp, height = 44.dp).clip(RoundedCornerShape(14.dp)).background(weight.badge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(top, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 16.sp), color = weight.text)
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.labelMedium.copy(lineHeight = 14.sp), color = weight.text)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(average, style = NumberStyles.Small)
                        Text(
                            " " + stringResource(R.string.weight_unit_avg, unitLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ChangePill(row.changeKg, unit)
                Icon(
                    painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(chevron),
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(start = 76.dp, end = Spacing.m, bottom = 10.dp)) {
                    when (val details = row.details) {
                        is PeriodDetails.Entries -> details.entries.forEach { entry ->
                            DetailLine(
                                label = Format.shortDate(entry.date),
                                value = Format.weight(entry.weightKg, unit),
                                photo = entry,
                                onClick = { onOpenEntry(entry.date) },
                            )
                        }
                        is PeriodDetails.Weeks -> details.weeks.forEach { week ->
                            DetailLine(
                                label = stringResource(R.string.weight_week_range, Format.dayMonth(week.start), Format.dayMonth(week.end)),
                                value = stringResource(R.string.weight_value_avg, Format.weight(week.averageKg, unit)),
                            )
                        }
                        is PeriodDetails.Extremes -> {
                            DetailLine(
                                label = stringResource(R.string.weight_lowest_month, monthName(details.lowestMonth.start)),
                                value = stringResource(R.string.weight_value_avg, Format.weight(details.lowestMonth.averageKg, unit)),
                            )
                            DetailLine(
                                label = stringResource(R.string.weight_highest_month, monthName(details.highestMonth.start)),
                                value = stringResource(R.string.weight_value_avg, Format.weight(details.highestMonth.averageKg, unit)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, photo: WeighIn? = null, onClick: (() -> Unit)? = null) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium)
            photo?.photos?.firstOrNull()?.let {
                AsyncImage(
                    model = it.file,
                    contentDescription = stringResource(R.string.weight_has_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
    }
}

@Composable
private fun ChangePill(changeKg: Double?, unit: WeightUnit) {
    val trend = VitrackTheme.metrics.trend
    val steady = changeKg == null || Format.weight(abs(changeKg), unit) == Format.oneDecimal(0.0)
    val (fg, bg) = when {
        changeKg == null || steady -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHighest
        changeKg < 0 -> trend.goodText to trend.goodContainer
        else -> trend.upText to trend.upContainer
    }
    Text(
        if (changeKg == null) "—" else signed(changeKg, unit, steady),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = fg,
        modifier = Modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = Spacing.xs),
    )
}

@Composable
private fun ListFooter(zoom: WeightZoom, remaining: Int, onMore: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (remaining > 0) {
            OutlinedButton(
                onClick = onMore,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    stringResource(R.string.weight_show_more, minOf(4, remaining)),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = Spacing.s).size(18.dp))
            }
        }
        Text(
            stringResource(
                when (zoom) {
                    WeightZoom.Weeks -> R.string.weight_note_weeks
                    WeightZoom.Months -> R.string.weight_note_months
                    WeightZoom.Years -> R.string.weight_note_years
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/* ---- Labels ---- */

private fun monthYear(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
private fun monthName(date: LocalDate): String = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
private fun shortMonth(date: LocalDate): String = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** Badge lines: "7 / Sep", "Nov / 2026" or "2026". */
private fun badgeLines(period: WeightPeriod): Pair<String, String> = when (period.zoom) {
    WeightZoom.Weeks -> period.start.dayOfMonth.toString() to shortMonth(period.start)
    WeightZoom.Months -> shortMonth(period.start) to period.start.year.toString()
    WeightZoom.Years -> period.start.year.toString() to ""
}

@Composable
private fun periodName(period: WeightPeriod): String = when (period.zoom) {
    WeightZoom.Weeks -> stringResource(R.string.weight_week_range, Format.dayMonth(period.start), Format.dayMonth(period.end))
    WeightZoom.Months -> monthYear(period.start)
    WeightZoom.Years -> period.start.year.toString()
}

@Composable
private fun periodMeta(period: WeightPeriod, unit: WeightUnit): String {
    val count = pluralStringResource(R.plurals.weight_weighins, period.count, period.count)
    return when (period.zoom) {
        WeightZoom.Weeks -> stringResource(
            R.string.weight_meta_week,
            stringResource(R.string.weight_week_range, Format.dayMonth(period.start), Format.dayMonth(period.end)),
            count,
        )
        WeightZoom.Months -> count
        WeightZoom.Years -> stringResource(
            R.string.weight_meta_year, count, Format.weight(period.minKg, unit), Format.weight(period.maxKg, unit),
        )
    }
}

@Composable
private fun heroLabel(state: WeightUiState.Ready): String {
    val start = state.current.start
    val isNow = start == state.todayPeriodStart
    return when (state.zoom) {
        WeightZoom.Weeks -> if (isNow) stringResource(R.string.weight_hero_this_week) else stringResource(R.string.weight_hero_week_of, Format.dayMonth(start))
        WeightZoom.Months -> if (isNow) stringResource(R.string.weight_hero_this_month) else stringResource(R.string.weight_hero_month, monthName(start))
        WeightZoom.Years -> stringResource(R.string.weight_hero_year, start.year)
    }
}

/** "vs last week", "vs October", "vs 2025". */
@Composable
private fun versusLabel(state: WeightUiState.Ready): String {
    val previous = state.previous?.start ?: return ""
    return when (state.zoom) {
        WeightZoom.Weeks -> if (state.current.start == state.todayPeriodStart) {
            stringResource(R.string.weight_vs_last_week)
        } else {
            stringResource(R.string.weight_vs, stringResource(R.string.weight_week_of_short, Format.dayMonth(previous)))
        }
        WeightZoom.Months -> stringResource(R.string.weight_vs, monthName(previous))
        WeightZoom.Years -> stringResource(R.string.weight_vs, previous.year.toString())
    }
}

@Composable
private fun axisLabel(state: WeightUiState.Ready, period: WeightPeriod): String = when (state.zoom) {
    WeightZoom.Weeks -> if (period.start == state.todayPeriodStart) {
        stringResource(R.string.weight_this_week)
    } else {
        stringResource(R.string.weight_week_of_short, Format.dayMonth(period.start))
    }
    WeightZoom.Months -> monthYear(period.start)
    WeightZoom.Years -> period.start.year.toString()
}

@Composable
private fun chartDescription(state: WeightUiState.Ready): String {
    val chart = state.chart
    if (chart.isEmpty()) return ""
    return stringResource(
        when (state.zoom) {
            WeightZoom.Weeks -> R.string.weight_chart_weeks
            WeightZoom.Months -> R.string.weight_chart_months
            WeightZoom.Years -> R.string.weight_chart_years
        },
        chart.size,
        Format.weight(chart.first().averageKg, state.unit),
        Format.weight(chart.last().averageKg, state.unit),
        Format.unitLabel(state.unit),
    )
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
private fun changeDescription(changeKg: Double, unit: WeightUnit): String {
    val amount = Format.weight(abs(changeKg), unit)
    val unitLabel = Format.unitLabel(unit)
    return when {
        amount == Format.oneDecimal(0.0) -> stringResource(R.string.weight_row_same)
        changeKg < 0 -> stringResource(R.string.weight_row_down, amount, unitLabel)
        else -> stringResource(R.string.weight_row_up, amount, unitLabel)
    }
}

/** "today, 7:12 AM", "yesterday" or "Tue, 24 Nov"; time only when logged on that day. */
@Composable
private fun loggedLabel(entry: WeighIn, today: LocalDate): String {
    val day = when (entry.date) {
        today -> stringResource(R.string.weight_today_lower)
        today.minusDays(1) -> stringResource(R.string.weight_yesterday_lower)
        else -> Format.shortDate(entry.date)
    }
    val logged = entry.loggedAt.atZone(ZoneId.systemDefault())
    return if (logged.toLocalDate() == entry.date) {
        stringResource(R.string.weight_day_time, day, Format.time(logged.toLocalTime()))
    } else {
        day
    }
}
