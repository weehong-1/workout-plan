package com.vitrack.app.data.activity

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Sample data matching the design canvas, until WHOOP is connected.
 * The last 7 days are the drawn values; older days are generated from the
 * date, so the same day always shows the same numbers.
 */
@Singleton
class SampleActivityRepository @Inject constructor(
    private val clock: Clock,
) : ActivityRepository {

    override fun observeSnapshot(): Flow<ActivitySnapshot> = flow {
        val week = history(7)
        emit(
            ActivitySnapshot(
                today = week.last(),
                stepGoal = 10_000,
                heart = HeartSummary(restingBpm = 58, hrvMs = 62, restingLast7Days = listOf(60, 59, 61, 57, 60, 57, 58)),
                sleep = SleepSummary(
                    durationMinutes = 7 * 60 + 40,
                    stages = SleepStages(awake = 0.05f, light = 0.48f, deep = 0.24f, rem = 0.23f),
                ),
                workouts = listOf(
                    Workout("Outdoor cycling", LocalTime.of(7, 10), 35, 138, 152, 9.4, listOf(0.10f, 0.22f, 0.38f, 0.22f, 0.08f)),
                    Workout("Walking", LocalTime.of(12, 40), 22, 101, 118, 3.1, listOf(0.45f, 0.40f, 0.15f, 0f, 0f)),
                ),
                lastSevenDays = week,
                syncedAt = LocalTime.of(8, 12),
            ),
        )
    }

    override fun observeHistory(days: Int): Flow<List<DailyActivity>> = flow { emit(history(days)) }

    private fun history(days: Int): List<DailyActivity> {
        val today = LocalDate.now(clock)
        return (days - 1 downTo 0).map { back -> day(today.minusDays(back.toLong()), back) }
    }

    private fun day(date: LocalDate, daysAgo: Int): DailyActivity {
        if (daysAgo < DRAWN_STRAIN.size) {
            val i = DRAWN_STRAIN.size - 1 - daysAgo
            return DailyActivity(
                date = date,
                sleepPerformance = if (daysAgo == 0) 86 else 78 + i,
                recovery = if (daysAgo == 0) 64 else 55 + i * 4,
                strain = DRAWN_STRAIN[i],
                steps = DRAWN_STEPS[i],
                calories = if (daysAgo == 0) 2_150 else 1_900 + i * 60,
                averageHeartRate = if (daysAgo == 0) 74 else 70 + i,
                maxHeartRate = if (daysAgo == 0) 152 else 140 + i * 3,
            )
        }
        val random = Random(date.toEpochDay())
        return DailyActivity(
            date = date,
            sleepPerformance = random.nextInt(60, 98),
            recovery = random.nextInt(30, 95),
            strain = (random.nextInt(50, 170) / 10.0),
            steps = random.nextInt(4_000, 13_000),
            calories = random.nextInt(1_800, 2_700),
            averageHeartRate = random.nextInt(66, 82),
            maxHeartRate = random.nextInt(120, 175),
        )
    }

    private companion object {
        val DRAWN_STRAIN = listOf(13.2, 15.8, 6.4, 10.9, 14.1, 8.7, 11.8)
        val DRAWN_STEPS = listOf(9_200, 11_400, 5_200, 7_800, 10_300, 5_700, 7_235)
    }
}
