package com.vitrack.app.ui.weight

import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.ui.common.ChartPeriod
import java.time.LocalDate

sealed interface WeightUiState {
    data object Loading : WeightUiState

    data class Empty(val unit: WeightUnit, val displayName: String?) : WeightUiState

    data class Ready(
        val displayName: String?,
        val today: LocalDate,
        val unit: WeightUnit,
        val latest: WeighIn,
        /** Latest minus the last weigh-in at least a week before it; null when there is none. */
        val weeklyChangeKg: Double?,
        val period: ChartPeriod,
        val chartStart: LocalDate,
        /** Weigh-ins inside the chart period, oldest first. */
        val chart: List<WeighIn>,
        /** All weigh-ins, newest first. */
        val history: List<WeighIn>,
    ) : WeightUiState
}

/**
 * Change from the most recent weigh-in at least 7 days before the latest one.
 * [newestFirst] must be sorted newest first.
 */
internal fun weeklyChangeKg(newestFirst: List<WeighIn>): Double? {
    val latest = newestFirst.firstOrNull() ?: return null
    val reference = newestFirst.firstOrNull { !it.date.isAfter(latest.date.minusDays(7)) } ?: return null
    return latest.weightKg - reference.weightKg
}

/** First day shown for [period], ending [today]. */
internal fun chartStart(period: ChartPeriod, today: LocalDate): LocalDate =
    today.minusDays(period.days - 1L)
