package com.vitrack.app.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.profile.ProfileRepository
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.weight.WeightRepository
import com.vitrack.app.ui.common.ChartPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
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
        WeightUiState.Ready(
            displayName = name,
            today = today,
            unit = unit,
            latest = latest,
            weeklyChangeKg = weeklyChangeKg(weighIns),
            period = period,
            chartStart = start,
            chart = weighIns.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }.reversed(),
            history = weighIns,
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
