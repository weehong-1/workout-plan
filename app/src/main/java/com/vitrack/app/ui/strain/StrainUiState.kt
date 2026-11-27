package com.vitrack.app.ui.strain

import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.activity.Workout
import java.time.LocalDate

enum class ChartPeriod(val days: Int) {
    Week(7),
    Month(30),
    Year(365),
}

/** One bar: a day for Week and Month, a calendar month for Year. */
data class StrainBar(
    val start: LocalDate,
    val strain: Double,
    val isCurrent: Boolean,
)

sealed interface StrainUiState {
    data object Loading : StrainUiState

    data class Ready(
        val today: DailyActivity,
        val period: ChartPeriod,
        val bars: List<StrainBar>,
        val workouts: List<Workout>,
    ) : StrainUiState
}

/** WHOOP's strain bands. */
enum class StrainLevel { Light, Moderate, Strenuous, AllOut }

fun strainLevel(strain: Double): StrainLevel = when {
    strain < 10.0 -> StrainLevel.Light
    strain < 14.0 -> StrainLevel.Moderate
    strain < 18.0 -> StrainLevel.Strenuous
    else -> StrainLevel.AllOut
}
