package com.vitrack.app.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.profile.ProfileRepository
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.weight.WeightRepository
import com.vitrack.app.data.weight.groupByWeek
import com.vitrack.app.data.weight.weekStartOf
import com.vitrack.app.ui.common.ChartPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.WeekFields
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WeightViewModel @Inject constructor(
    weightRepository: WeightRepository,
    settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository,
    private val clock: Clock,
    private val weekFields: WeekFields,
) : ViewModel() {

    private val period = MutableStateFlow(ChartPeriod.Month)

    val uiState: StateFlow<WeightUiState> = combine(
        weightRepository.observeWeighIns(),
        settingsRepository.weightUnit,
        period,
        profileRepository.observeDisplayName(),
    ) { weighIns, unit, period, name ->
        val latest = weighIns.firstOrNull() ?: return@combine WeightUiState.Empty(unit, name)
        val today = LocalDate.now(clock)
        val start = chartStart(period, today)
        val weeks = groupByWeek(weighIns, weekFields)
        val changes = weekChanges(weeks)
        // Each row's change is against the weigh-in before it, even across weeks.
        val rows = historyRows(weighIns).associateBy { it.entry.date }
        WeightUiState.Ready(
            displayName = name,
            today = today,
            thisWeekStart = weekStartOf(today, weekFields),
            unit = unit,
            latest = latest,
            currentWeek = weeks.first(),
            weeklyChangeKg = changes.first(),
            period = period,
            chartStart = start,
            chart = chartPoints(period, start, today, weighIns, weeks),
            weeks = weeks.mapIndexed { i, week ->
                WeekGroup(week, changes[i], week.entries.map { rows.getValue(it.date) })
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeightUiState.Loading,
    )

    fun selectPeriod(newPeriod: ChartPeriod) {
        period.value = newPeriod
    }
}
