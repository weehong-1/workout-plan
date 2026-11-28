package com.vitrack.app.ui.activity

import com.vitrack.app.data.activity.HeartSummary
import com.vitrack.app.data.activity.SleepSummary
import com.vitrack.app.data.activity.Workout
import java.time.LocalDate
import java.time.LocalTime

/** Days shown in the Steps card. */
sealed interface StepsRange {
    data object Last7Days : StepsRange
    data object Last30Days : StepsRange
    data class Custom(val start: LocalDate, val end: LocalDate) : StepsRange

    /** First and last day, both included. */
    fun bounds(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        Last7Days -> today.minusDays(6) to today
        Last30Days -> today.minusDays(29) to today
        is Custom -> start to end
    }
}

sealed interface ActivityUiState {
    data object Loading : ActivityUiState

    /** WHOOP isn't connected, so there is no activity to show. */
    data object NotConnected : ActivityUiState

    data class Ready(
        val date: LocalDate,
        val displayName: String?,
        val sleepPerformance: Int,
        val recovery: Int,
        val strain: Double,
        val steps: Int,
        val stepGoal: Int,
        val calories: Int,
        val averageHeartRate: Int,
        val heart: HeartSummary,
        val sleep: SleepSummary,
        /** Today's workouts, earliest first. */
        val workouts: List<Workout>,
        val stepsRange: StepsRange,
        /** One bar per day of [stepsRange], oldest first; the last is highlighted. */
        val stepsDays: List<DaySteps>,
        val averageSteps: Int,
        val syncedAt: LocalTime,
        /** False when a past day was picked from the calendar. */
        val isToday: Boolean = true,
        /** The real today, for the calendars; [date] is the day shown. */
        val today: LocalDate = date,
    ) : ActivityUiState {
        val stepProgress: Float get() = (steps.toFloat() / stepGoal).coerceIn(0f, 1f)
        val workoutMinutes: Int get() = workouts.sumOf { it.durationMinutes }
    }
}

data class DaySteps(
    val date: LocalDate,
    val steps: Int,
    val highlighted: Boolean,
)
