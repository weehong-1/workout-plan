package com.vitrack.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.activity.ActivitySnapshot
import com.vitrack.app.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ActivityViewModel @Inject constructor(
    activityRepository: ActivityRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<ActivityUiState> = combine(
        activityRepository.observeSnapshot(),
        profileRepository.observeDisplayName(),
    ) { snapshot, name -> snapshot.toUiState(name) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActivityUiState.Loading,
        )
}

internal fun ActivitySnapshot.toUiState(displayName: String?): ActivityUiState.Ready {
    val week = lastSevenDays.map { DaySteps(it.date, it.steps, isToday = it.date == today.date) }
    return ActivityUiState.Ready(
        date = today.date,
        displayName = displayName,
        sleepPerformance = today.sleepPerformance,
        recovery = today.recovery,
        strain = today.strain,
        steps = today.steps,
        stepGoal = stepGoal,
        calories = today.calories,
        averageHeartRate = today.averageHeartRate,
        heart = heart,
        sleep = sleep,
        topWorkout = workouts.maxByOrNull { it.strain },
        week = week,
        weeklyAverageSteps = if (week.isEmpty()) 0 else week.map { it.steps }.average().roundToInt(),
        syncedAt = syncedAt,
    )
}
