package com.vitrack.app.data.share

import com.vitrack.app.data.backend.SupabaseClient
import com.vitrack.app.data.food.FoodLabel
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.day
import com.vitrack.app.testing.foodPhoto
import com.vitrack.app.testing.weighIn
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TODAY is 26 Nov 2026. */
class SharedReviewTest {

    private val november = SharePeriod.Month(YearMonth.of(2026, 11))
    private val folder = "9b2d1c4e-0000-4000-8000-00000000abcd"

    private fun source(period: SharePeriod = november, unit: WeightUnit = WeightUnit.Kg) = ShareSource(
        period = period,
        today = TODAY,
        unit = unit,
        activity = (0L..40L).map { day(TODAY.minusDays(it), strain = 12.34, steps = 9_000).copy(sleepMinutes = 450) },
        weighIns = listOf(
            weighIn(LocalDate.of(2026, 10, 30), 82.0, photoIds = listOf(9)),
            weighIn(LocalDate.of(2026, 11, 2), 80.2, photoIds = listOf(1, 2)),
            weighIn(LocalDate.of(2026, 11, 12), 79.4),
            weighIn(TODAY, 78.4, photoIds = listOf(3)),
        ),
        foodPhotos = listOf(
            foodPhoto(10, TODAY.atTime(8, 5), FoodLabel.Breakfast),
            foodPhoto(11, TODAY.atTime(12, 30)),
            foodPhoto(12, LocalDate.of(2026, 11, 3).atTime(19, 0), FoodLabel.Dinner),
            foodPhoto(13, LocalDate.of(2026, 10, 31).atTime(19, 0)),
        ),
    )

    private fun build(
        contents: ShareContents = ShareContents(weightPhotos = true),
        source: ShareSource = source(),
        expiresOn: LocalDate? = LocalDate.of(2026, 12, 31),
    ) = buildSharedReview(source, contents, expiresOn, linkId = "link-1", userFolder = folder)

    @Test
    fun `the JSON has the fields the web page reads`() {
        val json = SupabaseClient.json.encodeToString(build().summary)
        val root = SupabaseClient.json.parseToJsonElement(json).jsonObject

        assertEquals(setOf("version", "period", "expiresOn", "activity", "weight", "food"), root.keys)
        assertEquals(setOf("kind", "label", "start", "end", "days"), root.obj("period").keys)
        assertTrue(root.obj("activity").keys.containsAll(listOf("averageSteps", "averageSleepMinutes", "averageMoveKcal", "ringsClosedDays", "daily")))
        assertEquals(setOf("unit", "count", "first", "last", "change", "entries", "firstPhoto", "lastPhoto"), root.obj("weight").keys)
        assertEquals(setOf("daysWithPhotos", "photos"), root.obj("food").keys)
        // No move calories from WHOOP: null, not left out.
        assertEquals(JsonNull, root.obj("activity")["averageMoveKcal"])
    }

    @Test
    fun `period covers the month with the days so far`() {
        val period = build().summary.period

        assertEquals("month", period.kind)
        assertEquals("2026-11-01", period.start)
        assertEquals("2026-11-30", period.end)
        assertEquals(26, period.days)
        assertEquals("2026-12-31", build().summary.expiresOn)
        assertNull(build(expiresOn = null).summary.expiresOn)
    }

    @Test
    fun `activity keeps the period's days with WHOOP figures`() {
        val activity = build().summary.activity!!

        assertEquals(26, activity.daily.size)
        assertEquals("2026-11-01", activity.daily.first().date)
        assertEquals(9_000, activity.averageSteps)
        assertEquals(450, activity.averageSleepMinutes)
        assertEquals(12.3, activity.averageStrain!!, 0.0)
        assertEquals(60, activity.averageRecovery)
        assertEquals("whoop", activity.source)
        assertEquals(0, activity.ringsClosedDays)
    }

    @Test
    fun `weight runs first to last in the period, in the chosen unit`() {
        val weight = build(source = source(unit = WeightUnit.Lb)).summary.weight!!

        assertEquals("lb", weight.unit)
        assertEquals(3, weight.count)
        assertEquals(176.8, weight.first.value, 0.0)
        assertEquals(172.8, weight.last.value, 0.0)
        assertEquals(-4.0, weight.change, 0.0)
        assertEquals(listOf("2026-11-02", "2026-11-12", "2026-11-26"), weight.entries.map { it.date })
    }

    @Test
    fun `weight photos are the first photo at each end, uploaded only when included`() {
        val with = build()
        val weight = with.summary.weight!!

        assertEquals("$folder/share-link-1-w1.jpg", weight.firstPhoto!!.path)
        assertEquals("2026-11-02", weight.firstPhoto!!.date)
        assertEquals("$folder/share-link-1-w3.jpg", weight.lastPhoto!!.path)

        val without = build(contents = ShareContents(weightPhotos = false, foodPhotos = false))
        assertNull(without.summary.weight!!.firstPhoto)
        assertTrue(without.uploads.isEmpty())
    }

    @Test
    fun `food lists the period's photos with time and label`() {
        val food = build().summary.food!!

        assertEquals(2, food.daysWithPhotos)
        assertEquals(listOf("f12", "f10", "f11"), food.photos.map { it.path.substringAfterLast('-').removeSuffix(".jpg") })
        assertEquals("08:05", food.photos[1].time)
        assertEquals("Breakfast", food.photos[1].label)
        assertNull(food.photos[2].label)
    }

    @Test
    fun `left out items are null and upload nothing`() {
        val built = build(contents = ShareContents(activity = false, weight = false, weightPhotos = false, foodPhotos = false))

        assertNull(built.summary.activity)
        assertNull(built.summary.weight)
        assertNull(built.summary.food)
        assertTrue(built.uploads.isEmpty())
    }

    @Test
    fun `every upload sits in the user's folder as the schema requires`() {
        val uploads = build().uploads

        assertEquals(5, uploads.size)
        uploads.forEach {
            assertTrue(it.path, it.path.startsWith("$folder/share-") && it.path.endsWith(".jpg"))
            assertEquals(1, it.path.count { c -> c == '/' })
            assertTrue(!it.path.contains(".."))
        }
    }

    @Test
    fun `a year covers January to today`() {
        val built = build(source = source(period = SharePeriod.Year(2026)))

        assertEquals("year", built.summary.period.kind)
        assertEquals("2026", built.summary.period.label)
        assertEquals(330, built.summary.period.days)
        assertEquals(4, built.summary.weight!!.count)
        assertEquals(3, built.summary.food!!.daysWithPhotos)
    }

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject
}
