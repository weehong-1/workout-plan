package com.vitrack.app.ui.review.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.share.NewShareLink
import com.vitrack.app.data.share.ShareContents
import com.vitrack.app.data.share.SharePeriod
import com.vitrack.app.data.share.ShareRepository
import com.vitrack.app.data.share.SharingNotSetUpException
import com.vitrack.app.ui.navigation.ShareRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainerLinkState(
    val today: LocalDate,
    val trainerName: String = "",
    val period: SharePeriod,
    val contents: ShareContents = ShareContents(),
    val expires: Boolean = true,
    val expiresOn: LocalDate,
    val isCreating: Boolean = false,
    val result: CreateResult? = null,
) {
    val canCreate: Boolean get() = trainerName.isNotBlank() && !contents.isEmpty && !isCreating
}

sealed interface CreateResult {
    data object Created : CreateResult
    data object NotSetUp : CreateResult
    data object Failed : CreateResult
}

/** Default expiry: the end of the month after the period ends, never in the past. */
internal fun defaultExpiry(period: SharePeriod, today: LocalDate): LocalDate {
    val afterPeriod = YearMonth.from(period.end).plusMonths(1).atEndOfMonth()
    return if (afterPeriod.isAfter(today)) afterPeriod else YearMonth.from(today).plusMonths(1).atEndOfMonth()
}

internal fun ShareRoute.toPeriod(): SharePeriod =
    if (month == 0) SharePeriod.Year(year) else SharePeriod.Month(YearMonth.of(year, month))

@HiltViewModel
class TrainerLinkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ShareRepository,
    clock: Clock,
) : ViewModel() {

    private val _state = run {
        val today = LocalDate.now(clock)
        // Read the route's args by key (same as toRoute<ShareRoute>(), without needing a Bundle).
        val period = ShareRoute(
            year = checkNotNull(savedStateHandle.get<Int>("year")),
            month = checkNotNull(savedStateHandle.get<Int>("month")),
        ).toPeriod()
        MutableStateFlow(TrainerLinkState(today = today, period = period, expiresOn = defaultExpiry(period, today)))
    }
    val state: StateFlow<TrainerLinkState> = _state.asStateFlow()

    fun setTrainerName(name: String) = _state.update { it.copy(trainerName = name.take(60)) }

    fun setPeriod(period: SharePeriod) = _state.update {
        it.copy(period = period, expiresOn = defaultExpiry(period, it.today))
    }

    fun setContents(contents: ShareContents) = _state.update { it.copy(contents = contents) }

    fun setExpires(expires: Boolean) = _state.update { it.copy(expires = expires) }

    fun setExpiresOn(date: LocalDate) = _state.update { if (date.isAfter(it.today)) it.copy(expiresOn = date) else it }

    fun dismissResult() = _state.update { it.copy(result = null) }

    /** Called after the consent the page shows; [canCreate] guards the button. */
    fun create() {
        val current = _state.value
        if (!current.canCreate) return
        _state.value = current.copy(isCreating = true)
        viewModelScope.launch {
            val result = try {
                repository.create(
                    NewShareLink(
                        trainerName = current.trainerName.trim(),
                        period = current.period,
                        contents = current.contents,
                        expiresOn = current.expiresOn.takeIf { current.expires },
                    ),
                )
                CreateResult.Created
            } catch (e: CancellationException) {
                throw e
            } catch (e: SharingNotSetUpException) {
                CreateResult.NotSetUp
            } catch (e: Exception) {
                CreateResult.Failed
            }
            _state.update { it.copy(isCreating = false, result = result) }
        }
    }
}
