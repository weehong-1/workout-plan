package com.vitrack.app.ui.weight

import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.groupByWeek
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeProfileRepository
import com.vitrack.app.testing.FakeSettingsRepository
import com.vitrack.app.testing.FakeWeightRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.weighIn
import com.vitrack.app.ui.common.ChartPeriod
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

    /** Two weigh-ins this week, two last week, one three weeks back. */
    private val sample = listOf(
        weighIn(TODAY, 78.4),
        weighIn(thisMonday, 78.8),
        weighIn(thisMonday.minusDays(2), 79.2),
        weighIn(thisMonday.minusDays(6), 79.4),
        weighIn(thisMonday.minusWeeks(3), 81.0),
    )

    @Test
    fun `no weigh-ins shows the empty state`() = runTest {
        assertTrue(viewModel(FakeWeightRepository()).uiState.value is WeightUiState.Empty)
    }

    @Test
    fun `the headline is this week's average, compared with last week's`() = runTest {
        val state = viewModel(FakeWeightRepository(sample)).ready()

        assertEquals(thisMonday, state.thisWeekStart)
        assertEquals(thisMonday, state.currentWeek.weekStart)
        assertEquals(78.6, state.currentWeek.averageKg, 0.0001)
        assertEquals(2, state.currentWeek.count)
        // 78.6 this week vs 79.3 last week.
        assertEquals(-0.7, state.weeklyChangeKg!!, 0.0001)
        assertEquals(TODAY, state.latest.date)
    }

    @Test
    fun `history is grouped by week, newest first, with each week's change`() = runTest {
        val groups = viewModel(FakeWeightRepository(sample)).ready().weeks

        assertEquals(listOf(thisMonday, thisMonday.minusWeeks(1), thisMonday.minusWeeks(3)), groups.map { it.week.weekStart })
        assertEquals(listOf(2, 2, 1), groups.map { it.rows.size })
        assertEquals(-0.7, groups[0].changeKg!!, 0.0001)
        // Nothing logged two weeks back, so the gap week has no change.
        assertNull(groups[1].changeKg)
        assertNull(groups[2].changeKg)
        // Row changes still run against the previous weigh-in, across weeks.
        assertEquals(78.8 - 79.2, groups[0].rows.last().changeKg!!, 0.0001)
    }

    @Test
    fun `month and year chart weekly averages, week charts each weigh-in`() = runTest {
        val viewModel = viewModel(FakeWeightRepository(sample))

        val month = viewModel.ready().chart
        assertEquals(listOf(81.0, 79.3, 78.6), month.map { it.kg }.map { Math.round(it * 10) / 10.0 })
        // This week's point sits mid-week (Thursday), never after today.
        assertEquals(TODAY, month.last().date)

        viewModel.selectPeriod(ChartPeriod.Week)
        assertEquals(listOf(79.2, 78.8, 78.4), viewModel.ready().chart.map { it.kg })
    }

    @Test
    fun `unit setting is passed through`() = runTest {
        assertEquals(WeightUnit.Lb, viewModel(FakeWeightRepository(listOf(weighIn(TODAY, 80.0))), WeightUnit.Lb).ready().unit)
    }

    @Test
    fun `chart points stay inside the window`() {
        val start = TODAY.minusDays(29)
        val weighIns = listOf(weighIn(start.plusDays(1), 80.0), weighIn(TODAY, 78.0))
        val points = chartPoints(ChartPeriod.Month, start, TODAY, weighIns, groupByWeek(weighIns, weeks))

        assertTrue(points.all { !it.date.isBefore(start) && !it.date.isAfter(TODAY) })
    }

    @Test
    fun `each history row carries its change from the previous weigh-in`() {
        val rows = historyRows(
            listOf(weighIn(TODAY, 78.4), weighIn(TODAY.minusDays(2), 78.7), weighIn(TODAY.minusDays(5), 79.0)),
        )

        assertEquals(-0.3, rows[0].changeKg!!, 0.0001)
        assertEquals(-0.3, rows[1].changeKg!!, 0.0001)
        assertNull(rows[2].changeKg)
    }

    @Test
    fun `axis covers the values on whole numbers with an even range`() {
        assertEquals(WeightAxis(78.0, 80.0), weightAxis(listOf(78.4, 79.6)))
        assertEquals(WeightAxis(77.0, 79.0), weightAxis(listOf(78.2, 78.4)))
        assertEquals(WeightAxis(76.0, 82.0), weightAxis(listOf(76.5, 80.2)))
    }
}
