package com.vitrack.app.ui.review

import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.share.ShareLink
import com.vitrack.app.data.share.SharePeriod
import com.vitrack.app.data.weight.WeighIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class ReviewUiState(
    val isLoading: Boolean = true,
    val displayName: String? = null,
    val today: LocalDate = LocalDate.MIN,
    val unit: WeightUnit = WeightUnit.Kg,
    val period: SharePeriod = SharePeriod.Month(YearMonth.of(2000, 1)),
    val activity: ActivityAverages? = null,
    val weight: WeightSummary? = null,
    val food: FoodSummary = FoodSummary(0, 0, emptyList()),
    val links: List<ShareLink> = emptyList(),
    val sharingAvailable: Boolean = false,
    val isSignedIn: Boolean = false,
) {
    val isYear: Boolean get() = period is SharePeriod.Year
    /** No going past the period that holds today. */
    val canGoForward: Boolean get() = nextPeriod(period).start <= today
}

/** Daily averages from WHOOP over the days counted. */
data class ActivityAverages(
    val steps: Int,
    val strain: Double,
    val recovery: Int,
    val sleepMinutes: Int,
    val days: Int,
)

data class WeightSummary(
    val first: WeighIn,
    val last: WeighIn,
    val count: Int,
    /** The period's earliest and latest weigh-ins that have a photo. */
    val firstPhoto: WeighIn?,
    val lastPhoto: WeighIn?,
) {
    val changeKg: Double get() = last.weightKg - first.weightKg
}

/**
 * Days with food photos out of the days so far. [marks]: one per day for a
 * month (true when it has photos); for a year, one per month with the share
 * of its days that have photos, 0..1.
 */
data class FoodSummary(
    val daysWithPhotos: Int,
    val daysSoFar: Int,
    val marks: List<Float>,
)

internal fun nextPeriod(period: SharePeriod): SharePeriod = when (period) {
    is SharePeriod.Month -> SharePeriod.Month(period.month.plusMonths(1))
    is SharePeriod.Year -> SharePeriod.Year(period.year + 1)
}

internal fun previousPeriod(period: SharePeriod): SharePeriod = when (period) {
    is SharePeriod.Month -> SharePeriod.Month(period.month.minusMonths(1))
    is SharePeriod.Year -> SharePeriod.Year(period.year - 1)
}

/** The days of [period] that have happened: start to the earlier of its end and [today]. */
internal fun daysSoFar(period: SharePeriod, today: LocalDate): ClosedRange<LocalDate> =
    period.start..minOf(period.end, today)

internal fun activityAverages(days: List<DailyActivity>): ActivityAverages? {
    if (days.isEmpty()) return null
    return ActivityAverages(
        steps = days.map { it.steps }.average().roundToInt(),
        strain = days.map { it.strain }.average(),
        recovery = days.map { it.recovery }.average().roundToInt(),
        sleepMinutes = days.map { it.sleepMinutes }.average().roundToInt(),
        days = days.size,
    )
}

internal fun weightSummary(inPeriod: List<WeighIn>): WeightSummary? {
    if (inPeriod.isEmpty()) return null
    val oldestFirst = inPeriod.sortedBy { it.date }
    val withPhotos = oldestFirst.filter { it.photos.isNotEmpty() }
    return WeightSummary(
        first = oldestFirst.first(),
        last = oldestFirst.last(),
        count = oldestFirst.size,
        firstPhoto = withPhotos.firstOrNull(),
        lastPhoto = withPhotos.lastOrNull()?.takeIf { withPhotos.size > 1 },
    )
}

internal fun foodSummary(period: SharePeriod, today: LocalDate, counts: Map<LocalDate, Int>): FoodSummary {
    val range = daysSoFar(period, today)
    if (range.start > range.endInclusive) return FoodSummary(0, 0, emptyList())
    val days = generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= range.endInclusive }.toList()
    val withPhotos = days.filter { (counts[it] ?: 0) > 0 }.toSet()
    val marks = when (period) {
        is SharePeriod.Month -> days.map { if (it in withPhotos) 1f else 0f }
        is SharePeriod.Year -> (1..12).mapNotNull { month ->
            val inMonth = days.filter { it.monthValue == month }
            if (inMonth.isEmpty()) null else inMonth.count { it in withPhotos }.toFloat() / inMonth.size
        }
    }
    return FoodSummary(withPhotos.size, days.size, marks)
}

/** Days from [from] to [today], inclusive, for asking the activity history. */
internal fun daysBack(from: LocalDate, today: LocalDate): Int = ChronoUnit.DAYS.between(from, today).toInt() + 1
