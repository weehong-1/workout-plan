package com.vitrack.app.data.weight

import com.vitrack.app.data.weight.db.WeightDao
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Weigh-ins on the phone only, until a backend is chosen. */
interface WeightRepository {
    /** All weigh-ins, newest first. */
    fun observeWeighIns(): Flow<List<WeighIn>>

    suspend fun save(draft: WeighInDraft)

    suspend fun delete(date: LocalDate)
}

@Singleton
class RoomWeightRepository @Inject constructor(
    private val dao: WeightDao,
    private val photos: PhotoStore,
    private val clock: Clock,
) : WeightRepository {

    override fun observeWeighIns(): Flow<List<WeighIn>> =
        combine(dao.observeWeighIns(), dao.observePhotos()) { weighIns, photoRows ->
            val byDay = photoRows.groupBy { it.epochDay }
            weighIns.map { row ->
                WeighIn(
                    date = LocalDate.ofEpochDay(row.epochDay),
                    weightKg = row.weightKg,
                    loggedAt = Instant.ofEpochMilli(row.loggedAtMillis),
                    photos = byDay[row.epochDay].orEmpty().map { WeighInPhoto(it.id, photos.file(it.fileName)) },
                )
            }
        }

    override suspend fun save(draft: WeighInDraft) {
        // Import first: if a photo fails to copy, nothing is written.
        val imported = draft.newPhotoUris.map { photos.import(it) }
        val removedFiles = try {
            dao.save(
                originalDay = draft.originalDate?.toEpochDay(),
                day = draft.date.toEpochDay(),
                weightKg = draft.weightKg,
                loggedAtMillis = clock.millis(),
                keepPhotoIds = draft.keepPhotoIds,
                removePhotoIds = draft.removePhotoIds,
                newPhotoFiles = imported,
            )
        } catch (e: Exception) {
            photos.delete(imported)
            throw e
        }
        photos.delete(removedFiles)
    }

    override suspend fun delete(date: LocalDate) {
        photos.delete(dao.delete(date.toEpochDay()))
    }
}
