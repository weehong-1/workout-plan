package com.vitrack.app.ui.strain

import com.vitrack.app.testing.FakeActivityRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.day
import com.vitrack.app.testing.snapshot
import com.vitrack.app.ui.common.ChartPeriod
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StrainViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun ready(viewModel: StrainViewModel) = viewModel.uiState.value as StrainUiState.Ready

    @Test
    fun `without WHOOP it asks to connect`() = runTest {
        val viewModel = StrainViewModel(FakeActivityRepository(snapshot = null))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(StrainUiState.NotConnected, viewModel.uiState.value)
    }

    @Test
    fun `starts on the week with one bar per day, today highlighted`() = runTest {
        val viewModel = StrainViewModel(FakeActivityRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = ready(viewModel)
        assertEquals(ChartPeriod.Week, state.period)
        assertEquals(7, state.bars.size)
        assertEquals(TODAY, state.bars.last().start)
        assertEquals(listOf(true), state.bars.filter { it.isCurrent }.map { it.start == TODAY })
    }

    @Test
    fun `switching period reloads history for that range`() = runTest {
        val repository = FakeActivityRepository()
        val viewModel = StrainViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.selectPeriod(ChartPeriod.Month)
        assertEquals(ChartPeriod.Month, ready(viewModel).period)
        assertEquals(30, ready(viewModel).bars.size)

        viewModel.selectPeriod(ChartPeriod.Year)
        assertEquals(ChartPeriod.Year, ready(viewModel).period)
        assertEquals(listOf(7, 30, 365), repository.historyRequests)
    }

    @Test
    fun `year bars average strain per calendar month`() {
        val days = listOf(
            day(LocalDate.of(2026, 10, 30), strain = 10.0),
            day(LocalDate.of(2026, 10, 31), strain = 14.0),
            day(LocalDate.of(2026, 11, 1), strain = 6.0),
            day(LocalDate.of(2026, 11, 2), strain = 8.0),
        )

        val bars = bars(ChartPeriod.Year, days)

        assertEquals(2, bars.size)
        assertEquals(LocalDate.of(2026, 10, 1), bars[0].start)
        assertEquals(12.0, bars[0].strain, 0.0001)
        assertEquals(7.0, bars[1].strain, 0.0001)
        assertEquals(listOf(false, true), bars.map { it.isCurrent })
    }

    @Test
    fun `year shows exactly the last 12 months`() {
        val end = LocalDate.of(2026, 11, 27)
        val days = (364 downTo 0).map { day(end.minusDays(it.toLong())) }

        val bars = bars(ChartPeriod.Year, days)

        assertEquals(12, bars.size)
        assertEquals(LocalDate.of(2025, 12, 1), bars.first().start)
        assertEquals(LocalDate.of(2026, 11, 1), bars.last().start)
    }

    @Test
    fun `weekly average covers the last 7 days including today`() = runTest {
        val week = listOf(13.2, 15.8, 6.4, 10.9, 14.1, 8.7, 11.8)
            .mapIndexed { i, s -> day(TODAY.minusDays((6 - i).toLong()), strain = s) }
        val viewModel = StrainViewModel(FakeActivityRepository(snapshot(week = week)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = ready(viewModel)
        assertEquals(11.557, state.weeklyAverageStrain, 0.001)
        // 11.8 vs 11.6 as shown.
        assertEquals(Comparison.Above, state.comparedToAverage)
    }

    @Test
    fun `comparison is level when both round to the same tenth`() {
        assertEquals(Comparison.Level, compare(11.56, 11.6))
        assertEquals(Comparison.Below, compare(9.0, 11.6))
        assertEquals(Comparison.Above, compare(11.7, 11.6))
    }

    @Test
    fun `strain levels follow WHOOP bands`() {
        assertEquals(StrainLevel.Light, strainLevel(9.9))
        assertEquals(StrainLevel.Moderate, strainLevel(11.8))
        assertEquals(StrainLevel.Strenuous, strainLevel(14.0))
        assertEquals(StrainLevel.AllOut, strainLevel(18.0))
    }
}
