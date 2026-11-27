package com.vitrack.app.ui.weight

import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.WeeklyWeight
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.ui.common.ChartPeriod
import java.time.LocalDate

sealed interface WeightUiState {
    data object Loading : WeightUiState

    data class Empty(val unit: WeightUnit, val displayName: String?) : WeightUiState

    data class Ready(
        val displayName: String?,
        val today: LocalDate,
        /** First day of the week containing [today]. */
        val thisWeekStart: LocalDate,
        val unit: WeightUnit,
        val latest: WeighIn,
        /** The most recent week with weigh-ins; its average is the headline. */
        val currentWeek: WeeklyWeight,
        /** [currentWeek]'s average minus the week before's; null when that week has none. */
        val weeklyChangeKg: Double?,
        val period: ChartPeriod,
        val chartStart: LocalDate,
        /** Week: each weigh-in. Month and Year: one point per week, its average. Oldest first. */
        val chart: List<ChartPoint>,
        /** Weeks with weigh-ins, newest first. */
        val weeks: List<WeekGroup>,
    ) : WeightUiState
}

data class ChartPoint(val date: LocalDate, val kg: Double)

/** A History section: the week, its change from the week before, and its rows. */
data class WeekGroup(val week: WeeklyWeight, val changeKg: Double?, val rows: List<HistoryRow>)

/** A History row: [changeKg] is against the previous (older) weigh-in; null for the first. */
data class HistoryRow(val entry: WeighIn, val changeKg: Double?)

/** Pairs each weigh-in with its change from the next older one. [newestFirst] must be sorted newest first. */
internal fun historyRows(newestFirst: List<WeighIn>): List<HistoryRow> =
    newestFirst.mapIndexed { i, entry -> HistoryRow(entry, newestFirst.getOrNull(i + 1)?.let { entry.weightKg - it.weightKg }) }

/**
 * Change in weekly average from the calendar week before. Only a week that
 * directly follows another with weigh-ins has a change. [weeks] newest first.
 */
internal fun weekChanges(weeks: List<WeeklyWeight>): List<Double?> =
    weeks.mapIndexed { i, week ->
        weeks.getOrNull(i + 1)
            ?.takeIf { it.weekStart == week.weekStart.minusWeeks(1) }
            ?.let { week.averageKg - it.averageKg }
    }

/**
 * Week: each weigh-in in the window. Month and Year: each week's average,
 * placed mid-week but kept inside [start]..[today]. Oldest first.
 */
internal fun chartPoints(
    period: ChartPeriod,
    start: LocalDate,
    today: LocalDate,
    weighIns: List<WeighIn>,
    weeks: List<WeeklyWeight>,
): List<ChartPoint> = when (period) {
    ChartPeriod.Week -> weighIns
        .filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
        .sortedBy { it.date }
        .map { ChartPoint(it.date, it.weightKg) }
    ChartPeriod.Month, ChartPeriod.Year -> weeks
        .filter { !it.weekEnd.isBefore(start) && !it.weekStart.isAfter(today) }
        .sortedBy { it.weekStart }
        .map { week ->
            val mid = week.weekStart.plusDays(3)
            val date = when {
                mid.isBefore(start) -> start
                mid.isAfter(today) -> today
                else -> mid
            }
            ChartPoint(date, week.averageKg)
        }
}

/** First day shown for [period], ending [today]. */
internal fun chartStart(period: ChartPeriod, today: LocalDate): LocalDate =
    today.minusDays(period.days - 1L)
