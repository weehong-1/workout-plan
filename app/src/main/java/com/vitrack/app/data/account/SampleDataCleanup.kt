package com.vitrack.app.data.account

import android.graphics.BitmapFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.vitrack.app.data.food.FoodPhotos
import com.vitrack.app.data.food.db.FoodDao
import com.vitrack.app.data.weight.PhotoStore
import com.vitrack.app.data.weight.db.WeightDao
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Debug builds used to fill Weight and Food with made-up entries. This removes
 * those entries once, and only those: each carries the old seeder's
 * fingerprint, which nothing logged in the app has (see [isSampleWeighIn] and
 * [isSampleFoodPhoto]). Real entries, and sample ones edited since, stay.
 */
@Singleton
class SampleDataCleanup @Inject constructor(
    private val weightDao: WeightDao,
    private val foodDao: FoodDao,
    private val weightPhotos: PhotoStore,
    @param:FoodPhotos private val foodPhotos: PhotoStore,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {

    suspend fun runOnce() = withContext(Dispatchers.IO) {
        if (dataStore.data.first()[DONE] == true) return@withContext
        weightDao.allWeighIns()
            .filter { isSampleWeighIn(it.epochDay, it.loggedAtMillis, clock.zone) }
            .forEach { weightPhotos.delete(weightDao.delete(it.epochDay)) }
        foodDao.all()
            .filter { isSampleFoodPhoto(it.secondOfDay, imageSize(foodPhotos, it.fileName)) }
            .forEach {
                foodDao.delete(it.id)
                foodPhotos.delete(listOf(it.fileName))
            }
        dataStore.edit { it[DONE] = true }
    }

    private fun imageSize(store: PhotoStore, fileName: String): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(store.file(fileName).path, options)
        return if (options.outWidth > 0) options.outWidth to options.outHeight else null
    }

    private companion object {
        val DONE = booleanPreferencesKey("sample_data_removed")
    }
}

/**
 * The seeder logged each sample on its own day at a whole minute between 07:05
 * and 07:24. The app stamps a saved weigh-in with the moment it was saved, to
 * the millisecond, so a real one matches by chance about once in 100,000.
 */
internal fun isSampleWeighIn(epochDay: Long, loggedAtMillis: Long, zone: ZoneId): Boolean {
    if (loggedAtMillis % 60_000 != 0L) return false
    val logged = Instant.ofEpochMilli(loggedAtMillis).atZone(zone)
    return logged.toLocalDate() == LocalDate.ofEpochDay(epochDay) &&
        logged.toLocalTime() in LocalTime.of(7, 5)..LocalTime.of(7, 24)
}

/** The seeder's food photos were 800×800 drawings timed to a whole minute; camera and gallery photos aren't both. */
internal fun isSampleFoodPhoto(secondOfDay: Int, size: Pair<Int, Int>?): Boolean =
    secondOfDay % 60 == 0 && size == (800 to 800)
