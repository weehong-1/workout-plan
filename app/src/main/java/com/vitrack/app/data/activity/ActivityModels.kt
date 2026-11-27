package com.vitrack.app.data.activity

import java.time.LocalDate
import java.time.LocalTime

/** One day of WHOOP activity data. */
data class DailyActivity(
    val date: LocalDate,
    /** Sleep performance, 0 to 100. */
    val sleepPerformance: Int,
    /** Recovery, 0 to 100. */
    val recovery: Int,
    /** Day strain on WHOOP's 0 to 21 scale. */
    val strain: Double,
    val steps: Int,
    val calories: Int,
    val averageHeartRate: Int,
    val maxHeartRate: Int,
    /** Time asleep the night before, in minutes. */
    val sleepMinutes: Int = 0,
)

data class HeartSummary(
    val restingBpm: Int,
    val hrvMs: Int,
    /** Resting heart rate for the last 7 days, oldest first. */
    val restingLast7Days: List<Int>,
)

/** Share of the night in each stage; the four add up to 1. */
data class SleepStages(
    val awake: Float,
    val light: Float,
    val deep: Float,
    val rem: Float,
)

data class SleepSummary(
    val durationMinutes: Int,
    val stages: SleepStages,
)

enum class WorkoutKind { Cycling, Walking, Other }

data class Workout(
    val kind: WorkoutKind,
    val name: String,
    val start: LocalTime,
    val durationMinutes: Int,
    val averageHeartRate: Int,
    val maxHeartRate: Int,
    val strain: Double,
    /** Share of time in heart rate zones 1 to 5. */
    val zones: List<Float>,
)

/** Everything the Activity tab shows for today. */
data class ActivitySnapshot(
    val today: DailyActivity,
    val stepGoal: Int,
    val heart: HeartSummary,
    val sleep: SleepSummary,
    val workouts: List<Workout>,
    /** The last 7 days including today, oldest first. */
    val lastSevenDays: List<DailyActivity>,
    val syncedAt: LocalTime,
)

/** WHOOP's strain scale tops out at 21. */
const val MAX_STRAIN = 21.0
