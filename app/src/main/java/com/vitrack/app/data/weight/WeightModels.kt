package com.vitrack.app.data.weight

import java.io.File
import java.time.Instant
import java.time.LocalDate

/** One weigh-in per day. Weight is always kept in kg. */
data class WeighIn(
    val date: LocalDate,
    val weightKg: Double,
    val loggedAt: Instant,
    val photos: List<WeighInPhoto>,
)

data class WeighInPhoto(
    val id: Long,
    val file: File,
)

/**
 * A save from the editor.
 * [originalDate] is the entry being edited, if any; when it differs from
 * [date] the entry moves. Photos listed in [removePhotoIds] are deleted;
 * [keepPhotoIds] move with the entry; [newPhotoUris] are imported.
 */
data class WeighInDraft(
    val originalDate: LocalDate?,
    val date: LocalDate,
    val weightKg: Double,
    val keepPhotoIds: List<Long>,
    val removePhotoIds: List<Long>,
    val newPhotoUris: List<String>,
)

const val MAX_PHOTOS = 6
const val MIN_WEIGHT_KG = 20.0
const val MAX_WEIGHT_KG = 400.0
