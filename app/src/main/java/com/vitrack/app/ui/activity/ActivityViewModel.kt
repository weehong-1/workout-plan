package com.vitrack.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.activity.ActivitySnapshot
import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    activityRepository: ActivityRepository,
    profileRepository: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val stepsRange = MutableStateFlow<StepsRange>(StepsRange.Last7Days)

    private val stepsHistory = stepsRange.flatMapLatest { range ->
        val today = LocalDate.now(clock)
        val (start, end) = range.bounds(today)
        val days = ChronoUnit.DAYS.between(start, today).toInt() + 1
        activityRepository.observeHistory(days).map { history ->
            range to history.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        }
    }

    val uiState: StateFlow<ActivityUiState> = combine(
        activityRepository.observeSnapshot(),
        profileRepository.observeDisplayName(),
        stepsHistory,
    ) { snapshot, name, (range, days) -> snapshot.toUiState(name, range, days) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActivityUiState.Loading,
        )

    fun selectStepsRange(range: StepsRange) {
        stepsRange.value = range
    }
}

internal fun ActivitySnapshot.toUiState(
    displayName: String?,
    range: StepsRange,
    rangeDays: List<DailyActivity>,
): ActivityUiState.Ready {
    val stepsDays = rangeDays.mapIndexed { i, d -> DaySteps(d.date, d.steps, highlighted = i == rangeDays.lastIndex) }
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
        workouts = workouts.sortedBy { it.start },
        stepsRange = range,
        stepsDays = stepsDays,
        averageSteps = if (stepsDays.isEmpty()) 0 else stepsDays.map { it.steps }.average().roundToInt(),
        syncedAt = syncedAt,
    )
}
