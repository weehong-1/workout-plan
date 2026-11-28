package com.vitrack.app.data.whoop

import com.vitrack.app.data.activity.WorkoutKind
import com.vitrack.app.data.backend.Backend
import com.vitrack.app.data.backend.BackendConfig
import com.vitrack.app.data.backend.SupabaseClient
import com.vitrack.app.testing.BASE_URL
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeTransport
import com.vitrack.app.testing.FakeWhoopRepository
import com.vitrack.app.testing.InMemorySecureStore
import com.vitrack.app.testing.tokenJson
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WhoopTest {

    /** WHOOP's v2 sample shapes, trimmed: today's cycle started at 23:30 last night (+08:00). */
    private val json = """
        {"cycles":[
          {"id":2,"user_id":1,"start":"2026-11-27T15:30:00.000Z","timezone_offset":"+08:00","score_state":"SCORED",
           "score":{"strain":11.8,"kilojoule":8368.0,"average_heart_rate":74,"max_heart_rate":152},"step_count":7235},
          {"id":1,"start":"2026-11-26T16:10:00.000Z","end":"2026-11-27T15:30:00.000Z","timezone_offset":"+08:00",
           "score":{"strain":8.7,"kilojoule":7950.0,"average_heart_rate":70,"max_heart_rate":140},"step_count":null}],
         "recoveries":[
          {"cycle_id":2,"sleep_id":"a","score_state":"SCORED","score":{"recovery_score":64,"resting_heart_rate":58.0,"hrv_rmssd_milli":61.6}},
          {"cycle_id":1,"score":{"recovery_score":55,"resting_heart_rate":60.0,"hrv_rmssd_milli":50.0}}],
         "sleeps":[
          {"id":"a","cycle_id":2,"start":"2026-11-27T15:30:00.000Z","end":"2026-11-27T23:30:00.000Z","nap":false,
           "score":{"stage_summary":{"total_in_bed_time_milli":28800000,"total_awake_time_milli":1200000,
             "total_light_sleep_time_milli":13200000,"total_slow_wave_sleep_time_milli":7200000,"total_rem_sleep_time_milli":7200000},
             "sleep_performance_percentage":86}},
          {"id":"n","cycle_id":2,"start":"2026-11-28T06:00:00.000Z","end":"2026-11-28T06:30:00.000Z","nap":true,
           "score":{"stage_summary":{"total_light_sleep_time_milli":1800000}}}],
         "workouts":[
          {"id":"w","start":"2026-11-27T23:10:00.000Z","end":"2026-11-27T23:45:00.000Z","timezone_offset":"+08:00","sport_name":"cycling",
           "score_state":"SCORED","score":{"strain":9.4,"average_heart_rate":138,"max_heart_rate":152,
             "zone_durations":{"zone_zero_milli":0,"zone_one_milli":210000,"zone_two_milli":462000,"zone_three_milli":798000,"zone_four_milli":462000,"zone_five_milli":168000}}}]}
    """.trimIndent()

    private val records = SupabaseClient.json.decodeFromString<WhoopRecords>(json)
    private val nov28 = LocalDate.of(2026, 11, 28)

    @Test
    fun `a cycle belongs to the day you wake up in`() {
        assertEquals(nov28, WhoopMapper.cycleDate("2026-11-27T15:30:00.000Z", "+08:00")) // 23:30 on the 27th
        assertEquals(nov28, WhoopMapper.cycleDate("2026-11-27T17:10:00.000Z", "+08:00")) // 01:10 on the 28th
        assertEquals(LocalDate.of(2026, 11, 27), WhoopMapper.cycleDate("2026-11-26T16:10:00.000Z", "+08:00"))
    }

    @Test
    fun `days take strain, steps, calories, recovery and the main sleep, with gaps as zeros`() {
        val days = WhoopMapper.days(records, LocalDate.of(2026, 11, 26), nov28)

        assertEquals(listOf(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 27), nov28), days.map { it.date })
        assertEquals(0.0, days[0].strain, 0.0)
        val today = days[2]
        assertEquals(11.8, today.strain, 0.0)
        assertEquals(7235, today.steps)
        assertEquals(2000, today.calories) // 8368 kJ
        assertEquals(64, today.recovery)
        assertEquals(86, today.sleepPerformance)
        assertEquals(460, today.sleepMinutes) // light + deep + REM, not awake or the nap
        assertEquals(0, days[1].steps) // no step data
    }

    @Test
    fun `the snapshot has today's heart, last night's stages and today's workouts`() {
        val snapshot = WhoopMapper.snapshot(records, nov28, 10_000, LocalTime.of(8, 12))

        assertEquals(58, snapshot.heart.restingBpm)
        assertEquals(62, snapshot.heart.hrvMs)
        assertEquals(listOf(0, 0, 0, 0, 0, 60, 58), snapshot.heart.restingLast7Days)
        assertEquals(460, snapshot.sleep.durationMinutes)
        assertEquals(0.25f, snapshot.sleep.stages.deep, 0.001f)
        val ride = snapshot.workouts.single()
        assertEquals(WorkoutKind.Cycling, ride.kind)
        assertEquals("Cycling", ride.name)
        assertEquals(LocalTime.of(7, 10), ride.start)
        assertEquals(35, ride.durationMinutes)
        assertEquals(1f, ride.zones.sum(), 0.001f)
        assertEquals(7, snapshot.lastSevenDays.size)
    }

    @Test
    fun `a past day shows that day and its week`() {
        val nov27 = LocalDate.of(2026, 11, 27)
        val snapshot = WhoopMapper.snapshot(records, nov27, 10_000, LocalTime.of(8, 0))

        assertEquals(nov27, snapshot.today.date)
        assertEquals(8.7, snapshot.today.strain, 0.0)
        assertEquals(55, snapshot.today.recovery)
        assertTrue(snapshot.workouts.isEmpty()) // the ride was on the 28th
        assertEquals(nov27, snapshot.lastSevenDays.last().date)
    }

    @Test
    fun `return links are read by their result`() {
        assertEquals(WhoopReturn.Connected, whoopReturnOf("vitrack://whoop-callback?result=connected"))
        assertEquals(WhoopReturn.Denied, whoopReturnOf("vitrack://whoop-callback?result=denied"))
        assertEquals(WhoopReturn.Failed, whoopReturnOf("vitrack://whoop-callback"))
        assertNull(whoopReturnOf("vitrack://login-callback?code=x"))
    }

    private val transport = FakeTransport()
    private val store = InMemorySecureStore()
    private val client = SupabaseClient(BackendConfig(BASE_URL, "key", "https://web.test"), transport, store, FIXED_CLOCK)

    private suspend fun signedIn(): SupabaseWhoopRepository {
        transport.on("POST", "/auth/v1/verify", 200, tokenJson())
        client.verify("vernon@example.com", "123456")
        return SupabaseWhoopRepository(Backend(client), store)
    }

    @Test
    fun `status, connect and data go to the function, signed in`() = runTest {
        val whoop = signedIn()
        transport.on("POST", "/functions/v1/whoop-callback") { r ->
            val body = r.body!!.decodeToString()
            com.vitrack.app.data.backend.HttpResponse(
                200,
                when {
                    "\"status\"" in body -> """{"connected":true}"""
                    "\"connect\"" in body -> """{"url":"https://api.prod.whoop.com/oauth/oauth2/auth?state=s"}"""
                    else -> json
                }.encodeToByteArray(),
            )
        }

        whoop.refreshStatus()
        assertEquals(WhoopStatus.Connected, whoop.status.value)
        assertEquals("1", store.values["whoop.connected"])
        assertTrue(whoop.connectUrl().startsWith("https://api.prod.whoop.com/oauth/oauth2/auth"))
        assertEquals(2, whoop.records(FIXED_CLOCK.instant().minusSeconds(86_400), FIXED_CLOCK.instant()).cycles.size)

        val sent = transport.sent("POST", "/functions/v1/whoop-callback")
        assertTrue(sent.all { it.headers["Authorization"] == "Bearer access-1" })
        assertTrue(sent.last().body!!.decodeToString().contains("\"action\":\"data\""))
    }

    @Test
    fun `a connection WHOOP no longer accepts turns into not connected`() = runTest {
        val whoop = signedIn()
        store.put("whoop.connected", "1")
        transport.on("POST", "/functions/v1/whoop-callback", 409, """{"error":"not_connected"}""")

        try {
            whoop.records(FIXED_CLOCK.instant().minusSeconds(60), FIXED_CLOCK.instant())
            fail("expected WhoopNotConnectedException")
        } catch (e: WhoopNotConnectedException) {
            assertEquals(WhoopStatus.NotConnected, whoop.status.value)
            assertNull(store.values["whoop.connected"])
        }
    }

    @Test
    fun `without a server WHOOP can't be set up`() {
        assertEquals(WhoopStatus.NotSetUp, SupabaseWhoopRepository(Backend(null), store).status.value)
    }

    @Test
    fun `there is no activity until WHOOP is connected, then it comes from WHOOP`() = runTest {
        val fake = FakeWhoopRepository().apply { records = this@WhoopTest.records }
        val clock = java.time.Clock.fixed(nov28.atTime(9, 0).toInstant(java.time.ZoneOffset.ofHours(8)), java.time.ZoneOffset.ofHours(8))
        val activity = WhoopActivityRepository(fake, clock)

        assertNull(activity.observeSnapshot().first())
        assertTrue(activity.observeHistory(30).first().isEmpty())
        assertTrue(fake.recordRequests.isEmpty())

        fake.statusFlow.value = WhoopStatus.Connected
        val snapshot = activity.observeSnapshot().first()!!
        assertEquals(11.8, snapshot.today.strain, 0.0)
        assertEquals(64, snapshot.today.recovery)
        // From the evening before the week's first day.
        assertEquals(nov28.minusDays(7).atStartOfDay(clock.zone).toInstant(), fake.recordRequests.single().first)

        activity.observeHistory(7).first()
        assertEquals(1, fake.recordRequests.size) // reused for a few minutes
    }
}
