package com.vitrack.app.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.profile.ProfileRepository
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.weight.WeightRepository
import com.vitrack.app.data.weight.WeightZoom
import com.vitrack.app.data.weight.groupWeighIns
import com.vitrack.app.data.weight.periodStartOf
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

    private val zoom = MutableStateFlow(WeightZoom.Weeks)

    val uiState: StateFlow<WeightUiState> = combine(
        weightRepository.observeWeighIns(),
        settingsRepository.weightUnit,
        zoom,
        profileRepository.observeDisplayName(),
    ) { weighIns, unit, zoom, name ->
        val latest = weighIns.firstOrNull() ?: return@combine WeightUiState.Empty(unit, name)
        val today = LocalDate.now(clock)
        val todayStart = periodStartOf(today, zoom, weekFields)
        val periods = groupWeighIns(weighIns, zoom, weekFields)
        val current = periods.first()
        val previous = previousOf(current, periods)
        val chart = chartWindow(zoom, todayStart, periods)
        WeightUiState.Ready(
            displayName = name,
            today = today,
            unit = unit,
            zoom = zoom,
            todayPeriodStart = todayStart,
            latest = latest,
            current = current,
            previous = previous,
            changeKg = previous?.let { current.averageKg - it.averageKg },
            chart = chart,
            trend = trendOf(zoom, chart),
            history = historyRows(periods, weekFields),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeightUiState.Loading,
    )

    fun selectZoom(newZoom: WeightZoom) {
        zoom.value = newZoom
    }
}
