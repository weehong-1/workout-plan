package com.vitrack.app.data.export

import com.vitrack.app.data.food.FoodRepository
import com.vitrack.app.data.weight.WeightRepository
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class ExportSummary(val weighIns: Int, val foodPhotos: Int, val photoFiles: Int)

/**
 * "Export my data" (Profile): weigh-ins, food photos and their labels and
 * notes, as one ZIP the person saves where they choose:
 * `weight.csv`, `food.csv`, `photos/weight/…` and `photos/food/…`.
 */
class DataExporter @Inject constructor(
    private val weight: WeightRepository,
    private val food: FoodRepository,
) {
    suspend fun export(out: OutputStream): ExportSummary = withContext(Dispatchers.IO) {
        val weighIns = weight.observeWeighIns().first().sortedBy { it.date }
        val meals = food.photosBetween(LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31))
        var files = 0
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            fun putFile(name: String, file: java.io.File): Boolean {
                if (!file.exists()) return false
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                files++
                return true
            }

            val weightRows = StringBuilder("date,weight_kg,photos\n")
            weighIns.forEach { entry ->
                val names = entry.photos.mapNotNull { photo ->
                    val name = "photos/weight/${entry.date}-${photo.id}.jpg"
                    name.takeIf { putFile(it, photo.file) }
                }
                weightRows.append(entry.date).append(',')
                    .append(String.format(Locale.ROOT, "%.2f", entry.weightKg)).append(',')
                    .append(csv(names.joinToString(" "))).append('\n')
            }
            put("weight.csv", weightRows.toString().encodeToByteArray())

            val foodRows = StringBuilder("date,time,label,note,photo\n")
            meals.forEach { meal ->
                val time = String.format(Locale.ROOT, "%02d:%02d", meal.takenAt.hour, meal.takenAt.minute)
                val name = "photos/food/${meal.takenAt.toLocalDate()}-${time.replace(":", "")}-${meal.id}.jpg"
                val saved = putFile(name, meal.file)
                foodRows.append(meal.takenAt.toLocalDate()).append(',').append(time).append(',')
                    .append(meal.label?.name.orEmpty()).append(',')
                    .append(csv(meal.note.orEmpty())).append(',')
                    .append(if (saved) name else "").append('\n')
            }
            put("food.csv", foodRows.toString().encodeToByteArray())
        }
        ExportSummary(weighIns.size, meals.size, files)
    }

    /** A CSV field, quoted when it holds a comma, quote or line break. */
    internal fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    companion object {
        fun fileName(today: LocalDate) = "vitrack-export-$today.zip"
    }
}
