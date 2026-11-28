package com.vitrack.app.data.account

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleDataCleanupTest {

    private val zone = ZoneId.of("Asia/Singapore")
    private val day = LocalDate.of(2026, 11, 27)
    private fun millis(at: LocalDateTime) = at.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `seeded weigh-ins match`() {
        assertTrue(isSampleWeighIn(day.toEpochDay(), millis(day.atTime(7, 5)), zone))
        assertTrue(isSampleWeighIn(day.toEpochDay(), millis(day.atTime(7, 24)), zone))
    }

    @Test
    fun `weigh-ins saved in the app don't match`() {
        // Saved now, to the millisecond.
        assertFalse(isSampleWeighIn(day.toEpochDay(), millis(day.atTime(7, 18, 3)) + 412, zone))
        // A past day logged today, as the editor stamps it.
        assertFalse(isSampleWeighIn(day.minusDays(48).toEpochDay(), millis(day.atTime(7, 18)), zone))
        // Outside the seeder's window.
        assertFalse(isSampleWeighIn(day.toEpochDay(), millis(day.atTime(8, 0)), zone))
    }

    @Test
    fun `only whole-minute 800 by 800 food photos match`() {
        assertTrue(isSampleFoodPhoto(7 * 3600 + 45 * 60, 800 to 800))
        assertFalse(isSampleFoodPhoto(7 * 3600 + 45 * 60 + 12, 800 to 800))
        assertFalse(isSampleFoodPhoto(7 * 3600 + 45 * 60, 1600 to 1200))
        assertFalse(isSampleFoodPhoto(7 * 3600 + 45 * 60, null))
    }
}
