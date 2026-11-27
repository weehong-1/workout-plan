package com.vitrack.app.ui.app

import com.vitrack.app.data.auth.Account
import com.vitrack.app.data.settings.ThemeMode
import com.vitrack.app.testing.FakeAuthRepository
import com.vitrack.app.testing.FakeSettingsRepository
import com.vitrack.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `with a server, sign-in comes first and the tabs follow it`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = AppViewModel(FakeSettingsRepository(), auth)

        assertEquals(AppGate.SignIn, viewModel.state.value.gate)
        auth.current.value = Account("u", "vernon@example.com")
        assertEquals(AppGate.App, viewModel.state.value.gate)
        auth.current.value = null
        assertEquals(AppGate.SignIn, viewModel.state.value.gate)
    }

    @Test
    fun `without a server the app opens straight away`() = runTest {
        val viewModel = AppViewModel(FakeSettingsRepository(), FakeAuthRepository(isAvailable = false))

        assertEquals(AppGate.App, viewModel.state.value.gate)
    }

    @Test
    fun `the theme follows Profile`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = AppViewModel(settings, FakeAuthRepository(isAvailable = false))

        settings.themeMode.value = ThemeMode.Dark

        assertEquals(ThemeMode.Dark, viewModel.state.value.theme)
    }
}
