package com.vitrack.app.data.food

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodDataTest {

    @Test
    fun `labels are suggested from the time of day`() {
        assertEquals(FoodLabel.Breakfast, suggestLabel(LocalTime.of(7, 45)))
        assertEquals(FoodLabel.Lunch, suggestLabel(LocalTime.of(12, 35)))
        assertEquals(FoodLabel.Snack, suggestLabel(LocalTime.of(15, 10)))
        assertEquals(FoodLabel.Dinner, suggestLabel(LocalTime.of(19, 5)))
        assertEquals(FoodLabel.Snack, suggestLabel(LocalTime.of(23, 30)))
    }

    @Test
    fun `sample week skips one day and never goes past now`() {
        val today = LocalDate.of(2026, 11, 26)
        val samples = sampleFoodPhotos(today, LocalTime.of(13, 0))

        val days = samples.map { it.date }.toSet()
        assertEquals(6, days.size)
        assertTrue(today.minusDays(4) !in days)
        assertTrue(samples.filter { it.date == today }.all { it.time <= LocalTime.of(13, 0) })
        assertTrue(samples.groupBy { it.date }.filterKeys { it != today }.values.all { it.size in 3..6 })
        assertEquals(samples, sampleFoodPhotos(today, LocalTime.of(13, 0)))
    }
}
