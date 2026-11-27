package com.vitrack.app.ui.weight

import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeProfileRepository
import com.vitrack.app.testing.FakeSettingsRepository
import com.vitrack.app.testing.FakeWeightRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.weighIn
import com.vitrack.app.ui.common.ChartPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeightViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun TestScopeViewModel(repo: FakeWeightRepository, settings: FakeSettingsRepository = FakeSettingsRepository()) =
        WeightViewModel(repo, settings, FakeProfileRepository(), FIXED_CLOCK)

    @Test
    fun `no weigh-ins shows the empty state`() = runTest {
        val viewModel = TestScopeViewModel(FakeWeightRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value is WeightUiState.Empty)
    }

    @Test
    fun `latest, weekly change and history come from the weigh-ins`() = runTest {
        val repo = FakeWeightRepository(
            listOf(
                weighIn(TODAY, 78.4),
                weighIn(TODAY.minusDays(2), 78.7),
                weighIn(TODAY.minusDays(7), 79.0),
                weighIn(TODAY.minusDays(40), 81.0),
            ),
        )
        val viewModel = TestScopeViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value as WeightUiState.Ready
        assertEquals(78.4, state.latest.weightKg, 0.0)
        assertEquals(-0.6, state.weeklyChangeKg!!, 0.0001)
        assertEquals(4, state.history.size)
        // Month (default) covers the last 30 days, oldest first.
        assertEquals(ChartPeriod.Month, state.period)
        assertEquals(listOf(79.0, 78.7, 78.4), state.chart.map { it.weightKg })
        assertEquals(TODAY.minusDays(29), state.chartStart)
    }

    @Test
    fun `period changes the chart window`() = runTest {
        val repo = FakeWeightRepository(
            listOf(weighIn(TODAY, 78.4), weighIn(TODAY.minusDays(7), 79.0), weighIn(TODAY.minusDays(40), 81.0)),
        )
        val viewModel = TestScopeViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.selectPeriod(ChartPeriod.Week)
        assertEquals(1, (viewModel.uiState.value as WeightUiState.Ready).chart.size)

        viewModel.selectPeriod(ChartPeriod.Year)
        assertEquals(3, (viewModel.uiState.value as WeightUiState.Ready).chart.size)
    }

    @Test
    fun `unit setting is passed through`() = runTest {
        val viewModel = TestScopeViewModel(FakeWeightRepository(listOf(weighIn(TODAY, 80.0))), FakeSettingsRepository(WeightUnit.Lb))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(WeightUnit.Lb, (viewModel.uiState.value as WeightUiState.Ready).unit)
    }

    @Test
    fun `weekly change needs a weigh-in at least a week before the latest`() {
        assertNull(weeklyChangeKg(listOf(weighIn(TODAY, 78.4), weighIn(TODAY.minusDays(6), 79.0))))
        assertEquals(
            1.0,
            weeklyChangeKg(listOf(weighIn(TODAY, 80.0), weighIn(TODAY.minusDays(9), 79.0), weighIn(TODAY.minusDays(20), 70.0)))!!,
            0.0001,
        )
    }

    @Test
    fun `axis covers the values on whole numbers with an even range`() {
        assertEquals(WeightAxis(78.0, 80.0), weightAxis(listOf(78.4, 79.6)))
        assertEquals(WeightAxis(77.0, 79.0), weightAxis(listOf(78.2, 78.4)))
        assertEquals(WeightAxis(76.0, 82.0), weightAxis(listOf(76.5, 80.2)))
    }
}
