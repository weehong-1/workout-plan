package com.vitrack.app.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.vitrack.app.R
import com.vitrack.app.data.food.MAX_FOOD_NOTE
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme
import java.time.LocalDate
import java.time.LocalDateTime

/** After taking or picking photos: an optional label (suggested from the time) and note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(draft: AddFoodDraft, today: LocalDate, viewModel: FoodViewModel) {
    val food = VitrackTheme.metrics.food
    ModalBottomSheet(
        onDismissRequest = viewModel::cancelDraft,
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
            Text(
                pluralStringResource(R.plurals.food_add_title, draft.photos.size, draft.photos.size),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.xs).semantics { heading() },
            )
            Text(
                takenLabel(draft.photos.first().takenAt, today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(top = Spacing.l),
            ) {
                items(draft.photos, key = { it.uri }) { photo ->
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = stringResource(R.string.food_photo_at, Format.time(photo.takenAt.toLocalTime())),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(if (draft.photos.size == 1) 160.dp else 112.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
            Text(
                stringResource(R.string.food_label_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xl, bottom = Spacing.s),
            )
            LabelChips(selected = draft.label, onSelect = viewModel::setDraftLabel, includeAll = false)
            OutlinedTextField(
                value = draft.note,
                onValueChange = viewModel::setDraftNote,
                label = { Text(stringResource(R.string.food_note_optional)) },
                supportingText = { Text("${draft.note.length} / $MAX_FOOD_NOTE") },
                maxLines = 3,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
            )
            if (draft.saveFailed) {
                Text(
                    stringResource(R.string.food_save_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xl), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Button(
                    onClick = viewModel::cancelDraft,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text(stringResource(R.string.weighin_cancel), style = MaterialTheme.typography.titleMedium) }
                Button(
                    onClick = viewModel::saveDraft,
                    enabled = !draft.isSaving,
                    modifier = Modifier.weight(2f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = food.strong, contentColor = food.onStrong),
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.weighin_save),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
        }
    }
}

/** A photo full screen: change its label, add a note, or delete it. */
@Composable
fun FoodPhotoDetail(detail: FoodDetail, today: LocalDate, viewModel: FoodViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = viewModel::closeDetail,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = detail.photo.file,
                contentDescription = detail.photo.label?.let { stringResource(R.string.food_photo_labelled, labelName(it), Format.time(detail.photo.takenAt.toLocalTime())) }
                    ?: stringResource(R.string.food_photo_at, Format.time(detail.photo.takenAt.toLocalTime())),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::closeDetail) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back), tint = Color.White)
                }
                Text(
                    takenLabel(detail.photo.takenAt, today),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.weighin_delete), tint = Color.White)
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(Spacing.xl)) {
                    LabelChips(selected = detail.label, onSelect = viewModel::setDetailLabel, includeAll = false)
                    OutlinedTextField(
                        value = detail.note,
                        onValueChange = viewModel::setDetailNote,
                        label = { Text(stringResource(R.string.food_note_optional)) },
                        maxLines = 3,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.food_delete_title)) },
            text = { Text(stringResource(R.string.food_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.deletePhoto() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.weighin_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.weighin_cancel)) } },
        )
    }
}

/** "Today, 7:45 PM" or "Tue, 24 Nov, 7:45 PM". */
@Composable
private fun takenLabel(at: LocalDateTime, today: LocalDate): String {
    val day = if (at.toLocalDate() == today) stringResource(R.string.weight_today) else Format.shortDate(at.toLocalDate())
    return stringResource(R.string.weight_day_time, day, Format.time(at.toLocalTime()))
}
