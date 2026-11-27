package com.vitrack.app.ui.account

import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.testing.FakeAuthRepository
import com.vitrack.app.testing.MainDispatcherRule
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

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun TestScope.viewModel(auth: FakeAuthRepository): AccountViewModel {
        val viewModel = AccountViewModel(auth)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `email, then code, signs in`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = viewModel(auth)

        assertFalse(viewModel.uiState.value.canSendCode)
        viewModel.setEmail("vernon@example.com")
        viewModel.sendCode()
        assertEquals(listOf("vernon@example.com"), auth.codesSent)
        assertEquals(SignInStep.Code, viewModel.uiState.value.step)

        viewModel.setCode("12 34-56")
        assertEquals("123456", viewModel.uiState.value.code)
        viewModel.verify()

        val state = viewModel.uiState.value
        assertTrue(state.isSignedIn)
        assertEquals("vernon@example.com", state.signedInEmail)
        assertEquals(SignInStep.Email, state.step)
    }

    @Test
    fun `a wrong code shows the server's message and stays on the code step`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        viewModel.setEmail("vernon@example.com")
        viewModel.sendCode()

        viewModel.setCode("000000")
        viewModel.verify()

        val state = viewModel.uiState.value
        assertFalse(state.isSignedIn)
        assertEquals(SignInStep.Code, state.step)
        assertEquals(AccountError.Server("Token has expired or is invalid"), state.error)
        viewModel.setCode("1")
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `no connection keeps the email step with an error`() = runTest {
        val auth = FakeAuthRepository().apply { failNext = BackendException.Network(RuntimeException()) }
        val viewModel = viewModel(auth)
        viewModel.setEmail("vernon@example.com")

        viewModel.sendCode()

        assertEquals(SignInStep.Email, viewModel.uiState.value.step)
        assertEquals(AccountError.Network, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `bad emails and short codes can't be sent`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = viewModel(auth)

        viewModel.setEmail("vernon@")
        viewModel.sendCode()
        assertTrue(auth.codesSent.isEmpty())

        viewModel.setEmail("vernon@example.com")
        viewModel.sendCode()
        viewModel.setCode("123")
        assertFalse(viewModel.uiState.value.canVerify)
    }

    @Test
    fun `changing email goes back and clears the code`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        viewModel.setEmail("vernon@example.com")
        viewModel.sendCode()
        viewModel.setCode("123456")

        viewModel.changeEmail()

        assertEquals(SignInStep.Email, viewModel.uiState.value.step)
        assertEquals("", viewModel.uiState.value.code)
        assertEquals("vernon@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun `sign out`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = viewModel(auth)
        viewModel.setEmail("vernon@example.com")
        viewModel.sendCode()
        viewModel.setCode("123456")
        viewModel.verify()

        viewModel.signOut()

        assertFalse(viewModel.uiState.value.isSignedIn)
    }

    @Test
    fun `without a server it says sign-in isn't set up`() = runTest {
        assertFalse(viewModel(FakeAuthRepository(isAvailable = false)).uiState.value.isAvailable)
    }
}
