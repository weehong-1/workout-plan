package com.vitrack.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitrack.app.R
import com.vitrack.app.data.activity.MAX_STRAIN
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.share.ShareLink
import com.vitrack.app.data.share.SharePeriod
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.ui.common.AccountAvatar
import com.vitrack.app.ui.common.ConnectedChoiceRow
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.common.MetricCard
import com.vitrack.app.ui.common.MetricIconBadge
import com.vitrack.app.ui.theme.MetricColor
import com.vitrack.app.ui.theme.NumberStyles
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun ReviewRoute(
    onShare: (SharePeriod) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewScreen(
        state = state,
        onShowYear = viewModel::showYear,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onShare = { onShare(state.period) },
        onRevoke = viewModel::revoke,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onShowYear: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShare: () -> Unit,
    onRevoke: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRevoke by remember { mutableStateOf<ShareLink?>(null) }
    confirmRevoke?.let { link ->
        AlertDialog(
            onDismissRequest = { confirmRevoke = null },
            title = { Text(stringResource(R.string.review_revoke_title, link.trainerName)) },
            text = { Text(stringResource(R.string.review_revoke_body)) },
            confirmButton = {
                TextButton(onClick = { onRevoke(link.id); confirmRevoke = null }) { Text(stringResource(R.string.review_revoke)) }
            },
            dismissButton = { TextButton(onClick = { confirmRevoke = null }) { Text(stringResource(R.string.share_cancel)) } },
        )
    }
    Box(modifier.fillMaxSize().statusBarsPadding()) {
        if (state.isLoading) {
            ContainedLoadingIndicator(Modifier.align(Alignment.Center))
            return@Box
        }
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item { Header(state.displayName) }
            item { PeriodBar(state, onShowYear, onPrevious, onNext) }
            item { ActivityTiles(state.activity, Modifier.padding(top = 6.dp)) }
            item {
                Text(
                    stringResource(R.string.review_from_whoop),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
            item { WeightCard(state.weight, state.unit) }
            item { FoodCard(state.food, state.isYear) }
            item {
                Button(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(64.dp),
                    shape = RoundedCornerShape(32.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_link), contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(
                        stringResource(R.string.review_share),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.review_active_links),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.m).semantics { heading() },
                )
            }
            if (state.links.isEmpty()) {
                item {
                    Text(
                        stringResource(
                            when {
                                !state.sharingAvailable -> R.string.review_links_not_set_up
                                !state.isSignedIn -> R.string.review_links_sign_in
                                else -> R.string.review_no_links
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                }
            } else {
                items(state.links, key = { it.id }) { link -> LinkRow(link, onRevoke = { confirmRevoke = link }) }
            }
        }
    }
}

@Composable
private fun Header(displayName: String?) {
    Column {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.s), horizontalArrangement = Arrangement.End) {
            AccountAvatar(displayName)
        }
        Text(
            stringResource(R.string.tab_review),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.s).semantics { heading() },
        )
    }
}

@Composable
internal fun periodLabel(period: SharePeriod): String = when (period) {
    is SharePeriod.Month -> period.month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
    is SharePeriod.Year -> period.year.toString()
}

@Composable
private fun PeriodBar(state: ReviewUiState, onShowYear: (Boolean) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ConnectedChoiceRow(
            options = listOf(false, true),
            selected = state.isYear,
            onSelect = onShowYear,
            label = { stringResource(if (it) R.string.review_year else R.string.review_month) },
            modifier = Modifier.width(168.dp),
        )
        Spacer(Modifier.weight(1f))
        NavButton(R.drawable.ic_chevron_left, stringResource(if (state.isYear) R.string.review_previous_year else R.string.review_previous_month), true, onPrevious)
        Text(periodLabel(state.period), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        NavButton(R.drawable.ic_chevron_right, stringResource(if (state.isYear) R.string.review_next_year else R.string.review_next_month), state.canGoForward, onNext)
    }
}

@Composable
private fun NavButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ActivityTiles(activity: ActivityAverages?, modifier: Modifier = Modifier) {
    val metrics = VitrackTheme.metrics
    val dash = "—"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Tile(
                metric = metrics.steps, icon = R.drawable.ic_nav_activity, label = stringResource(R.string.metric_steps),
                value = activity?.let { Format.grouped(it.steps) } ?: dash, unit = stringResource(R.string.review_per_day),
                progress = (activity?.steps ?: 0) / 10_000f, modifier = Modifier.weight(1f),
            )
            Tile(
                metric = metrics.strain, icon = R.drawable.ic_pulse, label = stringResource(R.string.metric_strain),
                value = activity?.let { Format.oneDecimal(it.strain) } ?: dash, unit = stringResource(R.string.strain_of_max),
                progress = ((activity?.strain ?: 0.0) / MAX_STRAIN).toFloat(), modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Tile(
                metric = metrics.recovery, icon = R.drawable.ic_heart, label = stringResource(R.string.metric_recovery),
                value = activity?.recovery?.toString() ?: dash, unit = "%",
                progress = (activity?.recovery ?: 0) / 100f, modifier = Modifier.weight(1f),
            )
            Tile(
                metric = metrics.sleep, icon = R.drawable.ic_sleep, label = stringResource(R.string.metric_sleep),
                value = activity?.let { stringResource(R.string.review_sleep_value, it.sleepMinutes / 60, it.sleepMinutes % 60) } ?: dash,
                unit = "",
                progress = (activity?.sleepMinutes ?: 0) / (9 * 60f), modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Tile(metric: MetricColor, icon: Int, label: String, value: String, unit: String, progress: Float, modifier: Modifier) {
    val stroke = with(LocalDensity.current) { Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round) }
    val description = "$label $value $unit".trim()
    MetricCard(
        modifier = modifier.height(150.dp).clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(28.dp),
        contentPadding = 0.dp,
        color = metric.hero,
    ) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                MetricIconBadge(icon, tint = metric.text, container = metric.badge, size = 30.dp, iconSize = 16.dp)
                Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = metric.text)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = NumberStyles.Large.copy(letterSpacing = (-0.5).sp))
                if (unit.isNotEmpty()) {
                    Text(
                        " $unit",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            LinearWavyProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = metric.graphic,
                trackColor = metric.badge,
                stroke = stroke,
                trackStroke = stroke,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun WeightCard(weight: WeightSummary?, unit: WeightUnit) {
    val metric = VitrackTheme.metrics.weight
    val unitLabel = Format.unitLabel(unit)
    MetricCard(shape = RoundedCornerShape(32.dp), contentPadding = 0.dp, color = metric.hero) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricIconBadge(R.drawable.ic_nav_weight, tint = metric.text, container = metric.badge, size = 36.dp, iconSize = 18.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_weight), style = MaterialTheme.typography.titleMedium, color = metric.text)
                    Text(
                        weight?.let {
                            stringResource(
                                R.string.review_weight_meta,
                                Format.weight(it.first.weightKg, unit), Format.weight(it.last.weightKg, unit), unitLabel,
                                pluralStringResource(R.plurals.weight_weighins, it.count, it.count),
                            )
                        } ?: stringResource(R.string.review_no_weighins),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                weight?.takeIf { it.count > 1 }?.let {
                    val amount = Format.weight(abs(it.changeKg), unit)
                    val sign = when {
                        amount == Format.oneDecimal(0.0) -> ""
                        it.changeKg < 0 -> "−"
                        else -> "+"
                    }
                    Text(
                        "$sign$amount $unitLabel",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = metric.onStrong,
                        modifier = Modifier.background(metric.strong, CircleShape).padding(horizontal = Spacing.m, vertical = 6.dp),
                    )
                }
            }
            val photos = listOfNotNull(weight?.firstPhoto, weight?.lastPhoto)
            if (photos.isNotEmpty()) {
                Row(Modifier.padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    photos.forEachIndexed { i, entry ->
                        val shape = when {
                            photos.size == 1 -> RoundedCornerShape(24.dp)
                            i == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 24.dp)
                            else -> RoundedCornerShape(topStart = 10.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 10.dp)
                        }
                        ProgressPhoto(entry, unit, shape, Modifier.weight(1f))
                    }
                    if (photos.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProgressPhoto(entry: WeighIn, unit: WeightUnit, shape: RoundedCornerShape, modifier: Modifier) {
    val label = "${Format.dayMonth(entry.date)} · ${Format.weight(entry.weightKg, unit)}"
    Box(modifier.aspectRatio(4f / 3f).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AsyncImage(
            model = entry.photos.first().file,
            contentDescription = stringResource(R.string.review_progress_photo, label),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.s)
                .background(Color.Black.copy(alpha = 0.62f), CircleShape)
                .padding(horizontal = Spacing.s, vertical = 3.dp),
        )
    }
}

@Composable
private fun FoodCard(food: FoodSummary, isYear: Boolean) {
    val metric = VitrackTheme.metrics.food
    val description = stringResource(R.string.review_food_description, food.daysWithPhotos, food.daysSoFar)
    MetricCard(shape = RoundedCornerShape(32.dp), contentPadding = 0.dp, color = metric.hero) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricIconBadge(R.drawable.ic_nav_food, tint = metric.text, container = metric.badge, size = 36.dp, iconSize = 18.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_food), style = MaterialTheme.typography.titleMedium, color = metric.text)
                    Text(
                        stringResource(R.string.review_days_with_photos),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(food.daysWithPhotos.toString(), style = NumberStyles.Small)
                    Text(
                        " / ${food.daysSoFar}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            // A dot per day (month) or per month (year), filled by how many days have photos.
            val columns = if (isYear) 12 else 13
            Column(
                Modifier.padding(top = Spacing.m).clearAndSetSemantics { contentDescription = description },
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                food.marks.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { share ->
                            Box(
                                Modifier.weight(1f).aspectRatio(1f)
                                    .background(lerp(metric.badge, metric.graphic, share), CircleShape),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(link: ShareLink, onRevoke: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val expiry = when {
        link.isExpired -> stringResource(R.string.review_link_expired)
        link.expiresOn != null -> stringResource(R.string.review_link_expires, Format.dayMonth(link.expiresOn))
        else -> stringResource(R.string.review_link_no_expiry)
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 68.dp).padding(start = Spacing.m, end = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    link.trainerName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(link.trainerName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${periodLabel(link.period)} · $expiry",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The token lives only on the phone that made the link, so only there can it be copied again.
            if (link.url != null && !link.isExpired) {
                FilledTonalIconButton(onClick = { clipboard.setText(AnnotatedString(link.url)) }, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_link), contentDescription = stringResource(R.string.review_copy_link, link.trainerName), modifier = Modifier.size(20.dp))
                }
            }
            Button(
                onClick = onRevoke,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.height(40.dp),
            ) { Text(stringResource(R.string.review_revoke), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
        }
    }
}
