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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitrack.app.R
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.MAX_PHOTOS
import com.vitrack.app.data.weight.MAX_WEIGHT_KG
import com.vitrack.app.data.weight.MIN_WEIGHT_KG
import com.vitrack.app.ui.common.ConnectedChoiceRow
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.theme.NumberStyles
import com.vitrack.app.ui.theme.Spacing
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
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xxl)
                .padding(bottom = Spacing.l),
        ) {
            Text(
                stringResource(if (state.isEditing) R.string.weighin_title_edit else R.string.weighin_title_new),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
            )
            WeightField(state, viewModel::onWeightChange, Modifier.padding(top = Spacing.xl))
            ConnectedChoiceRow(
                options = WeightUnit.entries,
                selected = state.unit,
                onSelect = viewModel::onUnitChange,
                label = { Format.unitLabel(it) },
                modifier = Modifier.width(180.dp).padding(top = Spacing.l),
            )
            DateField(state, onClick = { showDatePicker = true }, modifier = Modifier.padding(top = Spacing.xl))
            Text(
                state.replaces?.let {
                    stringResource(
                        R.string.weighin_replaces,
                        Format.weight(it.weightKg, state.unit), Format.unitLabel(state.unit), Format.shortDate(it.date),
                    )
                } ?: stringResource(R.string.weighin_one_per_day),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.replaces != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.l, top = 6.dp, end = Spacing.l),
            )
            Text(
                stringResource(R.string.weighin_photos),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
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
                modifier = Modifier.padding(start = Spacing.l, top = Spacing.s, end = Spacing.l),
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isEditing) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = !state.isSaving,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.weighin_delete)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::close, modifier = Modifier.height(48.dp)) {
                    Text(stringResource(R.string.weighin_cancel))
                }
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier.height(48.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) { Text(stringResource(R.string.weighin_save), modifier = Modifier.padding(horizontal = Spacing.s)) }
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

@Composable
private fun WeightField(state: WeighInEditorState, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val unitLabel = Format.unitLabel(state.unit)
    val showError = state.weightText.isNotBlank() && !state.isWeightValid
    OutlinedTextField(
        value = state.weightText,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.weighin_weight)) },
        textStyle = NumberStyles.Hero.copy(fontSize = 52.sp, lineHeight = 60.sp, color = MaterialTheme.colorScheme.onSurface),
        suffix = { Text(unitLabel, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        isError = showError,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        supportingText = {
            when {
                showError -> Text(
                    stringResource(
                        R.string.weighin_invalid,
                        // Whole numbers inside the limits: 20–400 kg, 45–881 lb.
                        ceil(state.unit.fromKg(MIN_WEIGHT_KG)).toInt().toString(),
                        floor(state.unit.fromKg(MAX_WEIGHT_KG)).toInt().toString(),
                        unitLabel,
                    ),
                )
                state.previous != null -> Text(
                    stringResource(
                        R.string.weighin_last,
                        Format.weight(state.previous.weightKg, state.unit), unitLabel, Format.shortDate(state.previous.date),
                    ),
                )
            }
        },
    )
}

@Composable
private fun DateField(state: WeighInEditorState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (state.date == state.today) {
        stringResource(R.string.weight_day_time, stringResource(R.string.weight_today), Format.dayMonth(state.date))
    } else {
        Format.shortDate(state.date)
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.weighin_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    val tiles = state.photos.mapIndexed { i, p -> Tile.Photo(p, i) } +
        if (state.remainingPhotoSlots > 0) listOf(Tile.Camera, Tile.Gallery) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { tile ->
                    val cell = Modifier.weight(1f).aspectRatio(3f / 4f)
                    when (tile) {
                        is Tile.Photo -> PhotoTile(tile.photo, tile.index, onRemove, cell)
                        Tile.Camera -> ActionTile(R.drawable.ic_camera, stringResource(R.string.weighin_camera), onCamera, cell)
                        Tile.Gallery -> ActionTile(R.drawable.ic_photo, stringResource(R.string.weighin_gallery), onGallery, cell)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: EditorPhoto, index: Int, onRemove: (String) -> Unit, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
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
private fun ActionTile(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(painterResource(icon), contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
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
