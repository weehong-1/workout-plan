package com.vitrack.app.data.food

import com.vitrack.app.data.food.db.FoodDao
import com.vitrack.app.data.food.db.FoodPhotoEntity
import com.vitrack.app.data.weight.PhotoStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The photo store for food photos, separate from progress photos. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FoodPhotos

/** A photo picked or taken, about to be saved. */
data class NewFoodPhoto(
    val uri: String,
    val takenAt: LocalDateTime,
    val label: FoodLabel?,
    val note: String?,
)

/** Food photos on the phone only, until a backend is chosen (spec D8). */
interface FoodRepository {
    /** The day's photos, in the order they were taken. */
    fun observeDay(date: LocalDate): Flow<List<FoodPhoto>>

    /** Photo counts for days from [from] on; days without photos are left out. */
    fun observeCounts(from: LocalDate): Flow<Map<LocalDate, Int>>

    fun newCaptureUri(): String

    /** When a picked photo was taken, or null when unknown. */
    suspend fun takenAt(uri: String): LocalDateTime?

    suspend fun add(photos: List<NewFoodPhoto>)

    suspend fun update(id: Long, label: FoodLabel?, note: String?)

    suspend fun delete(id: Long)
}

@Singleton
class RoomFoodRepository @Inject constructor(
    private val dao: FoodDao,
    @param:FoodPhotos private val photos: PhotoStore,
    private val clock: Clock,
) : FoodRepository {

    override fun observeDay(date: LocalDate): Flow<List<FoodPhoto>> =
        dao.observeDay(date.toEpochDay()).map { rows -> rows.map { it.toModel() } }

    override fun observeCounts(from: LocalDate): Flow<Map<LocalDate, Int>> =
        dao.observeCounts(from.toEpochDay()).map { counts -> counts.associate { LocalDate.ofEpochDay(it.epochDay) to it.count } }

    override fun newCaptureUri(): String = photos.newCaptureUri()

    override suspend fun takenAt(uri: String): LocalDateTime? =
        photos.takenAtMillis(uri)?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), clock.zone) }

    override suspend fun add(photos: List<NewFoodPhoto>) {
        // Import first: if a photo fails to copy, nothing is written.
        val imported = photos.map { it to this.photos.import(it.uri) }
        try {
            dao.insert(
                imported.map { (photo, fileName) ->
                    FoodPhotoEntity(
                        epochDay = photo.takenAt.toLocalDate().toEpochDay(),
                        secondOfDay = photo.takenAt.toLocalTime().toSecondOfDay(),
                        fileName = fileName,
                        label = photo.label?.name,
                        note = photo.note?.takeIf { it.isNotBlank() },
                    )
                },
            )
        } catch (e: Exception) {
            this.photos.delete(imported.map { it.second })
            throw e
        }
    }

    override suspend fun update(id: Long, label: FoodLabel?, note: String?) {
        dao.update(id, label?.name, note?.trim()?.takeIf { it.isNotEmpty() })
    }

    override suspend fun delete(id: Long) {
        val row = dao.get(id) ?: return
        dao.delete(id)
        photos.delete(listOf(row.fileName))
    }

    private fun FoodPhotoEntity.toModel() = FoodPhoto(
        id = id,
        takenAt = LocalDate.ofEpochDay(epochDay).atTime(LocalTime.ofSecondOfDay(secondOfDay.toLong())),
        file = photos.file(fileName),
        label = label?.let { name -> FoodLabel.entries.firstOrNull { it.name == name } },
        note = note,
    )
}
