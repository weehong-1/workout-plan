package com.vitrack.app.data.weight

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleWeighInsTest {

    private val today = LocalDate.of(2026, 11, 27)
    private val samples = sampleWeighIns(today)

    @Test
    fun `covers about three months ending today at 78_4 kg with a photo`() {
        val latest = samples.last()
        assertEquals(today, latest.date)
        assertEquals(78.4, latest.weightKg, 0.0)
        assertEquals(1, latest.photoCount)
        assertTrue(samples.first().date >= today.minusDays(90))
        assertTrue(samples.size in 30..46)
    }

    @Test
    fun `one per day, oldest first, all valid weights`() {
        assertEquals(samples.size, samples.map { it.date }.toSet().size)
        assertEquals(samples.sortedBy { it.date }, samples)
        assertTrue(samples.all { it.weightKg in MIN_WEIGHT_KG..MAX_WEIGHT_KG })
    }

    @Test
    fun `trends down overall and is the same every time`() {
        assertTrue(samples.first().weightKg > samples.last().weightKg + 4)
        assertEquals(samples, sampleWeighIns(today))
    }
}
