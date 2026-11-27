package com.vitrack.app.ui.strain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.ui.common.ChartPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
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
class StrainViewModel @Inject constructor(
    repository: ActivityRepository,
) : ViewModel() {

    private val period = MutableStateFlow(ChartPeriod.Week)

    private val history = period.flatMapLatest { p ->
        repository.observeHistory(p.days).map { days -> p to days }
    }

    val uiState: StateFlow<StrainUiState> = combine(
        repository.observeSnapshot(),
        history,
    ) { snapshot, (p, days) ->
        StrainUiState.Ready(
            today = snapshot.today,
            period = p,
            bars = bars(p, days),
            workouts = snapshot.workouts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StrainUiState.Loading,
    )

    fun selectPeriod(newPeriod: ChartPeriod) {
        period.value = newPeriod
    }
}

/** Daily bars for Week and Month; monthly averages for the last 12 months for Year. */
internal fun bars(period: ChartPeriod, days: List<DailyActivity>): List<StrainBar> {
    if (days.isEmpty()) return emptyList()
    return when (period) {
        ChartPeriod.Week, ChartPeriod.Month -> days.mapIndexed { i, d ->
            StrainBar(d.date, d.strain, isCurrent = i == days.lastIndex)
        }
        ChartPeriod.Year -> {
            val current = YearMonth.from(days.last().date)
            days.groupBy { YearMonth.from(it.date) }
                .toSortedMap()
                .map { (month, inMonth) ->
                    StrainBar(month.atDay(1), inMonth.map { it.strain }.average(), isCurrent = month == current)
                }
                // 365 days reach into the same month a year ago; keep 12 months.
                .takeLast(12)
        }
    }
}
