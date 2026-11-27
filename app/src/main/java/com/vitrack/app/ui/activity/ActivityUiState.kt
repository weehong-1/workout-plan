package com.vitrack.app.ui.activity

import com.vitrack.app.data.activity.HeartSummary
import com.vitrack.app.data.activity.SleepSummary
import com.vitrack.app.data.activity.Workout
import java.time.LocalDate
import java.time.LocalTime

sealed interface ActivityUiState {
    data object Loading : ActivityUiState

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
        /** The day's workout with the highest strain, if any. */
        val topWorkout: Workout?,
        val week: List<DaySteps>,
        val weeklyAverageSteps: Int,
        val syncedAt: LocalTime,
    ) : ActivityUiState {
        val stepProgress: Float get() = (steps.toFloat() / stepGoal).coerceIn(0f, 1f)
    }
}

data class DaySteps(
    val date: LocalDate,
    val steps: Int,
    val isToday: Boolean,
)
