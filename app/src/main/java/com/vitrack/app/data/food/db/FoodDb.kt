package com.vitrack.app.data.food.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One food photo; [epochDay] is the local day it was taken, for fast per-day queries. */
@Entity(tableName = "food_photo", indices = [Index("epochDay")])
data class FoodPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** Local date-time it was taken, as seconds of the day plus [epochDay]. */
    val secondOfDay: Int,
    val fileName: String,
    val label: String?,
    val note: String?,
)

/** Photos taken on one day. */
data class DayCount(val epochDay: Long, val count: Int)

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_photo WHERE epochDay = :epochDay ORDER BY secondOfDay, id")
    fun observeDay(epochDay: Long): Flow<List<FoodPhotoEntity>>

    @Query("SELECT epochDay, COUNT(*) AS count FROM food_photo WHERE epochDay >= :fromDay GROUP BY epochDay")
    fun observeCounts(fromDay: Long): Flow<List<DayCount>>

    @Query("SELECT * FROM food_photo WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay, secondOfDay, id")
    suspend fun between(fromDay: Long, toDay: Long): List<FoodPhotoEntity>

    @Query("SELECT * FROM food_photo WHERE id = :id")
    suspend fun get(id: Long): FoodPhotoEntity?

    @Insert
    suspend fun insert(photos: List<FoodPhotoEntity>)

    @Query("UPDATE food_photo SET label = :label, note = :note WHERE id = :id")
    suspend fun update(id: Long, label: String?, note: String?)

    @Query("DELETE FROM food_photo WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM food_photo")
    suspend fun count(): Int
}

/** Version 2 adds food photos; weigh-ins are untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_photo` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                "`secondOfDay` INTEGER NOT NULL, `fileName` TEXT NOT NULL, `label` TEXT, `note` TEXT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_photo_epochDay` ON `food_photo` (`epochDay`)")
    }
}
