package com.vitrack.app.data.weight

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.vitrack.app.data.weight.db.WeightDao
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Fills Weight with about three months of sample weigh-ins, for previews in
 * debug builds. Runs once per install, and never on a day that already has a
 * weigh-in. Not used in release builds.
 */
@Singleton
class SampleWeightSeeder @Inject constructor(
    private val dao: WeightDao,
    private val photos: PhotoStore,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {

    suspend fun seedOnce() = withContext(Dispatchers.IO) {
        if (dataStore.data.first()[SEEDED] == true) return@withContext
        val taken = dao.observeWeighIns().first().map { it.epochDay }.toSet()
        sampleWeighIns(LocalDate.now(clock))
            .filter { it.date.toEpochDay() !in taken }
            .forEach { sample ->
                val files = List(sample.photoCount) { i -> writePlaceholderPhoto(sample.date, i) }
                val loggedAt = sample.date.atTime(sample.time).atZone(clock.zone).toInstant().toEpochMilli()
                dao.save(null, sample.date.toEpochDay(), sample.weightKg, loggedAt, emptyList(), emptyList(), files)
            }
        dataStore.edit { it[SEEDED] = true }
    }

    /** A soft gradient with a simple figure, standing in for a progress photo. */
    private fun writePlaceholderPhoto(date: LocalDate, index: Int): String {
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hue = (date.dayOfYear * 7 + index * 40) % 360
        val top = android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.35f, 0.55f))
        val bottom = android.graphics.Color.HSVToColor(floatArrayOf(((hue + 30) % 360).toFloat(), 0.45f, 0.25f))
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 800f, top, bottom, Shader.TileMode.CLAMP) })
        val figure = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(150, 255, 255, 255) }
        canvas.drawCircle(300f, 230f, 70f, figure)
        canvas.drawRoundRect(RectF(190f, 320f, 410f, 640f), 90f, 90f, figure)
        val name = "${UUID.randomUUID()}.jpg"
        photos.file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        return name
    }

    private companion object {
        // v2: three years instead of three months, so Months and Years have data.
        val SEEDED = booleanPreferencesKey("sample_weight_seeded_v2")
    }
}

internal data class SampleWeighIn(val date: LocalDate, val weightKg: Double, val time: LocalTime, val photoCount: Int)

/** Days of sample history. */
internal const val SAMPLE_DAYS = 3 * 365

/**
 * The underlying weight [daysBack] days ago: 80 kg three years ago, rising to
 * a peak of 86 kg about 200 days ago, then down to 84.6 kg 90 days ago and
 * 78.4 kg today.
 */
internal fun sampleTrendKg(daysBack: Int): Double = when {
    daysBack <= 90 -> 78.4 + daysBack * (84.6 - 78.4) / 90.0
    daysBack <= 200 -> 84.6 + (daysBack - 90) * (86.0 - 84.6) / 110.0
    else -> 86.0 - (daysBack - 200) * (86.0 - 80.0) / (SAMPLE_DAYS - 200.0)
}

/**
 * Three years of weigh-ins every 2 to 3 days following [sampleTrendKg], with
 * day-to-day noise. Photos on roughly every sixth weigh-in in the last 90
 * days and on today's. Deterministic, so every install looks the same.
 */
internal fun sampleWeighIns(today: LocalDate): List<SampleWeighIn> {
    val random = Random(42)
    val days = mutableListOf<Int>()
    var back = 0
    while (back <= SAMPLE_DAYS) {
        days += back
        back += if (random.nextBoolean()) 2 else 3
    }
    return days.sortedDescending().mapIndexed { i, daysBack ->
        val date = today.minusDays(daysBack.toLong())
        val noise = if (daysBack == 0) 0.0 else 0.35 * sin(daysBack * 1.3) + random.nextDouble(-0.2, 0.2)
        SampleWeighIn(
            date = date,
            weightKg = ((sampleTrendKg(daysBack) + noise) * 10).roundToInt() / 10.0,
            time = LocalTime.of(7, 5 + random.nextInt(0, 20)),
            photoCount = when {
                daysBack == 0 -> 1
                daysBack <= 90 && i % 6 == 0 -> 1
                else -> 0
            },
        )
    }
}
