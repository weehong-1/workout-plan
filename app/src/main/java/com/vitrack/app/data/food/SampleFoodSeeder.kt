package com.vitrack.app.data.food

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.vitrack.app.data.food.db.FoodDao
import com.vitrack.app.data.food.db.FoodPhotoEntity
import com.vitrack.app.data.weight.PhotoStore
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Fills Food with a week of sample photos for previews in debug builds, once
 * per install and only when there are no food photos yet. Not used in release.
 */
@Singleton
class SampleFoodSeeder @Inject constructor(
    private val dao: FoodDao,
    @param:FoodPhotos private val photos: PhotoStore,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {

    suspend fun seedOnce() = withContext(Dispatchers.IO) {
        if (dataStore.data.first()[SEEDED] == true) return@withContext
        if (dao.count() == 0) {
            dao.insert(
                sampleFoodPhotos(LocalDate.now(clock), LocalTime.now(clock)).map { sample ->
                    FoodPhotoEntity(
                        epochDay = sample.date.toEpochDay(),
                        secondOfDay = sample.time.toSecondOfDay(),
                        fileName = writePlaceholder(sample.hue),
                        label = sample.label?.name,
                        note = sample.note,
                    )
                },
            )
        }
        dataStore.edit { it[SEEDED] = true }
    }

    /** A plate on a table, in a warm colour, standing in for a food photo. */
    private fun writePlaceholder(hue: Float): String {
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.HSVToColor(floatArrayOf((hue + 20) % 360, 0.25f, 0.35f)))
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(235, 245, 242, 236) }
        canvas.drawCircle(400f, 400f, 300f, plate)
        val food = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                380f, 380f, 220f,
                android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.65f, 0.95f)),
                android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.55f)),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(400f, 400f, 200f, food)
        val name = "${UUID.randomUUID()}.jpg"
        photos.file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        return name
    }

    private companion object {
        val SEEDED = booleanPreferencesKey("sample_food_seeded")
    }
}

internal data class SampleFoodPhoto(
    val date: LocalDate,
    val time: LocalTime,
    val label: FoodLabel?,
    val note: String?,
    val hue: Float,
)

/**
 * The last 7 days: 3 to 6 photos a day at meal times, one day left empty so
 * the strip shows a day without a dot. Today stops at [now]. Deterministic.
 */
internal fun sampleFoodPhotos(today: LocalDate, now: LocalTime): List<SampleFoodPhoto> {
    val random = Random(7)
    val slots = listOf(
        LocalTime.of(7, 45) to FoodLabel.Breakfast,
        LocalTime.of(10, 20) to null,
        LocalTime.of(12, 35) to FoodLabel.Lunch,
        LocalTime.of(15, 10) to FoodLabel.Snack,
        LocalTime.of(19, 5) to FoodLabel.Dinner,
        LocalTime.of(19, 12) to FoodLabel.Dinner,
    )
    val notes = listOf("Oats and berries", null, "Chicken rice", null, "Salmon, greens", null)
    return (6 downTo 0).flatMap { back ->
        val date = today.minusDays(back.toLong())
        if (back == 4) return@flatMap emptyList()
        slots.withIndex()
            .filter { (i, _) -> i == 0 || i == 2 || i == 4 || random.nextInt(3) > 0 }
            .map { (i, slot) ->
                val time = slot.first.plusMinutes(random.nextLong(-15, 16))
                SampleFoodPhoto(date, time, slot.second, notes[i].takeIf { back == 0 }, hue = random.nextInt(0, 60).toFloat())
            }
            .filter { back > 0 || it.time <= now }
    }
}
