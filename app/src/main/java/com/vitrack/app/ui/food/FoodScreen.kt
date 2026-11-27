package com.vitrack.app.ui.food

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitrack.app.R
import com.vitrack.app.data.food.FoodLabel
import com.vitrack.app.data.food.FoodPhoto
import com.vitrack.app.ui.common.AccountAvatar
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.common.MetricCard
import com.vitrack.app.ui.common.MetricIconBadge
import com.vitrack.app.ui.common.rememberReducedMotion
import com.vitrack.app.ui.theme.NumberStyles
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.sin

@Composable
fun FoodRoute(
    modifier: Modifier = Modifier,
    viewModel: FoodViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        pendingCapture?.let { if (saved) viewModel.onCaptured(it) }
        pendingCapture = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        viewModel.onPicked(uris.map(Uri::toString))
    }

    FoodScreen(
        state = state,
        onSelectDay = viewModel::selectDay,
        onSelectFilter = viewModel::selectFilter,
        onOpenPhoto = viewModel::openPhoto,
        onCamera = {
            val uri = viewModel.newCaptureUri()
            pendingCapture = uri
            camera.launch(Uri.parse(uri))
        },
        onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        modifier = modifier,
    )
    draft?.let { AddFoodSheet(it, state.today, viewModel) }
    detail?.let { FoodPhotoDetail(it, state.today, viewModel) }
}

private const val MAX_PICK = 10

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FoodScreen(
    state: FoodUiState,
    onSelectDay: (LocalDate) -> Unit,
    onSelectFilter: (FoodLabel?) -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val food = VitrackTheme.metrics.food
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Box(modifier.fillMaxSize().statusBarsPadding()) {
        if (state.isLoading) {
            ContainedLoadingIndicator(Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 176.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Header(state.displayName) }
                item { DayStrip(state, onSelectDay) }
                item { DayCard(state, Modifier.padding(top = 6.dp)) }
                item { LabelChips(state.filter, onSelectFilter, Modifier.padding(vertical = 8.dp)) }
                if (state.shown.isEmpty()) {
                    item { EmptyDay(state) }
                } else {
                    // Rows of three; the outer corners of each block of six are rounder.
                    val rows = state.shown.withIndex().chunked(3)
                    items(rows.size, key = { i -> rows[i].first().value.id }) { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.animateItem()) {
                            rows[r].forEach { (index, photo) ->
                                PhotoTile(photo, index, onClick = { onOpenPhoto(photo.id) }, modifier = Modifier.weight(1f))
                            }
                            repeat(3 - rows[r].size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                SmallFloatingActionButton(
                    onClick = onGallery,
                    containerColor = food.hero,
                    contentColor = food.text,
                ) {
                    Icon(painterResource(R.drawable.ic_photo), contentDescription = stringResource(R.string.food_from_gallery))
                }
                ExtendedFloatingActionButton(
                    onClick = onCamera,
                    expanded = fabExpanded,
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_camera),
                            contentDescription = if (fabExpanded) null else stringResource(R.string.food_add_photo),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    text = { Text(stringResource(R.string.food_add_photo), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                    containerColor = food.strong,
                    contentColor = food.onStrong,
                    shape = RoundedCornerShape(20.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    modifier = Modifier.height(64.dp),
                )
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
            stringResource(R.string.tab_food),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.m).semantics { heading() },
        )
    }
}

/** Seven days fit across; older days scroll in from the left. */
@Composable
private fun DayStrip(state: FoodUiState, onSelectDay: (LocalDate) -> Unit) {
    val food = VitrackTheme.metrics.food
    // Newest first in a reversed row: today is anchored to the right edge.
    val newestFirst = remember(state.days) { state.days.asReversed() }
    val stripState = rememberLazyListState()
    // Keep the selected day in view, e.g. after saving a photo from an earlier day.
    LaunchedEffect(state.selected) {
        val index = newestFirst.indexOfFirst { it.date == state.selected }
        val visible = stripState.layoutInfo.visibleItemsInfo.map { it.index }
        if (index >= 0 && index !in visible) stripState.animateScrollToItem((index - 3).coerceAtLeast(0))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - 6.dp * 6) / 7
        LazyRow(state = stripState, reverseLayout = true, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            items(newestFirst, key = { it.date.toEpochDay() }) { day ->
                val isSelected = day.date == state.selected
                val weekday = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                val description = stringResource(
                    R.string.food_day_description,
                    Format.dayHeading(day.date),
                    pluralStringResource(R.plurals.food_photos, day.count, day.count),
                )
                Surface(
                    onClick = { onSelectDay(day.date) },
                    shape = RoundedCornerShape(if (isSelected) 24.dp else 18.dp),
                    color = if (isSelected) food.strong else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (isSelected) food.onStrong else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .width(cell)
                        .height(68.dp)
                        .clearAndSetSemantics {
                            contentDescription = description
                            selected = isSelected
                            role = Role.Tab
                        },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(
                            weekday,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                            color = if (isSelected) food.onStrong else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(day.date.dayOfMonth.toString(), style = NumberStyles.Small)
                        if (!isSelected) {
                            Box(
                                Modifier.padding(top = 2.dp).size(6.dp)
                                    .background(if (day.count > 0) food.graphic else Color.Transparent, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The day, its photo count and a timeline of when each photo was taken. */
@Composable
private fun DayCard(state: FoodUiState, modifier: Modifier = Modifier) {
    val food = VitrackTheme.metrics.food
    val count = state.dayPhotos.size
    MetricCard(modifier = modifier, shape = RoundedCornerShape(36.dp), contentPadding = Spacing.xl, color = food.hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            MetricIconBadge(R.drawable.ic_nav_food, tint = food.text, container = food.badge, size = 40.dp, iconSize = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    state.selected.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = food.text,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(count.toString(), style = NumberStyles.Large)
                    Text(
                        " " + if (state.selected == state.today) {
                            pluralStringResource(R.plurals.food_photos_today, count)
                        } else {
                            pluralStringResource(R.plurals.food_photos_word, count)
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
        MealTimeline(state.dayPhotos.map { it.takenAt.toLocalTime() }, Modifier.padding(top = 10.dp))
    }
}

private const val TIMELINE_START = 6f
private const val TIMELINE_END = 22f

/**
 * A soft wavy track from 6 AM to 10 PM with a dot at each photo's time.
 * The dots pop in one after another; still with reduced motion.
 */
@Composable
private fun MealTimeline(times: List<LocalTime>, modifier: Modifier = Modifier) {
    val food = VitrackTheme.metrics.food
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)
    val reducedMotion = rememberReducedMotion()
    val pop = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(times) {
        if (reducedMotion) pop.snapTo(1f) else {
            pop.snapTo(0f)
            pop.animateTo(1f, tween(durationMillis = 700 + 90 * times.size))
        }
    }
    val description = if (times.isEmpty()) "" else stringResource(
        R.string.food_timeline_description, Format.time(times.min()), Format.time(times.max()),
    )
    Canvas(modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = description }) {
        val inset = 9.dp.toPx()
        val baseY = 26.dp.toPx()
        val amplitude = 7.dp.toPx()
        fun y(x: Float) = baseY + amplitude * sin(x / 14.dp.toPx())
        fun x(time: LocalTime): Float {
            val hours = (time.toSecondOfDay() / 3600f).coerceIn(TIMELINE_START, TIMELINE_END)
            return inset + (hours - TIMELINE_START) / (TIMELINE_END - TIMELINE_START) * (size.width - 2 * inset)
        }
        val wave = Path()
        var px = 4.dp.toPx()
        wave.moveTo(px, y(px))
        while (px < size.width - 4.dp.toPx()) {
            px += 3f
            wave.lineTo(px, y(px))
        }
        drawPath(wave, food.badge, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))

        times.sorted().forEachIndexed { i, time ->
            // Each dot starts a little after the one before.
            val start = i / (times.size + 2f)
            val t = ((pop.value - start) / (1f - start)).coerceIn(0f, 1f)
            val scale = DotPop.transform(t)
            if (scale <= 0f) return@forEachIndexed
            val cx = x(time)
            val center = Offset(cx, y(cx))
            drawCircle(food.hero, radius = (9.dp.toPx() + 1.5.dp.toPx()) * scale, center = center)
            drawCircle(food.graphic, radius = (9.dp.toPx() - 1.5.dp.toPx()) * scale, center = center)
        }

        listOf(TIMELINE_START, 14f, TIMELINE_END).forEachIndexed { i, hour ->
            val text = measurer.measure(Format.time(LocalTime.of(hour.toInt(), 0)), labelStyle)
            val tx = when (i) {
                0 -> 0f
                1 -> size.width / 2 - text.size.width / 2
                else -> size.width - text.size.width
            }
            drawText(text, topLeft = Offset(tx, size.height - text.size.height))
        }
    }
}

private val DotPop = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)

/**
 * Label choices. The first chip is null: "All" when [includeAll] (a filter),
 * otherwise "No label" (when labelling a photo).
 */
@Composable
internal fun LabelChips(
    selected: FoodLabel?,
    onSelect: (FoodLabel?) -> Unit,
    modifier: Modifier = Modifier,
    includeAll: Boolean = true,
) {
    val options: List<FoodLabel?> = listOf<FoodLabel?>(null) + FoodLabel.entries
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        options.forEach { option ->
            FoodChip(
                text = if (option == null) {
                    stringResource(if (includeAll) R.string.food_filter_all else R.string.food_no_label)
                } else {
                    labelName(option)
                },
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun FoodChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val food = VitrackTheme.metrics.food
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) food.strong else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) food.onStrong else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.height(36.dp).semantics { this.selected = selected; role = Role.RadioButton },
    ) {
        Row(
            Modifier.padding(start = if (selected) 10.dp else 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold))
        }
    }
}

@Composable
internal fun labelName(label: FoodLabel): String = stringResource(
    when (label) {
        FoodLabel.Breakfast -> R.string.food_label_breakfast
        FoodLabel.Lunch -> R.string.food_label_lunch
        FoodLabel.Dinner -> R.string.food_label_dinner
        FoodLabel.Snack -> R.string.food_label_snack
    },
)

/** Corner shapes for a block of six tiles: the block's outer corners are rounder. */
private val tileShapes = listOf(
    RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
    RoundedCornerShape(12.dp),
    RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 28.dp),
    RoundedCornerShape(12.dp),
    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
)

@Composable
private fun PhotoTile(photo: FoodPhoto, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val food = VitrackTheme.metrics.food
    val time = Format.time(photo.takenAt.toLocalTime())
    val description = photo.label?.let { stringResource(R.string.food_photo_labelled, labelName(it), time) }
        ?: stringResource(R.string.food_photo_at, time)
    Surface(
        onClick = onClick,
        shape = tileShapes[index % tileShapes.size],
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.aspectRatio(1f).semantics { contentDescription = description },
    ) {
        Box {
            AsyncImage(model = photo.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text(
                time,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.s)
                    .background(Color.Black.copy(alpha = 0.62f), CircleShape)
                    .padding(horizontal = Spacing.s, vertical = 3.dp),
            )
            photo.label?.let {
                Text(
                    labelName(it),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = food.onStrong,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.s)
                        .background(food.strong, CircleShape)
                        .padding(horizontal = Spacing.s, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyDay(state: FoodUiState) {
    val text = when {
        state.dayPhotos.isEmpty() -> stringResource(R.string.food_empty_day)
        else -> stringResource(R.string.food_empty_filter, labelName(state.filter!!))
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.food_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
