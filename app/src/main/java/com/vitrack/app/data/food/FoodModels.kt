package com.vitrack.app.data.food

import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime

/** Fixed labels (spec D12): one or none per photo. */
enum class FoodLabel { Breakfast, Lunch, Dinner, Snack }

/** One food photo. The day's intake is all its photos together; meal times aren't needed. */
data class FoodPhoto(
    val id: Long,
    val takenAt: LocalDateTime,
    val file: File,
    val label: FoodLabel?,
    val note: String?,
)

/** A label to start with, from when the photo was taken. The person can change or clear it. */
fun suggestLabel(time: LocalTime): FoodLabel = when (time.hour) {
    in 4..10 -> FoodLabel.Breakfast
    in 11..14 -> FoodLabel.Lunch
    in 17..21 -> FoodLabel.Dinner
    else -> FoodLabel.Snack
}

const val MAX_FOOD_NOTE = 200
