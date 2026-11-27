package com.vitrack.app.testing

import com.vitrack.app.data.food.FoodLabel
import com.vitrack.app.data.food.FoodPhoto
import com.vitrack.app.data.food.FoodRepository
import com.vitrack.app.data.food.NewFoodPhoto
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

fun foodPhoto(id: Long, at: LocalDateTime, label: FoodLabel? = null, note: String? = null) =
    FoodPhoto(id, at, File("food-$id.jpg"), label, note)

class FakeFoodRepository(initial: List<FoodPhoto> = emptyList()) : FoodRepository {
    val photos = MutableStateFlow(initial)
    val added = mutableListOf<NewFoodPhoto>()
    /** Taken-at times the "gallery" reports, by uri. */
    val takenAtByUri = mutableMapOf<String, LocalDateTime>()
    var failAdd = false
    private var nextId = 1000L

    override fun observeDay(date: LocalDate): Flow<List<FoodPhoto>> =
        photos.map { all -> all.filter { it.takenAt.toLocalDate() == date }.sortedBy { it.takenAt } }

    override fun observeCounts(from: LocalDate): Flow<Map<LocalDate, Int>> =
        photos.map { all -> all.filter { !it.takenAt.toLocalDate().isBefore(from) }.groupingBy { it.takenAt.toLocalDate() }.eachCount() }

    override suspend fun photosBetween(from: LocalDate, to: LocalDate): List<FoodPhoto> =
        photos.value.filter { it.takenAt.toLocalDate() in from..to }.sortedBy { it.takenAt }

    override fun newCaptureUri() = "content://test/capture.jpg"

    override suspend fun takenAt(uri: String): LocalDateTime? = takenAtByUri[uri]

    override suspend fun add(photos: List<NewFoodPhoto>) {
        if (failAdd) error("disk full")
        added += photos
        this.photos.value = this.photos.value + photos.map { foodPhoto(nextId++, it.takenAt, it.label, it.note) }
    }

    override suspend fun update(id: Long, label: FoodLabel?, note: String?) {
        photos.value = photos.value.map { if (it.id == id) it.copy(label = label, note = note?.trim()?.ifEmpty { null }) else it }
    }

    override suspend fun delete(id: Long) {
        photos.value = photos.value.filter { it.id != id }
    }
}
