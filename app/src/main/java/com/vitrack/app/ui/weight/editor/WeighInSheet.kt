package com.vitrack.app.ui.weight.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitrack.app.R
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.MAX_PHOTOS
import com.vitrack.app.data.weight.MAX_WEIGHT_KG
import com.vitrack.app.data.weight.MIN_WEIGHT_KG
import com.vitrack.app.ui.common.ChoiceDefaults
import com.vitrack.app.ui.common.ConnectedChoiceRow
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.theme.NumberStyles
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeighInSheet(
    state: WeighInEditorState,
    viewModel: WeighInEditorViewModel,
) {
    val weight = VitrackTheme.metrics.weight
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        pendingCapture?.let { if (saved) viewModel.addPhotos(listOf(it)) }
        pendingCapture = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        viewModel.addPhotos(uris.map(Uri::toString))
    }

    ModalBottomSheet(
        onDismissRequest = viewModel::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (state.isEditing) R.string.weighin_title_edit else R.string.weighin_title_new),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs).semantics { heading() },
                )
                if (state.isEditing) {
                    IconButton(onClick = { confirmDelete = true }, enabled = !state.isSaving) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.weighin_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            WeightHero(state, viewModel, Modifier.padding(top = Spacing.l))
            DateRow(state, onClick = { showDatePicker = true }, modifier = Modifier.padding(top = Spacing.m))
            state.replaces?.let {
                Text(
                    stringResource(
                        R.string.weighin_replaces,
                        Format.weight(it.weightKg, state.unit), Format.unitLabel(state.unit), Format.shortDate(it.date),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = weight.text,
                    modifier = Modifier.padding(start = Spacing.xs, top = 6.dp, end = Spacing.xs),
                )
            }
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.weighin_photos_title))
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(" " + stringResource(R.string.weighin_optional))
                    }
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xl, bottom = 10.dp),
            )
            PhotoGrid(
                state = state,
                onRemove = viewModel::removePhoto,
                onCamera = {
                    val uri = viewModel.newCaptureUri()
                    pendingCapture = uri
                    camera.launch(Uri.parse(uri))
                },
                onGallery = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            Text(
                stringResource(R.string.weighin_photos_note, MAX_PHOTOS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs, top = Spacing.s, end = Spacing.xs),
            )
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.weighin_save_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.m),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Button(
                    onClick = viewModel::close,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text(stringResource(R.string.weighin_cancel), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) }
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier.weight(2f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = weight.strong, contentColor = weight.onStrong),
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.weighin_save),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        WeighInDatePicker(
            selected = state.date,
            today = state.today,
            onPick = { viewModel.onDateChange(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.weighin_delete_title)) },
            text = { Text(stringResource(R.string.weighin_delete_body, Format.shortDate(state.originalDate ?: state.date))) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.weighin_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.weighin_cancel)) }
            },
        )
    }
}

/** The weight on the weight colour: −/+ steppers, the number to type into, and the unit. */
@Composable
private fun WeightHero(state: WeighInEditorState, viewModel: WeighInEditorViewModel, modifier: Modifier = Modifier) {
    val weight = VitrackTheme.metrics.weight
    val unitLabel = Format.unitLabel(state.unit)
    val showError = state.weightText.isNotBlank() && !state.isWeightValid
    val fieldLabel = stringResource(R.string.weighin_weight)
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = weight.hero) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fieldLabel, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = weight.text)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StepButton(R.drawable.ic_minus, stringResource(R.string.weighin_decrease, unitLabel)) { viewModel.nudge(-1) }
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicTextField(
                        value = state.weightText,
                        onValueChange = viewModel::onWeightChange,
                        singleLine = true,
                        textStyle = NumberStyles.Hero.copy(
                            fontSize = 60.sp,
                            lineHeight = 68.sp,
                            letterSpacing = (-1).sp,
                            textAlign = TextAlign.Center,
                            color = if (showError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(weight.graphic),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        modifier = Modifier.width(168.dp).semantics { contentDescription = "$fieldLabel, $unitLabel" },
                    )
                    Text(
                        unitLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                StepButton(R.drawable.ic_add, stringResource(R.string.weighin_increase, unitLabel)) { viewModel.nudge(1) }
            }
            Text(
                when {
                    showError -> stringResource(
                        R.string.weighin_invalid,
                        // Whole numbers inside the limits: 20–400 kg, 45–881 lb.
                        ceil(state.unit.fromKg(MIN_WEIGHT_KG)).toInt().toString(),
                        floor(state.unit.fromKg(MAX_WEIGHT_KG)).toInt().toString(),
                        unitLabel,
                    )
                    state.previous != null -> stringResource(
                        R.string.weighin_last_tap,
                        Format.weight(state.previous.weightKg, state.unit), unitLabel, Format.shortDate(state.previous.date),
                    )
                    else -> stringResource(R.string.weighin_tap_to_type)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = if (showError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            ConnectedChoiceRow(
                options = WeightUnit.entries,
                selected = state.unit,
                onSelect = viewModel::onUnitChange,
                label = { Format.unitLabel(it) },
                colors = ChoiceDefaults.metric(weight, unchecked = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.width(180.dp).padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun StepButton(icon: Int, description: String, onClick: () -> Unit) {
    val weight = VitrackTheme.metrics.weight
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = weight.badge, contentColor = weight.text),
    ) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(26.dp))
    }
}

/** The date as an outlined chip, with the one-per-day rule beside it. */
@Composable
private fun DateRow(state: WeighInEditorState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val weight = VitrackTheme.metrics.weight
    val label = if (state.date == state.today) {
        stringResource(R.string.weight_day_time, stringResource(R.string.weight_today), Format.dayMonth(state.date))
    } else {
        Format.shortDate(state.date)
    }
    val description = stringResource(R.string.weighin_date_description, label)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.height(40.dp).semantics { contentDescription = description },
        ) {
            Row(
                modifier = Modifier.padding(start = Spacing.m, end = Spacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, tint = weight.text, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }
        }
        Text(
            stringResource(R.string.weighin_one_per_day),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

private sealed interface Tile {
    data class Photo(val photo: EditorPhoto, val index: Int) : Tile
    data object Camera : Tile
    data object Gallery : Tile
}

@Composable
private fun PhotoGrid(
    state: WeighInEditorState,
    onRemove: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    val weight = VitrackTheme.metrics.weight
    val tiles = state.photos.mapIndexed { i, p -> Tile.Photo(p, i) } +
        if (state.remainingPhotoSlots > 0) listOf(Tile.Camera, Tile.Gallery) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { tile ->
                    val cell = Modifier.weight(1f).aspectRatio(3f / 4f)
                    when (tile) {
                        is Tile.Photo -> PhotoTile(tile.photo, tile.index, onRemove, cell)
                        Tile.Camera -> ActionTile(
                            R.drawable.ic_camera, stringResource(R.string.weighin_camera), onCamera, cell,
                            container = weight.strong, content = weight.onStrong,
                        )
                        Tile.Gallery -> ActionTile(
                            R.drawable.ic_photo, stringResource(R.string.weighin_gallery), onGallery, cell,
                            container = weight.badge, content = weight.text,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: EditorPhoto, index: Int, onRemove: (String) -> Unit, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AsyncImage(
            model = when (photo) {
                is EditorPhoto.Saved -> photo.photo.file
                is EditorPhoto.New -> photo.uri
            },
            contentDescription = stringResource(R.string.weighin_photo, index + 1),
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        FilledTonalIconButton(
            onClick = { onRemove(photo.key) },
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).size(36.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
            ),
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.weighin_remove_photo), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ActionTile(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = container,
        contentColor = content,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterVertically),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeighInDatePicker(
    selected: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The date picker works in UTC midnight millis.
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.weighin_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.weighin_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}
