package com.vitrack.app.testing

import com.vitrack.app.data.activity.WorkoutKind
import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.activity.ActivitySnapshot
import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.activity.HeartSummary
import com.vitrack.app.data.activity.SleepStages
import com.vitrack.app.data.activity.SleepSummary
import com.vitrack.app.data.activity.Workout
import com.vitrack.app.data.profile.ProfileRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

val TODAY: LocalDate = LocalDate.of(2026, 11, 26)

fun day(date: LocalDate, strain: Double = 10.0, steps: Int = 8_000) = DailyActivity(
    date = date,
    sleepPerformance = 80,
    recovery = 60,
    strain = strain,
    steps = steps,
    calories = 2_000,
    averageHeartRate = 72,
    maxHeartRate = 150,
)

fun workout(name: String, strain: Double) =
    Workout(WorkoutKind.Other, name, LocalTime.of(7, 0), 30, 130, 150, strain, listOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f))

fun snapshot(
    week: List<DailyActivity> = (6 downTo 0).map { day(TODAY.minusDays(it.toLong())) },
    workouts: List<Workout> = emptyList(),
) = ActivitySnapshot(
    today = week.last(),
    stepGoal = 10_000,
    heart = HeartSummary(58, 62, listOf(60, 59, 61, 57, 60, 57, 58)),
    sleep = SleepSummary(460, SleepStages(0.05f, 0.48f, 0.24f, 0.23f)),
    workouts = workouts,
    lastSevenDays = week,
    syncedAt = LocalTime.of(8, 12),
)

class FakeActivityRepository(snapshot: ActivitySnapshot = snapshot()) : ActivityRepository {
    val snapshots = MutableStateFlow(snapshot)

    /** Days requested by each history call, to check the period wiring. */
    val historyRequests = mutableListOf<Int>()

    override fun observeSnapshot(): Flow<ActivitySnapshot> = snapshots

    /**
     * Days ending [TODAY]. Days in the snapshot's week keep their values; older
     * days get 1,000 steps per day back, so a range's days are easy to tell apart.
     */
    override fun observeHistory(days: Int): Flow<List<DailyActivity>> {
        historyRequests += days
        val week = snapshots.value.lastSevenDays.associateBy { it.date }
        return flowOf(
            (days - 1 downTo 0).map { back ->
                val date = TODAY.minusDays(back.toLong())
                week[date] ?: day(date, strain = back.toDouble() % 21, steps = back * 1_000)
            },
        )
    }
}

class FakeProfileRepository(name: String? = "Vernon") : ProfileRepository {
    val names = MutableStateFlow(name)
    override fun observeDisplayName(): Flow<String?> = names
}
