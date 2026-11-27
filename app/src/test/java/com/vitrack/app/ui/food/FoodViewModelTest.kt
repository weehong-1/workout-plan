package com.vitrack.app.ui.food

import com.vitrack.app.data.food.FoodLabel
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeFoodRepository
import com.vitrack.app.testing.FakeProfileRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.foodPhoto
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

/** FIXED_CLOCK is 09:00 UTC on TODAY (26 Nov 2026). */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val sample = listOf(
        foodPhoto(1, TODAY.atTime(7, 45), FoodLabel.Breakfast),
        foodPhoto(2, TODAY.atTime(8, 30)),
        foodPhoto(3, TODAY.minusDays(1).atTime(12, 35), FoodLabel.Lunch),
        foodPhoto(4, TODAY.minusDays(1).atTime(19, 5), FoodLabel.Dinner),
    )

    private fun TestScope.viewModel(repo: FakeFoodRepository = FakeFoodRepository(sample)): FoodViewModel {
        val viewModel = FoodViewModel(repo, FakeProfileRepository(), FIXED_CLOCK)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `opens on today with the strip ending today and dots for days with photos`() = runTest {
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(TODAY, state.selected)
        assertEquals(STRIP_DAYS, state.days.size)
        assertEquals(TODAY, state.days.last().date)
        assertEquals(2, state.days.last().count)
        assertEquals(2, state.days[state.days.size - 2].count)
        assertEquals(0, state.days[state.days.size - 3].count)
        assertEquals(listOf(1L, 2L), state.shown.map { it.id })
    }

    @Test
    fun `selecting a day and a label filters the grid`() = runTest {
        val viewModel = viewModel()

        viewModel.selectDay(TODAY.minusDays(1))
        viewModel.selectFilter(FoodLabel.Dinner)

        val state = viewModel.uiState.value
        assertEquals(2, state.dayPhotos.size)
        assertEquals(listOf(4L), state.shown.map { it.id })
    }

    @Test
    fun `future days can't be selected`() = runTest {
        val viewModel = viewModel()

        viewModel.selectDay(TODAY.plusDays(1))

        assertEquals(TODAY, viewModel.uiState.value.selected)
    }

    @Test
    fun `a camera photo is taken now and suggests a label from the time`() = runTest {
        val viewModel = viewModel()

        viewModel.onCaptured("content://cam/1")

        val draft = viewModel.draft.value!!
        assertEquals(TODAY.atTime(9, 0), draft.photos.single().takenAt)
        // 9:00 is breakfast time.
        assertEquals(FoodLabel.Breakfast, draft.label)
    }

    @Test
    fun `gallery photos keep the day they were taken and saving shows that day`() = runTest {
        val repo = FakeFoodRepository(sample)
        repo.takenAtByUri["content://pick/a"] = TODAY.minusDays(3).atTime(13, 0)
        val viewModel = viewModel(repo)
        viewModel.selectFilter(FoodLabel.Snack)

        viewModel.onPicked(listOf("content://pick/a", "content://pick/b"))
        val draft = viewModel.draft.value!!
        assertEquals(TODAY.minusDays(3).atTime(13, 0), draft.photos[0].takenAt)
        // Unknown taken time: now.
        assertEquals(TODAY.atTime(9, 0), draft.photos[1].takenAt)
        assertEquals(FoodLabel.Lunch, draft.label)

        viewModel.setDraftLabel(null)
        viewModel.setDraftNote("Ramen")
        viewModel.saveDraft()

        assertNull(viewModel.draft.value)
        assertEquals(2, repo.added.size)
        assertTrue(repo.added.all { it.label == null && it.note == "Ramen" })
        val state = viewModel.uiState.value
        assertEquals(TODAY.minusDays(3), state.selected)
        assertNull(state.filter)
    }

    @Test
    fun `a failed save keeps the sheet open`() = runTest {
        val repo = FakeFoodRepository().apply { failAdd = true }
        val viewModel = viewModel(repo)
        viewModel.onCaptured("content://cam/1")

        viewModel.saveDraft()

        assertTrue(viewModel.draft.value!!.saveFailed)
        assertFalse(viewModel.draft.value!!.isSaving)
    }

    @Test
    fun `detail changes the label at once and saves the note on close`() = runTest {
        val repo = FakeFoodRepository(sample)
        val viewModel = viewModel(repo)

        viewModel.openPhoto(2)
        viewModel.setDetailLabel(FoodLabel.Snack)
        assertEquals(FoodLabel.Snack, repo.photos.value.first { it.id == 2L }.label)

        viewModel.setDetailNote("Coffee and toast")
        viewModel.closeDetail()

        assertNull(viewModel.detail.value)
        assertEquals("Coffee and toast", repo.photos.value.first { it.id == 2L }.note)
    }

    @Test
    fun `deleting from detail removes the photo`() = runTest {
        val repo = FakeFoodRepository(sample)
        val viewModel = viewModel(repo)

        viewModel.openPhoto(1)
        viewModel.deletePhoto()

        assertNull(viewModel.detail.value)
        assertEquals(listOf(2L), viewModel.uiState.value.dayPhotos.map { it.id })
    }
}
