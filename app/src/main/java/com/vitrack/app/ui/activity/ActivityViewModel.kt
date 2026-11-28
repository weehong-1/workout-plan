package com.vitrack.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.activity.ActivitySnapshot
import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.profile.ProfileRepository
import com.vitrack.app.data.settings.SettingsRepository
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    activityRepository: ActivityRepository,
    profileRepository: ProfileRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val stepsRange = MutableStateFlow<StepsRange>(StepsRange.Last7Days)

    /** The day picked from the calendar; null is today. */
    private val pickedDay = MutableStateFlow<LocalDate?>(null)

    /** The picked day's snapshot; Pending while it loads, so the old day isn't shown as the new one. */
    private val snapshot = pickedDay.flatMapLatest { day ->
        activityRepository.observeSnapshot(day).map<ActivitySnapshot?, DayLoad> { DayLoad.Loaded(it, day == null) }
            .onStart { emit(DayLoad.Pending) }
    }

    private val stepsHistory = stepsRange.flatMapLatest { range ->
        val today = LocalDate.now(clock)
        val (start, end) = range.bounds(today)
        val days = ChronoUnit.DAYS.between(start, today).toInt() + 1
        activityRepository.observeHistory(days).map { history ->
            range to history.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        }
    }

    val uiState: StateFlow<ActivityUiState> = combine(
        snapshot,
        profileRepository.observeDisplayName(),
        stepsHistory,
        settingsRepository.stepGoal,
    ) { loaded, name, (range, days), stepGoal ->
        when {
            loaded !is DayLoad.Loaded -> ActivityUiState.Loading
            loaded.snapshot == null -> ActivityUiState.NotConnected
            // The goal set in Profile, over the one that came with the data.
            else -> loaded.snapshot.copy(stepGoal = stepGoal).toUiState(name, range, days)
                .copy(isToday = loaded.isToday, today = LocalDate.now(clock))
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActivityUiState.Loading,
        )

    fun selectStepsRange(range: StepsRange) {
        stepsRange.value = range
    }

    /** Shows [day]; today (or a day after it) goes back to today. */
    fun selectDay(day: LocalDate) {
        pickedDay.value = day.takeIf { it.isBefore(LocalDate.now(clock)) }
    }

    fun showToday() {
        pickedDay.value = null
    }

    private sealed interface DayLoad {
        data object Pending : DayLoad
        data class Loaded(val snapshot: ActivitySnapshot?, val isToday: Boolean) : DayLoad
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
