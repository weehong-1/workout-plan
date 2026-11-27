package com.vitrack.app.data.weight

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleWeighInsTest {

    private val today = LocalDate.of(2026, 11, 27)
    private val samples = sampleWeighIns(today)

    @Test
    fun `covers three years ending today at 78_4 kg with a photo`() {
        val latest = samples.last()
        assertEquals(today, latest.date)
        assertEquals(78.4, latest.weightKg, 0.0)
        assertEquals(1, latest.photoCount)
        assertTrue(samples.first().date >= today.minusDays(SAMPLE_DAYS.toLong()))
        assertTrue(samples.first().date <= today.minusDays(SAMPLE_DAYS - 3L))
        assertTrue(samples.size in 400..560)
    }

    @Test
    fun `one per day, oldest first, all valid weights, photos only recently`() {
        assertEquals(samples.size, samples.map { it.date }.toSet().size)
        assertEquals(samples.sortedBy { it.date }, samples)
        assertTrue(samples.all { it.weightKg in MIN_WEIGHT_KG..MAX_WEIGHT_KG })
        assertTrue(samples.filter { it.photoCount > 0 }.all { it.date >= today.minusDays(90) })
    }

    @Test
    fun `rises to a peak then falls, and is the same every time`() {
        assertEquals(86.0, sampleTrendKg(200), 0.0001)
        assertTrue(sampleTrendKg(SAMPLE_DAYS) < sampleTrendKg(200))
        assertTrue(sampleTrendKg(0) < sampleTrendKg(90))
        assertEquals(samples, sampleWeighIns(today))
    }
}
