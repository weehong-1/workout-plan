package com.vitrack.app.data.share

import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.food.FoodPhoto
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.WeighIn
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * The review copied to the server for a link and read by the web page
 * `/share/<token>` (src/lib/shared-review.ts on the ios branch). Version 1,
 * field for field the same as the iOS app's, so one page serves both.
 *
 * Android's activity comes from WHOOP, not Apple Health: there are no move
 * calories or rings, so those fields are empty (null, 0, false), and the WHOOP
 * figures go in extra fields ([Activity.source], strain, recovery) that the
 * page can show; it ignores fields it doesn't know.
 */
@Serializable
data class SharedReviewPayload(
    val version: Int = 1,
    val period: Period,
    /** Last day the link works (yyyy-MM-dd), or null for no expiry. */
    val expiresOn: String?,
    val activity: Activity?,
    val weight: Weight?,
    val food: Food?,
) {
    @Serializable
    data class Period(val kind: String, val label: String, val start: String, val end: String, val days: Int)

    @Serializable
    data class Activity(
        val averageSteps: Int?,
        val averageSleepMinutes: Int?,
        val averageMoveKcal: Int? = null,
        val ringsClosedDays: Int = 0,
        val daily: List<Day>,
        val source: String = "whoop",
        val averageStrain: Double?,
        val averageRecovery: Int?,
    ) {
        @Serializable
        data class Day(
            val date: String,
            val steps: Int,
            val moveKcal: Int = 0,
            val sleepMinutes: Int?,
            val ringsClosed: Boolean = false,
            val strain: Double,
            val recovery: Int,
        )
    }

    @Serializable
    data class Weight(
        val unit: String,
        val count: Int,
        val first: Point,
        val last: Point,
        val change: Double,
        val entries: List<Point>,
        val firstPhoto: Photo?,
        val lastPhoto: Photo?,
    ) {
        @Serializable
        data class Point(val date: String, val value: Double)

        @Serializable
        data class Photo(val path: String, val date: String)
    }

    @Serializable
    data class Food(val daysWithPhotos: Int, val photos: List<Photo>) {
        @Serializable
        data class Photo(val path: String, val date: String, val time: String, val label: String?)
    }
}

/** What a link includes, as the `share_links.includes` column (same keys as iOS). */
@Serializable
data class ShareIncludes(
    val activity: Boolean,
    val weight: Boolean,
    val weightPhotos: Boolean,
    val food: Boolean,
)

fun ShareContents.toIncludes() = ShareIncludes(activity = activity, weight = weight, weightPhotos = weightPhotos, food = foodPhotos)

/** The phone's data for a link's period, before it is turned into a [SharedReviewPayload]. */
data class ShareSource(
    val period: SharePeriod,
    val today: LocalDate,
    val unit: WeightUnit,
    val activity: List<DailyActivity>,
    val weighIns: List<WeighIn>,
    val foodPhotos: List<FoodPhoto>,
)

/** A photo to copy to the server at [path] before the link row is written. */
data class PhotoUpload(val path: String, val file: File)

data class BuiltShare(val summary: SharedReviewPayload, val uploads: List<PhotoUpload>)

/** The days of [period] that have happened: its start to the earlier of its end and [today]. */
fun SharePeriod.daysSoFar(today: LocalDate): ClosedRange<LocalDate> = start..minOf(end, today)

fun SharePeriod.kind(): String = if (this is SharePeriod.Year) "year" else "month"

/** "November 2026" or "2026", as the iOS app labels periods. */
fun SharePeriod.label(locale: Locale = Locale.getDefault()): String = when (this) {
    is SharePeriod.Month -> month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    is SharePeriod.Year -> year.toString()
}

/**
 * Turns the phone's data into the review a link shows, keeping only what
 * [contents] includes. Photos are named `<user>/share-<link>-<photo>.jpg`,
 * the form the schema allows, so revoking or deleting the account removes them.
 */
fun buildSharedReview(
    source: ShareSource,
    contents: ShareContents,
    expiresOn: LocalDate?,
    linkId: String,
    userFolder: String,
): BuiltShare {
    val range = source.period.daysSoFar(source.today)
    val uploads = mutableListOf<PhotoUpload>()
    fun photoPath(key: String) = "$userFolder/share-$linkId-$key.jpg"

    val activity = source.activity.filter { it.date in range }.sortedBy { it.date }
        .takeIf { contents.activity && it.isNotEmpty() }
        ?.let { days ->
            val slept = days.filter { it.sleepMinutes > 0 }
            SharedReviewPayload.Activity(
                averageSteps = days.map { it.steps }.average().roundToInt(),
                averageSleepMinutes = slept.takeIf { it.isNotEmpty() }?.map { it.sleepMinutes }?.average()?.roundToInt(),
                daily = days.map {
                    SharedReviewPayload.Activity.Day(
                        date = it.date.toString(),
                        steps = it.steps,
                        sleepMinutes = it.sleepMinutes.takeIf { minutes -> minutes > 0 },
                        strain = round1(it.strain),
                        recovery = it.recovery,
                    )
                },
                averageStrain = round1(days.map { it.strain }.average()),
                averageRecovery = days.map { it.recovery }.average().roundToInt(),
            )
        }

    val weight = source.weighIns.filter { it.date in range }.sortedBy { it.date }
        .takeIf { contents.weight && it.isNotEmpty() }
        ?.let { entries ->
            fun point(entry: WeighIn) = SharedReviewPayload.Weight.Point(entry.date.toString(), round1(source.unit.fromKg(entry.weightKg)))
            fun photo(entry: WeighIn?): SharedReviewPayload.Weight.Photo? {
                val picture = entry?.photos?.firstOrNull() ?: return null
                if (!contents.weightPhotos) return null
                val path = photoPath("w${picture.id}")
                uploads += PhotoUpload(path, picture.file)
                return SharedReviewPayload.Weight.Photo(path, entry.date.toString())
            }
            val withPhotos = entries.filter { it.photos.isNotEmpty() }
            SharedReviewPayload.Weight(
                unit = if (source.unit == WeightUnit.Lb) "lb" else "kg",
                count = entries.size,
                first = point(entries.first()),
                last = point(entries.last()),
                change = round1(source.unit.fromKg(entries.last().weightKg - entries.first().weightKg)),
                entries = entries.map(::point),
                firstPhoto = photo(withPhotos.firstOrNull()),
                lastPhoto = photo(withPhotos.lastOrNull()?.takeIf { withPhotos.size > 1 }),
            )
        }

    val food = if (contents.foodPhotos) {
        val photos = source.foodPhotos.filter { it.takenAt.toLocalDate() in range }.sortedBy { it.takenAt }
        SharedReviewPayload.Food(
            daysWithPhotos = photos.map { it.takenAt.toLocalDate() }.distinct().size,
            photos = photos.map {
                val path = photoPath("f${it.id}")
                uploads += PhotoUpload(path, it.file)
                SharedReviewPayload.Food.Photo(
                    path = path,
                    date = it.takenAt.toLocalDate().toString(),
                    time = String.format(Locale.ROOT, "%02d:%02d", it.takenAt.hour, it.takenAt.minute),
                    label = it.label?.name,
                )
            },
        )
    } else {
        null
    }

    val days = if (range.start > range.endInclusive) 0 else (range.endInclusive.toEpochDay() - range.start.toEpochDay() + 1).toInt()
    val summary = SharedReviewPayload(
        period = SharedReviewPayload.Period(
            kind = source.period.kind(),
            label = source.period.label(),
            start = source.period.start.toString(),
            end = source.period.end.toString(),
            days = days,
        ),
        expiresOn = expiresOn?.toString(),
        activity = activity,
        weight = weight,
        food = food,
    )
    return BuiltShare(summary, uploads)
}

private fun round1(value: Double) = (value * 10).roundToInt() / 10.0
