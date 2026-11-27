package com.vitrack.app.ui.weight.editor

import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.MAX_PHOTOS
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakePhotoStore
import com.vitrack.app.testing.FakeSettingsRepository
import com.vitrack.app.testing.FakeWeightRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.weighIn
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeighInEditorViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Before
    fun locale() = Locale.setDefault(Locale.US)

    private fun editor(
        repo: FakeWeightRepository = FakeWeightRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = WeighInEditorViewModel(repo, settings, FakePhotoStore(), FIXED_CLOCK)

    @Test
    fun `new entry is for today, prefilled from the last weigh-in`() = runTest {
        val viewModel = editor(FakeWeightRepository(listOf(weighIn(TODAY.minusDays(2), 78.7))))

        viewModel.openNew()

        val state = viewModel.state.value!!
        assertFalse(state.isEditing)
        assertEquals(TODAY, state.date)
        assertEquals("78.7", state.weightText)
        assertEquals(TODAY.minusDays(2), state.previous?.date)
    }

    @Test
    fun `log weight when today is logged starts a new entry that would update today`() = runTest {
        val viewModel = editor(FakeWeightRepository(listOf(weighIn(TODAY, 78.4, photoIds = listOf(1, 2)))))

        viewModel.openNew()

        val state = viewModel.state.value!!
        assertNull(state.originalDate)
        assertEquals(TODAY, state.replaces?.date)
        assertEquals("78.4", state.weightText)
        assertTrue(state.photos.isEmpty())
    }

    @Test
    fun `backdating from log weight leaves today's entry alone`() = runTest {
        val repo = FakeWeightRepository(listOf(weighIn(TODAY, 78.4)))
        val viewModel = editor(repo)
        viewModel.openNew()

        viewModel.onDateChange(TODAY.minusDays(3))
        viewModel.onWeightChange("79.1")
        viewModel.save()

        val draft = repo.saved.single()
        assertNull(draft.originalDate)
        assertEquals(TODAY.minusDays(3), draft.date)
        assertEquals(listOf(TODAY, TODAY.minusDays(3)), repo.weighIns.value.map { it.date })
    }

    @Test
    fun `save stores kg and closes the sheet`() = runTest {
        val repo = FakeWeightRepository()
        val viewModel = editor(repo)
        viewModel.openNew()

        viewModel.onWeightChange("78,4")
        viewModel.save()

        assertEquals(78.4, repo.saved.single().weightKg, 0.0001)
        assertNull(repo.saved.single().originalDate)
        assertNull(viewModel.state.value)
    }

    @Test
    fun `weights outside 20 to 400 kg can't be saved`() = runTest {
        val viewModel = editor()
        viewModel.openNew()

        viewModel.onWeightChange("12")
        assertFalse(viewModel.state.value!!.canSave)
        viewModel.onWeightChange("")
        assertFalse(viewModel.state.value!!.canSave)
        viewModel.onWeightChange("75.5")
        assertTrue(viewModel.state.value!!.canSave)
    }

    @Test
    fun `input keeps at most one decimal`() = runTest {
        val viewModel = editor()
        viewModel.openNew()

        viewModel.onWeightChange("78.4")
        viewModel.onWeightChange("78.45")
        viewModel.onWeightChange("78a")

        assertEquals("78.4", viewModel.state.value!!.weightText)
    }

    @Test
    fun `minus and plus step by a tenth of the shown unit`() = runTest {
        val viewModel = editor(FakeWeightRepository(listOf(weighIn(TODAY.minusDays(1), 78.4))))
        viewModel.openNew()

        viewModel.nudge(1)
        assertEquals("78.5", viewModel.state.value!!.weightText)
        viewModel.nudge(-1)
        viewModel.nudge(-1)
        assertEquals("78.3", viewModel.state.value!!.weightText)
    }

    @Test
    fun `stepping from an empty field starts at the last weigh-in`() = runTest {
        val viewModel = editor(FakeWeightRepository(listOf(weighIn(TODAY.minusDays(1), 80.0))))
        viewModel.openNew()
        viewModel.onWeightChange("")

        viewModel.nudge(1)

        assertEquals("80.1", viewModel.state.value!!.weightText)
    }

    @Test
    fun `switching unit converts the typed value and is remembered`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = editor(settings = settings)
        viewModel.openNew()
        viewModel.onWeightChange("80")

        viewModel.onUnitChange(WeightUnit.Lb)

        assertEquals("176.4", viewModel.state.value!!.weightText)
        assertEquals(80.0, viewModel.state.value!!.weightKg!!, 0.05)
        assertEquals(WeightUnit.Lb, settings.weightUnit.value)
    }

    @Test
    fun `picking a day that has an entry says it will be updated`() = runTest {
        val viewModel = editor(FakeWeightRepository(listOf(weighIn(TODAY.minusDays(2), 78.7))))
        viewModel.openNew()

        viewModel.onDateChange(TODAY.minusDays(2))

        assertEquals(TODAY.minusDays(2), viewModel.state.value!!.replaces?.date)
    }

    @Test
    fun `editing keeps, removes and adds photos`() = runTest {
        val repo = FakeWeightRepository(listOf(weighIn(TODAY.minusDays(1), 79.0, photoIds = listOf(1, 2))))
        val viewModel = editor(repo)
        viewModel.openExisting(TODAY.minusDays(1))

        viewModel.removePhoto("saved-1")
        viewModel.addPhotos(listOf("content://picker/a", "content://picker/a"))
        viewModel.onDateChange(TODAY)
        viewModel.save()

        val draft = repo.saved.single()
        assertEquals(TODAY.minusDays(1), draft.originalDate)
        assertEquals(TODAY, draft.date)
        assertEquals(listOf(2L), draft.keepPhotoIds)
        assertEquals(listOf(1L), draft.removePhotoIds)
        assertEquals(listOf("content://picker/a"), draft.newPhotoUris)
    }

    @Test
    fun `photos are capped`() = runTest {
        val viewModel = editor()
        viewModel.openNew()

        viewModel.addPhotos((1..10).map { "content://picker/$it" })

        assertEquals(MAX_PHOTOS, viewModel.state.value!!.photos.size)
        assertEquals(0, viewModel.state.value!!.remainingPhotoSlots)
    }

    @Test
    fun `a failed save keeps the sheet open with an error`() = runTest {
        val repo = FakeWeightRepository().apply { failSave = true }
        val viewModel = editor(repo)
        viewModel.openNew()
        viewModel.onWeightChange("78")

        viewModel.save()

        val state = viewModel.state.value!!
        assertTrue(state.saveFailed)
        assertFalse(state.isSaving)
    }

    @Test
    fun `delete removes the entry and closes`() = runTest {
        val repo = FakeWeightRepository(listOf(weighIn(TODAY, 78.4)))
        val viewModel = editor(repo)
        viewModel.openExisting(TODAY)

        viewModel.delete()

        assertEquals(listOf(TODAY), repo.deleted)
        assertNull(viewModel.state.value)
    }
}
