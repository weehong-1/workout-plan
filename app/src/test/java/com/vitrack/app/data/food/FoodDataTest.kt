package com.vitrack.app.data.food

import java.time.LocalTime
import org.junit.Assert.assertEquals
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
}
