package com.vitrack.app.ui.strain

import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.activity.Workout
import com.vitrack.app.ui.common.ChartPeriod
import java.time.LocalDate

/** One bar: a day for Week and Month, a calendar month for Year. */
data class StrainBar(
    val start: LocalDate,
    val strain: Double,
    val isCurrent: Boolean,
)

sealed interface StrainUiState {
    data object Loading : StrainUiState

    /** WHOOP isn't connected, so there is no strain to show. */
    data object NotConnected : StrainUiState

    data class Ready(
        val today: DailyActivity,
        /** Average day strain over the last 7 days, today included. */
        val weeklyAverageStrain: Double,
        val stepGoal: Int,
        val period: ChartPeriod,
        val bars: List<StrainBar>,
        val workouts: List<Workout>,
    ) : StrainUiState {
        val stepProgress: Float get() = (today.steps.toFloat() / stepGoal).coerceIn(0f, 1f)
        val comparedToAverage: Comparison get() = compare(today.strain, weeklyAverageStrain)
    }
}

enum class Comparison { Above, Below, Level }

/** Level when the two round to the same tenth, as shown. */
internal fun compare(value: Double, average: Double): Comparison {
    val diff = Math.round(value * 10) - Math.round(average * 10)
    return when {
        diff > 0 -> Comparison.Above
        diff < 0 -> Comparison.Below
        else -> Comparison.Level
    }
}

/** WHOOP's strain bands. */
enum class StrainLevel { Light, Moderate, Strenuous, AllOut }

fun strainLevel(strain: Double): StrainLevel = when {
    strain < 10.0 -> StrainLevel.Light
    strain < 14.0 -> StrainLevel.Moderate
    strain < 18.0 -> StrainLevel.Strenuous
    else -> StrainLevel.AllOut
}
