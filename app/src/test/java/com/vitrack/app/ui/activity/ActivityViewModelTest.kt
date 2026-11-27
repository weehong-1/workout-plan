package com.vitrack.app.ui.activity

import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeActivityRepository
import com.vitrack.app.testing.FakeProfileRepository
import com.vitrack.app.testing.FakeSettingsRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.day
import com.vitrack.app.testing.snapshot
import com.vitrack.app.testing.workout
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

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val drawnSteps = listOf(9_200, 11_400, 5_200, 7_800, 10_300, 5_700, 7_235)
    private val drawnWeek = drawnSteps.mapIndexed { i, s -> day(TODAY.minusDays((6 - i).toLong()), steps = s) }

    private fun TestScope.viewModel(
        activity: FakeActivityRepository = FakeActivityRepository(snapshot(week = drawnWeek)),
        profile: FakeProfileRepository = FakeProfileRepository(),
    ): ActivityViewModel {
        val viewModel = ActivityViewModel(activity, profile, FakeSettingsRepository(), FIXED_CLOCK)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun ActivityViewModel.ready() = uiState.value as ActivityUiState.Ready

    @Test
    fun `maps the snapshot and name into a ready state`() = runTest {
        val state = viewModel().ready()

        assertEquals(TODAY, state.date)
        assertEquals("Vernon", state.displayName)
        assertEquals(7_235, state.steps)
        assertEquals(0.7235f, state.stepProgress, 0.0001f)
    }

    @Test
    fun `steps start on the last 7 days with the last day highlighted`() = runTest {
        val state = viewModel().ready()

        assertEquals(StepsRange.Last7Days, state.stepsRange)
        assertEquals(drawnSteps, state.stepsDays.map { it.steps })
        assertEquals(listOf(false, false, false, false, false, false, true), state.stepsDays.map { it.highlighted })
        assertEquals(8_119, state.averageSteps)
    }

    @Test
    fun `last 30 days shows 30 bars ending today`() = runTest {
        val viewModel = viewModel()

        viewModel.selectStepsRange(StepsRange.Last30Days)

        val days = viewModel.ready().stepsDays
        assertEquals(30, days.size)
        assertEquals(TODAY.minusDays(29), days.first().date)
        assertEquals(TODAY, days.last().date)
    }

    @Test
    fun `a custom range shows only its days, highlighting its last`() = runTest {
        val viewModel = viewModel()

        viewModel.selectStepsRange(StepsRange.Custom(TODAY.minusDays(12), TODAY.minusDays(10)))

        val state = viewModel.ready()
        assertEquals(listOf(TODAY.minusDays(12), TODAY.minusDays(11), TODAY.minusDays(10)), state.stepsDays.map { it.date })
        assertEquals(listOf(12_000, 11_000, 10_000), state.stepsDays.map { it.steps })
        assertEquals(11_000, state.averageSteps)
        assertTrue(state.stepsDays.last().highlighted)
    }

    @Test
    fun `workouts are listed earliest first with their total time`() = runTest {
        val late = workout("Walking", 3.1).copy(start = java.time.LocalTime.of(12, 40), durationMinutes = 22)
        val early = workout("Outdoor cycling", 9.4).copy(start = java.time.LocalTime.of(7, 10), durationMinutes = 35)
        val state = viewModel(FakeActivityRepository(snapshot(workouts = listOf(late, early)))).ready()

        assertEquals(listOf("Outdoor cycling", "Walking"), state.workouts.map { it.name })
        assertEquals(57, state.workoutMinutes)
    }

    @Test
    fun `no workouts and no name are allowed`() = runTest {
        val state = viewModel(profile = FakeProfileRepository(name = null)).ready()

        assertTrue(state.workouts.isEmpty())
        assertNull(state.displayName)
    }

    @Test
    fun `step progress is capped at the goal`() = runTest {
        val week = (6 downTo 0).map { day(TODAY.minusDays(it.toLong()), steps = 14_000) }

        assertEquals(1f, viewModel(FakeActivityRepository(snapshot(week = week))).ready().stepProgress)
    }

    @Test
    fun `new data from the repository updates the state`() = runTest {
        val activity = FakeActivityRepository()
        val viewModel = viewModel(activity)

        val updatedWeek = (6 downTo 0).map { day(TODAY.minusDays(it.toLong()), steps = if (it == 0) 12_345 else 8_000) }
        activity.snapshots.value = snapshot(week = updatedWeek)

        assertEquals(12_345, viewModel.ready().steps)
    }

    @Test
    fun `a picked range that matches a preset becomes that preset`() {
        assertEquals(StepsRange.Last7Days, rangeFor(TODAY.minusDays(6), TODAY, TODAY))
        assertEquals(StepsRange.Last30Days, rangeFor(TODAY.minusDays(29), TODAY, TODAY))
        assertEquals(
            StepsRange.Custom(TODAY.minusDays(6), TODAY.minusDays(1)),
            rangeFor(TODAY.minusDays(6), TODAY.minusDays(1), TODAY),
        )
    }
}
