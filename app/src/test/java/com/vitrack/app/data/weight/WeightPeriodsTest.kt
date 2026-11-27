package com.vitrack.app.data.weight

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightPeriodsTest {

    private fun entry(date: LocalDate, kg: Double) = WeighIn(date, kg, Instant.EPOCH, emptyList())

    private val entries = listOf(
        entry(LocalDate.of(2026, 11, 26), 78.0),
        entry(LocalDate.of(2026, 11, 23), 79.0),
        entry(LocalDate.of(2026, 11, 16), 80.0),
        entry(LocalDate.of(2026, 10, 30), 81.0),
        entry(LocalDate.of(2025, 6, 1), 85.0),
    )

    @Test
    fun `weeks follow the calendar's first day`() {
        val thursday = LocalDate.of(2026, 11, 26)
        assertEquals(LocalDate.of(2026, 11, 23), weekStartOf(thursday, WeekFields.ISO))
        assertEquals(LocalDate.of(2026, 11, 22), weekStartOf(thursday, WeekFields.of(Locale.US)))
    }

    @Test
    fun `weeks group newest first with average, range and end`() {
        val weeks = groupWeighIns(entries, WeightZoom.Weeks, WeekFields.ISO)

        assertEquals(LocalDate.of(2026, 11, 23), weeks[0].start)
        assertEquals(LocalDate.of(2026, 11, 29), weeks[0].end)
        assertEquals(78.5, weeks[0].averageKg, 0.0)
        assertEquals(78.0, weeks[0].minKg, 0.0)
        assertEquals(79.0, weeks[0].maxKg, 0.0)
        assertEquals(listOf(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 23)), weeks[0].entries.map { it.date })
    }

    @Test
    fun `months and years group by calendar`() {
        val months = groupWeighIns(entries, WeightZoom.Months, WeekFields.ISO)
        assertEquals(listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2025, 6, 1)), months.map { it.start })
        assertEquals(LocalDate.of(2026, 11, 30), months[0].end)
        assertEquals(79.0, months[0].averageKg, 0.0001)

        val years = groupWeighIns(entries, WeightZoom.Years, WeekFields.ISO)
        assertEquals(listOf(2026, 2025), years.map { it.start.year })
        assertEquals(LocalDate.of(2026, 12, 31), years[0].end)
        assertEquals(4, years[0].count)
    }

    @Test
    fun `previous period start steps back one unit`() {
        assertEquals(LocalDate.of(2026, 11, 16), previousPeriodStart(LocalDate.of(2026, 11, 23), WeightZoom.Weeks))
        assertEquals(LocalDate.of(2026, 10, 1), previousPeriodStart(LocalDate.of(2026, 11, 1), WeightZoom.Months))
        assertEquals(LocalDate.of(2025, 1, 1), previousPeriodStart(LocalDate.of(2026, 1, 1), WeightZoom.Years))
    }
}
