package com.vitrack.app.ui.activity

import com.vitrack.app.testing.FakeActivityRepository
import com.vitrack.app.testing.FakeProfileRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.day
import com.vitrack.app.testing.snapshot
import com.vitrack.app.testing.workout
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
class ActivityViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `maps the snapshot and name into a ready state`() = runTest {
        val week = listOf(9_200, 11_400, 5_200, 7_800, 10_300, 5_700, 7_235)
            .mapIndexed { i, s -> day(TODAY.minusDays((6 - i).toLong()), steps = s) }
        val activity = FakeActivityRepository(snapshot(week = week))
        val viewModel = ActivityViewModel(activity, FakeProfileRepository("Vernon"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value as ActivityUiState.Ready
        assertEquals(TODAY, state.date)
        assertEquals("Vernon", state.displayName)
        assertEquals(7_235, state.steps)
        assertEquals(8_119, state.weeklyAverageSteps)
        assertEquals(listOf(false, false, false, false, false, false, true), state.week.map { it.isToday })
        assertEquals(0.7235f, state.stepProgress, 0.0001f)
    }

    @Test
    fun `top workout is the one with the highest strain`() = runTest {
        val activity = FakeActivityRepository(
            snapshot(workouts = listOf(workout("Walking", 3.1), workout("Outdoor cycling", 9.4))),
        )
        val viewModel = ActivityViewModel(activity, FakeProfileRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals("Outdoor cycling", (viewModel.uiState.value as ActivityUiState.Ready).topWorkout?.name)
    }

    @Test
    fun `no workouts and no name are allowed`() = runTest {
        val viewModel = ActivityViewModel(FakeActivityRepository(), FakeProfileRepository(name = null))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value as ActivityUiState.Ready
        assertNull(state.topWorkout)
        assertNull(state.displayName)
    }

    @Test
    fun `step progress is capped at the goal`() = runTest {
        val week = (6 downTo 0).map { day(TODAY.minusDays(it.toLong()), steps = 14_000) }
        val viewModel = ActivityViewModel(FakeActivityRepository(snapshot(week = week)), FakeProfileRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(1f, (viewModel.uiState.value as ActivityUiState.Ready).stepProgress)
    }

    @Test
    fun `new data from the repository updates the state`() = runTest {
        val activity = FakeActivityRepository()
        val viewModel = ActivityViewModel(activity, FakeProfileRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val updatedWeek = (6 downTo 0).map { day(TODAY.minusDays(it.toLong()), steps = if (it == 0) 12_345 else 8_000) }
        activity.snapshots.value = snapshot(week = updatedWeek)

        val state = viewModel.uiState.value
        assertTrue(state is ActivityUiState.Ready)
        assertEquals(12_345, (state as ActivityUiState.Ready).steps)
    }
}
