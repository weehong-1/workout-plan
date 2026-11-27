package com.vitrack.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.food.FoodRepository
import com.vitrack.app.data.profile.ProfileRepository
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.share.ShareRepository
import com.vitrack.app.data.share.SharePeriod
import com.vitrack.app.data.weight.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val weightRepository: WeightRepository,
    private val foodRepository: FoodRepository,
    settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository,
    private val shareRepository: ShareRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now(clock)
    private val period = MutableStateFlow<SharePeriod>(SharePeriod.Month(YearMonth.now(clock)))

    private data class Summaries(
        val period: SharePeriod,
        val activity: ActivityAverages?,
        val weight: WeightSummary?,
        val food: FoodSummary,
    )

    private val summaries = period.flatMapLatest { p ->
        val today = today
        val range = daysSoFar(p, today)
        combine(
            activityRepository.observeHistory(daysBack(p.start, today).coerceAtLeast(1)),
            weightRepository.observeWeighIns(),
            foodRepository.observeCounts(p.start),
        ) { history, weighIns, counts ->
            Summaries(
                period = p,
                activity = activityAverages(history.filter { it.date in range }),
                weight = weightSummary(weighIns.filter { it.date in range }),
                food = foodSummary(p, today, counts),
            )
        }
    }

    val uiState: StateFlow<ReviewUiState> = combine(
        summaries,
        settingsRepository.weightUnit,
        profileRepository.observeDisplayName(),
        shareRepository.observeLinks(),
        shareRepository.isSignedIn,
    ) { s, unit, name, links, signedIn ->
        ReviewUiState(
            isLoading = false,
            displayName = name,
            today = today,
            unit = unit,
            period = s.period,
            activity = s.activity,
            weight = s.weight,
            food = s.food,
            links = links,
            sharingAvailable = shareRepository.isAvailable,
            isSignedIn = signedIn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    /** Month keeps the year; Year → Month opens this month, or December of a past year. */
    fun showYear(year: Boolean) {
        val current = period.value
        period.value = when {
            year && current is SharePeriod.Month -> SharePeriod.Year(current.month.year)
            !year && current is SharePeriod.Year ->
                SharePeriod.Month(if (current.year == today.year) YearMonth.from(today) else YearMonth.of(current.year, 12))
            else -> current
        }
    }

    fun previous() {
        period.value = previousPeriod(period.value)
    }

    fun next() {
        val next = nextPeriod(period.value)
        if (next.start <= today) period.value = next
    }

    fun revoke(id: String) {
        viewModelScope.launch { runCatching { shareRepository.revoke(id) } }
    }
}
