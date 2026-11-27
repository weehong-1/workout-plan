package com.vitrack.app.ui.weight

import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.WeightZoom
import com.vitrack.app.data.weight.groupWeighIns
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeProfileRepository
import com.vitrack.app.testing.FakeSettingsRepository
import com.vitrack.app.testing.FakeWeightRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.weighIn
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** TODAY is Thursday 26 Nov 2026; with ISO weeks its week is Mon 23 – Sun 29 Nov. */
@OptIn(ExperimentalCoroutinesApi::class)
class WeightViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val weeks = WeekFields.ISO
    private val thisMonday = LocalDate.of(2026, 11, 23)

    private fun TestScope.viewModel(repo: FakeWeightRepository, unit: WeightUnit = WeightUnit.Kg): WeightViewModel {
        val viewModel = WeightViewModel(repo, FakeSettingsRepository(unit), FakeProfileRepository(), FIXED_CLOCK, weeks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun WeightViewModel.ready() = uiState.value as WeightUiState.Ready

    /** Two weigh-ins this week, two last week, one four weeks back (26 Oct), one last year. */
    private val sample = listOf(
        weighIn(TODAY, 78.4),
        weighIn(thisMonday, 78.8),
        weighIn(thisMonday.minusDays(2), 79.2),
        weighIn(thisMonday.minusDays(6), 79.4),
        weighIn(thisMonday.minusWeeks(4), 81.0),
        weighIn(LocalDate.of(2025, 6, 10), 85.0),
    )

    @Test
    fun `no weigh-ins shows the empty state`() = runTest {
        assertTrue(viewModel(FakeWeightRepository()).uiState.value is WeightUiState.Empty)
    }

    @Test
    fun `weeks headline is this week's average against last week's`() = runTest {
        val state = viewModel(FakeWeightRepository(sample)).ready()

        assertEquals(WeightZoom.Weeks, state.zoom)
        assertEquals(thisMonday, state.todayPeriodStart)
        assertEquals(thisMonday, state.current.start)
        assertEquals(78.6, state.current.averageKg, 0.0001)
        assertEquals(thisMonday.minusWeeks(1), state.previous?.start)
        assertEquals(-0.7, state.changeKg!!, 0.0001)
    }

    @Test
    fun `weeks chart holds the last 12 weeks, oldest first, with a streak`() = runTest {
        val state = viewModel(FakeWeightRepository(sample)).ready()

        assertEquals(listOf(thisMonday.minusWeeks(4), thisMonday.minusWeeks(1), thisMonday), state.chart.map { it.start })
        // 81.0 → 79.3 → 78.6: two weeks down in a row.
        assertEquals(Trend.Streak(weeks = 2, down = true), state.trend)
    }

    @Test
    fun `history rows carry the change from the row below and open to their weigh-ins`() = runTest {
        val rows = viewModel(FakeWeightRepository(sample)).ready().history

        assertEquals(4, rows.size)
        assertEquals(-0.7, rows[0].changeKg!!, 0.0001)
        // Rows compare with the next listed period, even across a gap.
        assertEquals(79.3 - 81.0, rows[1].changeKg!!, 0.0001)
        assertNull(rows.last().changeKg)
        val details = rows[0].details as PeriodDetails.Entries
        assertEquals(listOf(TODAY, thisMonday), details.entries.map { it.date })
    }

    @Test
    fun `months zoom averages by month and opens to weeks`() = runTest {
        val viewModel = viewModel(FakeWeightRepository(sample))

        viewModel.selectZoom(WeightZoom.Months)

        val state = viewModel.ready()
        assertEquals(LocalDate.of(2026, 11, 1), state.current.start)
        assertEquals(LocalDate.of(2026, 10, 1), state.previous?.start)
        // The chart covers Dec 2025 to Nov 2026, so June 2025 is left off.
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), state.chart.map { it.start })
        val weeksInNovember = (state.history[0].details as PeriodDetails.Weeks).weeks
        assertEquals(listOf(thisMonday, thisMonday.minusWeeks(1)), weeksInNovember.map { it.start })
    }

    @Test
    fun `years zoom compares with last year and opens to lowest and highest month`() = runTest {
        val viewModel = viewModel(FakeWeightRepository(sample))

        viewModel.selectZoom(WeightZoom.Years)

        val state = viewModel.ready()
        assertEquals(listOf(2025, 2026), state.chart.map { it.start.year })
        assertTrue(state.changeKg!! < 0)
        val trend = state.trend as Trend.FromYear
        assertEquals(2025, trend.year)
        val extremes = state.history[0].details as PeriodDetails.Extremes
        assertEquals(11, extremes.lowestMonth.start.monthValue)
        assertEquals(10, extremes.highestMonth.start.monthValue)
    }

    @Test
    fun `month trend spans the chart`() {
        val periods = groupWeighIns(sample, WeightZoom.Months, weeks)
        val chart = chartWindow(WeightZoom.Months, LocalDate.of(2026, 11, 1), periods)

        val trend = trendOf(WeightZoom.Months, chart) as Trend.OverMonths
        assertEquals(2, trend.months)
        assertTrue(trend.changeKg < 0)
    }

    @Test
    fun `a single period has no trend and no change`() = runTest {
        val state = viewModel(FakeWeightRepository(listOf(weighIn(TODAY, 80.0)))).ready()

        assertNull(state.trend)
        assertNull(state.changeKg)
    }

    @Test
    fun `unit setting is passed through`() = runTest {
        assertEquals(WeightUnit.Lb, viewModel(FakeWeightRepository(listOf(weighIn(TODAY, 80.0))), WeightUnit.Lb).ready().unit)
    }
}
