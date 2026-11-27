package com.vitrack.app.ui.activity

import com.vitrack.app.data.activity.HeartSummary
import com.vitrack.app.data.activity.SleepStages
import com.vitrack.app.data.activity.SleepSummary
import com.vitrack.app.data.activity.Workout
import java.time.LocalDate
import java.time.LocalTime

/** The design canvas's sample values, for @Preview only. */
internal val PreviewActivityState: ActivityUiState.Ready = run {
    val today = LocalDate.of(2026, 11, 26)
    val steps = listOf(9_200, 11_400, 5_200, 7_800, 10_300, 5_700, 7_235)
    ActivityUiState.Ready(
        date = today,
        displayName = "Vernon",
        sleepPerformance = 86,
        recovery = 64,
        strain = 11.8,
        steps = 7_235,
        stepGoal = 10_000,
        calories = 2_150,
        averageHeartRate = 74,
        heart = HeartSummary(58, 62, listOf(60, 59, 61, 57, 60, 57, 58)),
        sleep = SleepSummary(460, SleepStages(0.05f, 0.48f, 0.24f, 0.23f)),
        topWorkout = Workout("Outdoor cycling", LocalTime.of(7, 10), 35, 138, 152, 9.4, listOf(0.10f, 0.22f, 0.38f, 0.22f, 0.08f)),
        week = steps.mapIndexed { i, s -> DaySteps(today.minusDays((6 - i).toLong()), s, isToday = i == 6) },
        weeklyAverageSteps = 8_120,
        syncedAt = LocalTime.of(8, 12),
    )
}
