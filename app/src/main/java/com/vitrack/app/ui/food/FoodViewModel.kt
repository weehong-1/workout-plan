package com.vitrack.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.food.FoodLabel
import com.vitrack.app.data.food.FoodRepository
import com.vitrack.app.data.food.MAX_FOOD_NOTE
import com.vitrack.app.data.food.NewFoodPhoto
import com.vitrack.app.data.food.suggestLabel
import com.vitrack.app.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodViewModel @Inject constructor(
    private val repository: FoodRepository,
    profileRepository: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now(clock)
    private val selected = MutableStateFlow(LocalDate.now(clock))
    private val filter = MutableStateFlow<FoodLabel?>(null)

    val uiState: StateFlow<FoodUiState> = combine(
        selected,
        selected.flatMapLatest { repository.observeDay(it) },
        repository.observeCounts(today.minusDays(STRIP_DAYS - 1L)),
        filter,
        profileRepository.observeDisplayName(),
    ) { date, photos, counts, filter, name ->
        val today = today
        FoodUiState(
            isLoading = false,
            displayName = name,
            today = today,
            selected = date,
            days = (STRIP_DAYS - 1 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                FoodDay(day, counts[day] ?: 0)
            },
            // The day's list can lag one emission behind a new selection; keep only that day.
            dayPhotos = photos.filter { it.takenAt.toLocalDate() == date },
            filter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodUiState())

    private val _draft = MutableStateFlow<AddFoodDraft?>(null)
    val draft: StateFlow<AddFoodDraft?> = _draft.asStateFlow()

    private val _detail = MutableStateFlow<FoodDetail?>(null)
    val detail: StateFlow<FoodDetail?> = _detail.asStateFlow()

    fun selectDay(date: LocalDate) {
        if (date.isAfter(today)) return
        selected.value = date
    }

    fun selectFilter(label: FoodLabel?) {
        filter.value = label
    }

    fun newCaptureUri(): String = repository.newCaptureUri()

    /** A camera photo: taken now. */
    fun onCaptured(uri: String) {
        startDraft(listOf(DraftPhoto(uri, LocalDateTime.now(clock))))
    }

    /** Gallery photos: each goes on the day it was taken, or now when that's unknown. */
    fun onPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val now = LocalDateTime.now(clock)
            startDraft(uris.map { uri -> DraftPhoto(uri, repository.takenAt(uri)?.takeIf { !it.isAfter(now) } ?: now) })
        }
    }

    private fun startDraft(photos: List<DraftPhoto>) {
        _draft.value = AddFoodDraft(photos = photos, label = suggestLabel(photos.first().takenAt.toLocalTime()))
    }

    fun setDraftLabel(label: FoodLabel?) {
        _draft.update { it?.copy(label = label) }
    }

    fun setDraftNote(note: String) {
        _draft.update { it?.copy(note = note.take(MAX_FOOD_NOTE)) }
    }

    fun cancelDraft() {
        _draft.value = null
    }

    fun saveDraft() {
        val draft = _draft.value ?: return
        if (draft.isSaving) return
        _draft.value = draft.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                repository.add(draft.photos.map { NewFoodPhoto(it.uri, it.takenAt, draft.label, draft.note) })
                // Show where they went: the first photo's day, all labels.
                selected.value = draft.photos.first().takenAt.toLocalDate()
                filter.value = null
                _draft.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _draft.update { it?.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    fun openPhoto(id: Long) {
        val photo = uiState.value.dayPhotos.firstOrNull { it.id == id } ?: return
        _detail.value = FoodDetail(photo, photo.label, photo.note.orEmpty())
    }

    /** Label changes save at once. */
    fun setDetailLabel(label: FoodLabel?) {
        val detail = _detail.value ?: return
        _detail.value = detail.copy(label = label)
        viewModelScope.launch { repository.update(detail.photo.id, label, detail.note) }
    }

    fun setDetailNote(note: String) {
        _detail.update { it?.copy(note = note.take(MAX_FOOD_NOTE)) }
    }

    /** Closing saves the note if it changed. */
    fun closeDetail() {
        val detail = _detail.value ?: return
        _detail.value = null
        if (detail.note.trim() != detail.photo.note.orEmpty()) {
            viewModelScope.launch { repository.update(detail.photo.id, detail.label, detail.note) }
        }
    }

    fun deletePhoto() {
        val detail = _detail.value ?: return
        _detail.value = null
        viewModelScope.launch { repository.delete(detail.photo.id) }
    }
}
