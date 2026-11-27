package com.vitrack.app.data.settings

import com.vitrack.app.data.reminders.nextOccurrence
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightGoalTest {

    @Test
    fun `lose and gain colour by direction`() {
        val lose = WeightGoal(GoalKind.Lose)
        assertTrue(lose.isGoodChange(-0.4))
        assertTrue(lose.isGoodChange(0.0))
        assertFalse(lose.isGoodChange(0.3))

        val gain = WeightGoal(GoalKind.Gain)
        assertTrue(gain.isGoodChange(0.3))
        assertFalse(gain.isGoodChange(-0.1))
    }

    @Test
    fun `maintain counts weeks near the target`() {
        val maintain = WeightGoal(GoalKind.Maintain, targetKg = 78.5)
        assertTrue(maintain.isOnTrack(78.9, previousKg = 80.0))
        assertTrue(maintain.isOnTrack(78.0, previousKg = null))
        assertFalse(maintain.isOnTrack(79.2, previousKg = 79.1))
        // Change-based colours: within half a kilo either way.
        assertTrue(maintain.isGoodChange(-0.5))
        assertFalse(maintain.isGoodChange(0.6))
    }

    @Test
    fun `chart points follow the goal's direction`() {
        val gain = WeightGoal(GoalKind.Gain)
        assertTrue(gain.isOnTrack(70.4, previousKg = 70.0))
        assertFalse(gain.isOnTrack(69.8, previousKg = 70.0))
        assertTrue(gain.isOnTrack(69.8, previousKg = null))
    }

    @Test
    fun `reminders fire at the next time, today or tomorrow`() {
        val morning = LocalDateTime.of(2026, 11, 27, 6, 30)
        assertEquals(LocalDateTime.of(2026, 11, 27, 7, 0), nextOccurrence(morning, LocalTime.of(7, 0)))
        val later = LocalDateTime.of(2026, 11, 27, 7, 0)
        assertEquals(LocalDateTime.of(2026, 11, 28, 7, 0), nextOccurrence(later, LocalTime.of(7, 0)))
    }
}
