package com.vitrack.app.ui.review.share

import androidx.lifecycle.SavedStateHandle
import com.vitrack.app.data.share.NewShareLink
import com.vitrack.app.data.share.NotConnectedShareRepository
import com.vitrack.app.data.share.ShareContents
import com.vitrack.app.data.share.ShareLink
import com.vitrack.app.data.share.SharePeriod
import com.vitrack.app.data.share.ShareRepository
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.ui.navigation.ShareRoute
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private class RecordingShareRepository : ShareRepository {
        val created = mutableListOf<NewShareLink>()
        override val isAvailable = true
        override fun observeLinks(): Flow<List<ShareLink>> = MutableStateFlow(emptyList())
        override suspend fun create(link: NewShareLink): ShareLink {
            created += link
            return ShareLink("1", link.trainerName, link.period, link.expiresOn, "https://example.test/share/abc")
        }
        override suspend fun revoke(id: String) = Unit
    }

    private fun handle(year: Int, month: Int) = SavedStateHandle(mapOf("year" to year, "month" to month))

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
        val viewModel = TrainerLinkViewModel(handle(2026, 11), RecordingShareRepository(), FIXED_CLOCK)

        assertFalse(viewModel.state.value.canCreate)
        viewModel.setTrainerName("Alex")
        assertTrue(viewModel.state.value.canCreate)
        viewModel.setContents(ShareContents(activity = false, weight = false, weightPhotos = false, foodPhotos = false))
        assertFalse(viewModel.state.value.canCreate)
    }

    @Test
    fun `create sends the choices, with no expiry when switched off`() = runTest {
        val repo = RecordingShareRepository()
        val viewModel = TrainerLinkViewModel(handle(2026, 11), repo, FIXED_CLOCK)
        viewModel.setTrainerName("  Alex  ")
        viewModel.setContents(ShareContents(weightPhotos = true))
        viewModel.setExpires(false)

        viewModel.create()

        val link = repo.created.single()
        assertEquals("Alex", link.trainerName)
        assertEquals(SharePeriod.Month(YearMonth.of(2026, 11)), link.period)
        assertTrue(link.contents.weightPhotos)
        assertNull(link.expiresOn)
        assertEquals(CreateResult.Created, viewModel.state.value.result)
    }

    @Test
    fun `without a server it says so and makes no link`() = runTest {
        val viewModel = TrainerLinkViewModel(handle(2026, 11), NotConnectedShareRepository(), FIXED_CLOCK)
        viewModel.setTrainerName("Alex")

        viewModel.create()

        assertEquals(CreateResult.NotSetUp, viewModel.state.value.result)
        assertFalse(viewModel.state.value.isCreating)
    }

    @Test
    fun `past or today expiry dates are ignored`() = runTest {
        val viewModel = TrainerLinkViewModel(handle(2026, 11), RecordingShareRepository(), FIXED_CLOCK)
        val before = viewModel.state.value.expiresOn

        viewModel.setExpiresOn(TODAY)

        assertEquals(before, viewModel.state.value.expiresOn)
    }
}
