package com.vitrack.app.data.weight

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields

/** How weigh-ins are grouped: the Weight screen reports each group's average. */
enum class WeightZoom { Weeks, Months, Years }

/**
 * The weigh-ins in one week, month or year. Its average is the measure the
 * Weight screen reports, because single days swing with water and food.
 */
data class WeightPeriod(
    val zoom: WeightZoom,
    val start: LocalDate,
    /** Last day of the period, inclusive. */
    val end: LocalDate,
    /** The period's weigh-ins, newest first. */
    val entries: List<WeighIn>,
) {
    val averageKg: Double get() = entries.map { it.weightKg }.average()
    val minKg: Double get() = entries.minOf { it.weightKg }
    val maxKg: Double get() = entries.maxOf { it.weightKg }
    val count: Int get() = entries.size
}

/** First day of [date]'s week, by [weekFields] (the phone's calendar). */
fun weekStartOf(date: LocalDate, weekFields: WeekFields): LocalDate = date.with(weekFields.dayOfWeek(), 1)

/** First day of the [zoom] period containing [date]. */
fun periodStartOf(date: LocalDate, zoom: WeightZoom, weekFields: WeekFields): LocalDate = when (zoom) {
    WeightZoom.Weeks -> weekStartOf(date, weekFields)
    WeightZoom.Months -> date.withDayOfMonth(1)
    WeightZoom.Years -> date.withDayOfYear(1)
}

/** The period before the one starting on [start]. */
fun previousPeriodStart(start: LocalDate, zoom: WeightZoom): LocalDate = when (zoom) {
    WeightZoom.Weeks -> start.minusWeeks(1)
    WeightZoom.Months -> start.minusMonths(1)
    WeightZoom.Years -> start.minusYears(1)
}

private fun periodEnd(start: LocalDate, zoom: WeightZoom): LocalDate = when (zoom) {
    WeightZoom.Weeks -> start.plusDays(6)
    WeightZoom.Months -> YearMonth.from(start).atEndOfMonth()
    WeightZoom.Years -> start.withDayOfYear(start.lengthOfYear())
}

/** Periods that have weigh-ins, newest first. */
fun groupWeighIns(weighIns: List<WeighIn>, zoom: WeightZoom, weekFields: WeekFields): List<WeightPeriod> =
    weighIns
        .sortedByDescending { it.date }
        .groupBy { periodStartOf(it.date, zoom, weekFields) }
        .map { (start, entries) -> WeightPeriod(zoom, start, periodEnd(start, zoom), entries) }
        .sortedByDescending { it.start }
