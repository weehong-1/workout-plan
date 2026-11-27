package com.vitrack.app.data.weight

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyWeightTest {

    private fun entry(date: LocalDate, kg: Double) = WeighIn(date, kg, Instant.EPOCH, emptyList())

    @Test
    fun `weeks follow the calendar's first day`() {
        val thursday = LocalDate.of(2026, 11, 26)
        assertEquals(LocalDate.of(2026, 11, 23), weekStartOf(thursday, WeekFields.ISO))
        assertEquals(LocalDate.of(2026, 11, 22), weekStartOf(thursday, WeekFields.of(Locale.US)))
    }

    @Test
    fun `groups by week, newest first, and averages each week`() {
        val weeks = groupByWeek(
            listOf(
                entry(LocalDate.of(2026, 11, 16), 80.0),
                entry(LocalDate.of(2026, 11, 26), 78.0),
                entry(LocalDate.of(2026, 11, 23), 79.0),
            ),
            WeekFields.ISO,
        )

        assertEquals(listOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 16)), weeks.map { it.weekStart })
        assertEquals(78.5, weeks[0].averageKg, 0.0)
        assertEquals(listOf(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 23)), weeks[0].entries.map { it.date })
        assertEquals(LocalDate.of(2026, 11, 29), weeks[0].weekEnd)
    }
}
