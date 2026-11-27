package com.vitrack.app.ui.review

import com.vitrack.app.testing.FakeShareRepository
import com.vitrack.app.data.share.SharePeriod
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeActivityRepository
import com.vitrack.app.testing.FakeFoodRepository
import com.vitrack.app.testing.FakeProfileRepository
import com.vitrack.app.testing.FakeSettingsRepository
import com.vitrack.app.testing.FakeWeightRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.foodPhoto
import com.vitrack.app.testing.weighIn
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** TODAY is 26 Nov 2026. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val november = YearMonth.of(2026, 11)

    private fun TestScope.viewModel(
        weight: FakeWeightRepository = FakeWeightRepository(
            listOf(
                weighIn(LocalDate.of(2026, 11, 2), 80.2, photoIds = listOf(1)),
                weighIn(LocalDate.of(2026, 11, 12), 79.4),
                weighIn(TODAY, 78.4, photoIds = listOf(2)),
                weighIn(LocalDate.of(2026, 10, 28), 81.0, photoIds = listOf(3)),
            ),
        ),
        food: FakeFoodRepository = FakeFoodRepository(
            listOf(
                foodPhoto(1, TODAY.atTime(8, 0)),
                foodPhoto(2, TODAY.atTime(12, 0)),
                foodPhoto(3, LocalDate.of(2026, 11, 3).atTime(19, 0)),
                foodPhoto(4, LocalDate.of(2026, 10, 30).atTime(19, 0)),
            ),
        ),
    ): ReviewViewModel {
        val viewModel = ReviewViewModel(
            FakeActivityRepository(), weight, food, FakeSettingsRepository(), FakeProfileRepository(),
            FakeShareRepository(isAvailable = false, signedIn = false), FIXED_CLOCK,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `opens on this month and can't go past it`() = runTest {
        val state = viewModel().uiState.value

        assertEquals(SharePeriod.Month(november), state.period)
        assertFalse(state.canGoForward)
        assertFalse(state.sharingAvailable)
        assertTrue(state.links.isEmpty())
    }

    @Test
    fun `weight runs from the first to the last weigh-in of the month, with photos at both ends`() = runTest {
        val weight = viewModel().uiState.value.weight!!

        assertEquals(80.2, weight.first.weightKg, 0.0)
        assertEquals(78.4, weight.last.weightKg, 0.0)
        assertEquals(3, weight.count)
        assertEquals(-1.8, weight.changeKg, 0.0001)
        assertEquals(LocalDate.of(2026, 11, 2), weight.firstPhoto?.date)
        assertEquals(TODAY, weight.lastPhoto?.date)
    }

    @Test
    fun `food counts days with photos out of the days so far`() = runTest {
        val food = viewModel().uiState.value.food

        assertEquals(2, food.daysWithPhotos)
        assertEquals(26, food.daysSoFar)
        assertEquals(26, food.marks.size)
        assertEquals(1f, food.marks[2])
        assertEquals(0f, food.marks[0])
    }

    @Test
    fun `activity averages cover the month so far`() = runTest {
        val activity = viewModel().uiState.value.activity!!

        assertEquals(26, activity.days)
        assertTrue(activity.steps > 0)
    }

    @Test
    fun `stepping back to October changes every summary`() = runTest {
        val viewModel = viewModel()

        viewModel.previous()

        val state = viewModel.uiState.value
        assertEquals(SharePeriod.Month(YearMonth.of(2026, 10)), state.period)
        assertTrue(state.canGoForward)
        assertEquals(1, state.weight!!.count)
        assertNull(state.weight!!.lastPhoto)
        assertEquals(1, state.food.daysWithPhotos)
        assertEquals(31, state.food.daysSoFar)
    }

    @Test
    fun `year view keeps the year and shows a mark per month so far`() = runTest {
        val viewModel = viewModel()

        viewModel.showYear(true)

        val state = viewModel.uiState.value
        assertEquals(SharePeriod.Year(2026), state.period)
        assertEquals(11, state.food.marks.size)
        assertEquals(4, state.weight!!.count)

        viewModel.showYear(false)
        assertEquals(SharePeriod.Month(november), viewModel.uiState.value.period)
    }

    @Test
    fun `empty periods have no weight and zero food days`() {
        val period = SharePeriod.Month(YearMonth.of(2025, 2))
        assertNull(weightSummary(emptyList()))
        val food = foodSummary(period, TODAY, emptyMap())
        assertEquals(0, food.daysWithPhotos)
        assertEquals(28, food.daysSoFar)
    }
}
