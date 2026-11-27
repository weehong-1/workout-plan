package com.vitrack.app.data.weight.db

import androidx.room.Dao
import com.vitrack.app.data.food.db.FoodDao
import com.vitrack.app.data.food.db.FoodPhotoEntity
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One row per day; the day is the key, so a second save for a day updates it. */
@Entity(tableName = "weigh_in")
data class WeighInEntity(
    @PrimaryKey val epochDay: Long,
    val weightKg: Double,
    val loggedAtMillis: Long,
)

@Entity(tableName = "weigh_in_photo", indices = [Index("epochDay")])
data class WeighInPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val fileName: String,
    val addedAtMillis: Long,
)

@Dao
abstract class WeightDao {

    @Query("SELECT * FROM weigh_in ORDER BY epochDay DESC")
    abstract fun observeWeighIns(): Flow<List<WeighInEntity>>

    @Query("SELECT * FROM weigh_in_photo ORDER BY addedAtMillis, id")
    abstract fun observePhotos(): Flow<List<WeighInPhotoEntity>>

    @Query("SELECT * FROM weigh_in_photo WHERE epochDay = :epochDay")
    abstract suspend fun photosForDay(epochDay: Long): List<WeighInPhotoEntity>

    @Query("SELECT * FROM weigh_in_photo WHERE id IN (:ids)")
    abstract suspend fun photosById(ids: List<Long>): List<WeighInPhotoEntity>

    @Upsert
    abstract suspend fun upsert(weighIn: WeighInEntity)

    @Insert
    abstract suspend fun insertPhotos(photos: List<WeighInPhotoEntity>)

    @Query("UPDATE weigh_in_photo SET epochDay = :epochDay WHERE id IN (:ids)")
    abstract suspend fun movePhotos(ids: List<Long>, epochDay: Long)

    @Query("DELETE FROM weigh_in_photo WHERE id IN (:ids)")
    abstract suspend fun deletePhotos(ids: List<Long>)

    @Query("DELETE FROM weigh_in WHERE epochDay = :epochDay")
    abstract suspend fun deleteWeighIn(epochDay: Long)

    /**
     * Applies an editor save in one transaction. Returns the file names of
     * photos whose rows were deleted, so the caller can remove the files.
     */
    @Transaction
    open suspend fun save(
        originalDay: Long?,
        day: Long,
        weightKg: Double,
        loggedAtMillis: Long,
        keepPhotoIds: List<Long>,
        removePhotoIds: List<Long>,
        newPhotoFiles: List<String>,
    ): List<String> {
        val removed = if (removePhotoIds.isEmpty()) emptyList() else photosById(removePhotoIds)
        if (removePhotoIds.isNotEmpty()) deletePhotos(removePhotoIds)
        if (originalDay != null && originalDay != day) {
            if (keepPhotoIds.isNotEmpty()) movePhotos(keepPhotoIds, day)
            deleteWeighIn(originalDay)
        }
        upsert(WeighInEntity(day, weightKg, loggedAtMillis))
        insertPhotos(newPhotoFiles.map { WeighInPhotoEntity(epochDay = day, fileName = it, addedAtMillis = loggedAtMillis) })
        return removed.map { it.fileName }
    }

    /** Deletes the day's entry and its photo rows; returns the photo file names. */
    @Transaction
    open suspend fun delete(day: Long): List<String> {
        val photos = photosForDay(day)
        deletePhotos(photos.map { it.id })
        deleteWeighIn(day)
        return photos.map { it.fileName }
    }
}

/** The app's database. Version 2 adds food photos (see MIGRATION_1_2). */
@Database(
    entities = [WeighInEntity::class, WeighInPhotoEntity::class, FoodPhotoEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class VitrackDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun foodDao(): FoodDao
}
