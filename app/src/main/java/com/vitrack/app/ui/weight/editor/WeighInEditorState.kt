package com.vitrack.app.ui.weight.editor

import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.MAX_PHOTOS
import com.vitrack.app.data.weight.MAX_WEIGHT_KG
import com.vitrack.app.data.weight.MIN_WEIGHT_KG
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.data.weight.WeighInPhoto
import java.time.LocalDate

/** A photo in the editor: already saved, or picked and not yet imported. */
sealed interface EditorPhoto {
    val key: String

    data class Saved(val photo: WeighInPhoto) : EditorPhoto {
        override val key get() = "saved-${photo.id}"
    }

    data class New(val uri: String) : EditorPhoto {
        override val key get() = "new-$uri"
    }
}

data class WeighInEditorState(
    /** The day of the entry being edited; null when logging a new one. */
    val originalDate: LocalDate?,
    val date: LocalDate,
    val today: LocalDate,
    val weightText: String,
    val unit: WeightUnit,
    val photos: List<EditorPhoto>,
    val removedPhotoIds: List<Long> = emptyList(),
    /** The latest weigh-in before [date], shown as "Last: …". */
    val previous: WeighIn? = null,
    /** Another entry already on [date] that saving will update. */
    val replaces: WeighIn? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val isEditing: Boolean get() = originalDate != null

    /** The typed weight in kg, or null when it can't be read. */
    val weightKg: Double? get() = parseWeight(weightText)?.let(unit::toKg)

    val isWeightValid: Boolean get() = weightKg?.let { it in MIN_WEIGHT_KG..MAX_WEIGHT_KG } == true

    val canSave: Boolean get() = isWeightValid && !isSaving

    val remainingPhotoSlots: Int get() = (MAX_PHOTOS - photos.size).coerceAtLeast(0)
}

/** Accepts "78.4" and "78,4". */
internal fun parseWeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Keeps digits and one decimal separator with at most one decimal. */
internal fun filterWeightInput(text: String): String? {
    val ok = Regex("""^\d{0,3}([.,]\d?)?$""")
    return if (ok.matches(text)) text else null
}
