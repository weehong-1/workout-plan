package com.vitrack.app.ui.home

import com.vitrack.app.data.home.HomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private class FakeHomeRepository : HomeRepository {
        val greetings = MutableSharedFlow<String>()
        override fun observeGreeting(): Flow<String> = greetings
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading, then shows the repository greeting`() = runTest {
        val repository = FakeHomeRepository()
        val viewModel = HomeViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertEquals(HomeUiState(isLoading = true, greeting = ""), viewModel.uiState.value)

        repository.greetings.emit("Hello")

        assertEquals(HomeUiState(isLoading = false, greeting = "Hello"), viewModel.uiState.value)
    }
}
