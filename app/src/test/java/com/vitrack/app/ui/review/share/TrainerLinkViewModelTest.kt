package com.vitrack.app.ui.review.share

import androidx.lifecycle.SavedStateHandle
import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.data.share.ShareContents
import com.vitrack.app.data.share.SharePeriod
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeShareRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.ui.navigation.ShareRoute
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainerLinkViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun handle(year: Int, month: Int) = SavedStateHandle(mapOf("year" to year, "month" to month))

    private fun viewModel(repo: FakeShareRepository = FakeShareRepository()) = TrainerLinkViewModel(handle(2026, 11), repo, FIXED_CLOCK)

    @Test
    fun `route maps to a month or a year`() {
        assertEquals(SharePeriod.Month(YearMonth.of(2026, 11)), ShareRoute(2026, 11).toPeriod())
        assertEquals(SharePeriod.Year(2025), ShareRoute(2025, 0).toPeriod())
    }

    @Test
    fun `expiry defaults to the end of the month after the period, never the past`() {
        assertEquals(LocalDate.of(2026, 12, 31), defaultExpiry(SharePeriod.Month(YearMonth.of(2026, 11)), TODAY))
        // A month long gone: a month from now instead.
        assertEquals(LocalDate.of(2026, 12, 31), defaultExpiry(SharePeriod.Month(YearMonth.of(2025, 3)), TODAY))
        assertEquals(LocalDate.of(2027, 1, 31), defaultExpiry(SharePeriod.Year(2026), TODAY))
    }

    @Test
    fun `a name and at least one item are needed`() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.canCreate)
        viewModel.setTrainerName("Alex")
        assertTrue(viewModel.state.value.canCreate)
        viewModel.setContents(ShareContents(activity = false, weight = false, weightPhotos = false, foodPhotos = false))
        assertFalse(viewModel.state.value.canCreate)
    }

    @Test
    fun `signed out with a server asks to sign in first`() = runTest {
        val repo = FakeShareRepository(signedIn = false)
        val viewModel = viewModel(repo)

        assertTrue(viewModel.state.value.needsSignIn)
        repo.signedInFlow.value = true
        assertFalse(viewModel.state.value.needsSignIn)
    }

    @Test
    fun `without a server it never asks to sign in`() = runTest {
        val viewModel = viewModel(FakeShareRepository(isAvailable = false, signedIn = false))

        assertFalse(viewModel.state.value.needsSignIn)
    }

    @Test
    fun `create sends the choices and shows the new link`() = runTest {
        val repo = FakeShareRepository()
        val viewModel = viewModel(repo)
        viewModel.setTrainerName("  Alex  ")
        viewModel.setContents(ShareContents(weightPhotos = true))
        viewModel.setExpires(false)

        viewModel.create()

        val link = repo.created.single()
        assertEquals("Alex", link.trainerName)
        assertEquals(SharePeriod.Month(YearMonth.of(2026, 11)), link.period)
        assertTrue(link.contents.weightPhotos)
        assertNull(link.expiresOn)
        assertEquals(CreateResult.Created("https://web.test/share/token"), viewModel.state.value.result)
        assertFalse(viewModel.state.value.isCreating)
        assertNull(viewModel.state.value.progress)
    }

    @Test
    fun `expiry is sent when switched on`() = runTest {
        val repo = FakeShareRepository()
        val viewModel = viewModel(repo)
        viewModel.setTrainerName("Alex")

        viewModel.create()

        assertEquals(LocalDate.of(2026, 12, 31), repo.created.single().expiresOn)
    }

    @Test
    fun `without a server it says so and makes no link`() = runTest {
        val repo = FakeShareRepository(isAvailable = false, signedIn = false)
        val viewModel = viewModel(repo)
        viewModel.setTrainerName("Alex")

        viewModel.create()

        assertEquals(CreateResult.NotSetUp, viewModel.state.value.result)
        assertTrue(repo.created.isEmpty())
    }

    @Test
    fun `an ended session asks to sign in again`() = runTest {
        val repo = FakeShareRepository().apply { createError = BackendException.NotSignedIn() }
        val viewModel = viewModel(repo)
        viewModel.setTrainerName("Alex")

        viewModel.create()

        assertEquals(CreateResult.NeedsSignIn, viewModel.state.value.result)
    }

    @Test
    fun `a server or network error fails without a link`() = runTest {
        val repo = FakeShareRepository().apply { createError = BackendException.Network(RuntimeException()) }
        val viewModel = viewModel(repo)
        viewModel.setTrainerName("Alex")

        viewModel.create()

        assertEquals(CreateResult.Failed, viewModel.state.value.result)
        viewModel.dismissResult()
        assertNull(viewModel.state.value.result)
    }

    @Test
    fun `past or today expiry dates are ignored`() = runTest {
        val viewModel = viewModel()
        val before = viewModel.state.value.expiresOn

        viewModel.setExpiresOn(TODAY)

        assertEquals(before, viewModel.state.value.expiresOn)
    }
}
