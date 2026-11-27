package com.vitrack.app.ui.food

import com.vitrack.app.data.food.FoodLabel
import com.vitrack.app.data.food.FoodPhoto
import java.time.LocalDate
import java.time.LocalDateTime

data class FoodUiState(
    val isLoading: Boolean = true,
    val displayName: String? = null,
    val today: LocalDate = LocalDate.MIN,
    val selected: LocalDate = LocalDate.MIN,
    /** Days on the strip, oldest first, ending today. */
    val days: List<FoodDay> = emptyList(),
    /** All of the selected day's photos, in the order taken. */
    val dayPhotos: List<FoodPhoto> = emptyList(),
    /** Null shows every label. */
    val filter: FoodLabel? = null,
) {
    /** The grid: the day's photos with [filter] applied. */
    val shown: List<FoodPhoto> get() = if (filter == null) dayPhotos else dayPhotos.filter { it.label == filter }
}

data class FoodDay(val date: LocalDate, val count: Int)

/** Photos just taken or picked, waiting for an optional label and note before saving. */
data class AddFoodDraft(
    val photos: List<DraftPhoto>,
    val label: FoodLabel?,
    val note: String = "",
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

data class DraftPhoto(val uri: String, val takenAt: LocalDateTime)

/** A photo open full screen, with its label and note being edited. */
data class FoodDetail(
    val photo: FoodPhoto,
    val label: FoodLabel?,
    val note: String,
)

/** How far back the strip goes. */
const val STRIP_DAYS = 90
