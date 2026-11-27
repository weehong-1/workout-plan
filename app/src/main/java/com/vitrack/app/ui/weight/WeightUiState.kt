package com.vitrack.app.ui.weight

import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.data.weight.WeightPeriod
import com.vitrack.app.data.weight.WeightZoom
import com.vitrack.app.data.weight.groupWeighIns
import com.vitrack.app.data.weight.previousPeriodStart
import java.time.LocalDate
import java.time.temporal.WeekFields

sealed interface WeightUiState {
    data object Loading : WeightUiState

    data class Empty(val unit: WeightUnit, val displayName: String?) : WeightUiState

    data class Ready(
        val displayName: String?,
        val today: LocalDate,
        val unit: WeightUnit,
        val zoom: WeightZoom,
        /** First day of the period that contains [today]. */
        val todayPeriodStart: LocalDate,
        val latest: WeighIn,
        /** The newest period with weigh-ins; its average is the headline. */
        val current: WeightPeriod,
        /** The period just before [current], when it has weigh-ins. */
        val previous: WeightPeriod?,
        /** [current]'s average minus [previous]'s. */
        val changeKg: Double?,
        /** Periods on the chart, oldest first: 12 weeks, 12 months, or every year. */
        val chart: List<WeightPeriod>,
        val trend: Trend?,
        /** Every period with weigh-ins, newest first. */
        val history: List<PeriodRow>,
    ) : WeightUiState
}

/** A History card: the period, its change from the one listed before it, and what it opens to. */
data class PeriodRow(
    val period: WeightPeriod,
    val changeKg: Double?,
    val details: PeriodDetails,
)

sealed interface PeriodDetails {
    /** A week opens to its weigh-ins, newest first. */
    data class Entries(val entries: List<WeighIn>) : PeriodDetails

    /** A month opens to its weeks, newest first. */
    data class Weeks(val weeks: List<WeightPeriod>) : PeriodDetails

    /** A year opens to its lowest and highest monthly averages. */
    data class Extremes(val lowestMonth: WeightPeriod, val highestMonth: WeightPeriod) : PeriodDetails
}

/** The line under the chart. */
sealed interface Trend {
    /** Weeks: the latest run of weekly averages moving the same way. */
    data class Streak(val weeks: Int, val down: Boolean) : Trend

    /** Months: change from the first to the last month on the chart. */
    data class OverMonths(val changeKg: Double, val months: Int) : Trend

    /** Years: change from the highest year, or from the lowest when this year is the highest. */
    data class FromYear(val changeKg: Double, val year: Int) : Trend
}

internal fun chartWindow(zoom: WeightZoom, todayPeriodStart: LocalDate, newestFirst: List<WeightPeriod>): List<WeightPeriod> {
    val first = when (zoom) {
        WeightZoom.Weeks -> todayPeriodStart.minusWeeks(11)
        WeightZoom.Months -> todayPeriodStart.minusMonths(11)
        WeightZoom.Years -> LocalDate.MIN
    }
    return newestFirst.filter { !it.start.isBefore(first) }.reversed()
}

internal fun trendOf(zoom: WeightZoom, chart: List<WeightPeriod>): Trend? {
    if (chart.size < 2) return null
    val averages = chart.map { it.averageKg }
    return when (zoom) {
        WeightZoom.Weeks -> {
            val down = averages.last() < averages[averages.size - 2]
            var run = 0
            for (i in averages.lastIndex downTo 1) {
                val step = averages[i] - averages[i - 1]
                if ((down && step < 0) || (!down && step > 0)) run++ else break
            }
            Trend.Streak(run, down)
        }
        WeightZoom.Months -> Trend.OverMonths(averages.last() - averages.first(), chart.size)
        WeightZoom.Years -> {
            val current = chart.last()
            val highest = chart.maxBy { it.averageKg }
            val reference = if (highest == current) chart.minBy { it.averageKg } else highest
            Trend.FromYear(current.averageKg - reference.averageKg, reference.start.year)
        }
    }
}

internal fun historyRows(newestFirst: List<WeightPeriod>, weekFields: WeekFields): List<PeriodRow> =
    newestFirst.mapIndexed { i, period ->
        PeriodRow(
            period = period,
            changeKg = newestFirst.getOrNull(i + 1)?.let { period.averageKg - it.averageKg },
            details = when (period.zoom) {
                WeightZoom.Weeks -> PeriodDetails.Entries(period.entries)
                WeightZoom.Months -> PeriodDetails.Weeks(groupWeighIns(period.entries, WeightZoom.Weeks, weekFields))
                WeightZoom.Years -> {
                    val months = groupWeighIns(period.entries, WeightZoom.Months, weekFields)
                    PeriodDetails.Extremes(months.minBy { it.averageKg }, months.maxBy { it.averageKg })
                }
            },
        )
    }

/** The period right before [current] when it has weigh-ins; [newestFirst] holds all periods. */
internal fun previousOf(current: WeightPeriod, newestFirst: List<WeightPeriod>): WeightPeriod? {
    val wanted = previousPeriodStart(current.start, current.zoom)
    return newestFirst.firstOrNull { it.start == wanted }
}
