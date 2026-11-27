package com.vitrack.app.ui.weight.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.PhotoStore
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.data.weight.WeighInDraft
import com.vitrack.app.data.weight.WeightRepository
import com.vitrack.app.ui.common.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Log weigh-in sheet. [state] is null while the sheet is closed.
 * One entry per day: saving onto a day that already has one updates it.
 */
@HiltViewModel
class WeighInEditorViewModel @Inject constructor(
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository,
    private val photoStore: PhotoStore,
    private val clock: Clock,
) : ViewModel() {

    private val weighIns: StateFlow<List<WeighIn>> = weightRepository.observeWeighIns()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val unit: StateFlow<WeightUnit> = settingsRepository.weightUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.Kg)

    private val _state = MutableStateFlow<WeighInEditorState?>(null)
    val state: StateFlow<WeighInEditorState?> = _state.asStateFlow()

    /**
     * Opens a new entry dated today, prefilled with the most recent weight.
     * If today is already logged, the sheet says saving will update it; picking
     * another date logs that day instead, leaving today's entry alone.
     */
    fun openNew() {
        val today = LocalDate.now(clock)
        val todays = weighIns.value.firstOrNull { it.date == today }
        val previous = previousBefore(today, excluding = null)
        _state.value = WeighInEditorState(
            originalDate = null,
            date = today,
            today = today,
            weightText = (todays ?: previous)?.let { Format.weight(it.weightKg, unit.value) }.orEmpty(),
            unit = unit.value,
            photos = emptyList(),
            previous = previous,
            replaces = todays,
        )
    }

    fun openExisting(date: LocalDate) {
        val entry = weighIns.value.firstOrNull { it.date == date } ?: return
        _state.value = WeighInEditorState(
            originalDate = date,
            date = date,
            today = LocalDate.now(clock),
            weightText = Format.weight(entry.weightKg, unit.value),
            unit = unit.value,
            photos = entry.photos.map { EditorPhoto.Saved(it) },
            previous = previousBefore(date, excluding = date),
        )
    }

    fun close() {
        _state.value = null
    }

    fun onWeightChange(text: String) {
        val accepted = filterWeightInput(text) ?: return
        _state.update { it?.copy(weightText = accepted, saveFailed = false) }
    }

    /** Converts the typed value and remembers the unit for next time. */
    fun onUnitChange(newUnit: WeightUnit) {
        val current = _state.value ?: return
        if (newUnit == current.unit) return
        val kg = current.weightKg
        _state.value = current.copy(
            unit = newUnit,
            weightText = kg?.let { Format.weight(it, newUnit) } ?: current.weightText,
        )
        viewModelScope.launch { settingsRepository.setWeightUnit(newUnit) }
    }

    fun onDateChange(date: LocalDate) {
        _state.update { current ->
            current?.copy(
                date = date,
                previous = previousBefore(date, excluding = current.originalDate),
                replaces = weighIns.value.firstOrNull { it.date == date && it.date != current.originalDate },
            )
        }
    }

    fun newCaptureUri(): String = photoStore.newCaptureUri()

    fun addPhotos(uris: List<String>) {
        _state.update { current ->
            current ?: return@update null
            val existing = current.photos.map { it.key }.toSet()
            val added = uris.map { EditorPhoto.New(it) }
                .distinctBy { it.key }
                .filter { it.key !in existing }
                .take(current.remainingPhotoSlots)
            current.copy(photos = current.photos + added)
        }
    }

    fun removePhoto(key: String) {
        _state.update { current ->
            current ?: return@update null
            val photo = current.photos.firstOrNull { it.key == key } ?: return@update current
            current.copy(
                photos = current.photos - photo,
                removedPhotoIds = if (photo is EditorPhoto.Saved) current.removedPhotoIds + photo.photo.id else current.removedPhotoIds,
            )
        }
    }

    fun save() {
        val current = _state.value ?: return
        val kg = current.weightKg ?: return
        if (!current.canSave) return
        _state.value = current.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                weightRepository.save(
                    WeighInDraft(
                        originalDate = current.originalDate,
                        date = current.date,
                        weightKg = kg,
                        keepPhotoIds = current.photos.filterIsInstance<EditorPhoto.Saved>().map { it.photo.id },
                        removePhotoIds = current.removedPhotoIds,
                        newPhotoUris = current.photos.filterIsInstance<EditorPhoto.New>().map { it.uri },
                    ),
                )
                _state.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it?.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    fun delete() {
        val date = _state.value?.originalDate ?: return
        _state.update { it?.copy(isSaving = true) }
        viewModelScope.launch {
            weightRepository.delete(date)
            _state.value = null
        }
    }

    private fun previousBefore(date: LocalDate, excluding: LocalDate?): WeighIn? =
        weighIns.value.firstOrNull { it.date.isBefore(date) && it.date != excluding }
}
