package com.vitrack.app.data.weight

import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * The weigh-ins of one calendar week. The week's average is the measure the
 * Weight screen reports, because single days swing with water and food.
 */
data class WeeklyWeight(
    val weekStart: LocalDate,
    /** This week's weigh-ins, newest first. */
    val entries: List<WeighIn>,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
    val averageKg: Double get() = entries.map { it.weightKg }.average()
    val count: Int get() = entries.size
}

/** First day of [date]'s week, by [weekFields] (the phone's calendar). */
fun weekStartOf(date: LocalDate, weekFields: WeekFields): LocalDate = date.with(weekFields.dayOfWeek(), 1)

/** Weeks that have weigh-ins, newest first. */
fun groupByWeek(weighIns: List<WeighIn>, weekFields: WeekFields): List<WeeklyWeight> =
    weighIns
        .sortedByDescending { it.date }
        .groupBy { weekStartOf(it.date, weekFields) }
        .map { (start, entries) -> WeeklyWeight(start, entries) }
        .sortedByDescending { it.weekStart }
