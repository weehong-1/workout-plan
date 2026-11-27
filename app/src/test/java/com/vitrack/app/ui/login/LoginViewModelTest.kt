package com.vitrack.app.ui.login

import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.testing.FakeAuthRepository
import com.vitrack.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `Google asks with the hashed nonce and signs in with the raw one`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = LoginViewModel(auth)

        val request = viewModel.startGoogle()!!
        assertTrue(viewModel.state.value.isBusy)
        assertEquals("client.apps.googleusercontent.com", request.clientId)
        assertEquals(LoginViewModel.sha256Hex(request.rawNonce), request.hashedNonce)
        assertEquals(64, request.hashedNonce.length)

        viewModel.finishGoogle(request, "google-id-token")

        assertEquals(listOf("google-id-token" to request.rawNonce), auth.googleSignIns)
        assertNotNull(auth.current.value)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `each try gets a new nonce`() {
        val viewModel = LoginViewModel(FakeAuthRepository())
        val first = viewModel.startGoogle()!!
        viewModel.googleEnded(GoogleOutcome.Cancelled)
        val second = viewModel.startGoogle()!!

        assertNotEquals(first.rawNonce, second.rawNonce)
    }

    @Test
    fun `without a Google client it says so and starts nothing`() {
        val viewModel = LoginViewModel(FakeAuthRepository().apply { googleWebClientId = null })

        assertFalse(viewModel.state.value.googleAvailable)
        assertNull(viewModel.startGoogle())
        assertEquals(LoginError.GoogleNotSetUp, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `cancelling the Google sheet shows nothing, no account says so`() {
        val viewModel = LoginViewModel(FakeAuthRepository())
        viewModel.startGoogle()
        viewModel.googleEnded(GoogleOutcome.Cancelled)
        assertNull(viewModel.state.value.error)

        viewModel.startGoogle()
        viewModel.googleEnded(GoogleOutcome.NoAccount)
        assertEquals(LoginError.NoGoogleAccount, viewModel.state.value.error)
    }

    @Test
    fun `Supabase refusing the token shows its message`() = runTest {
        val auth = FakeAuthRepository().apply { failNext = BackendException.Server(400, "Provider (issuer \"https://accounts.google.com\") is not enabled") }
        val viewModel = LoginViewModel(auth)
        val request = viewModel.startGoogle()!!

        viewModel.finishGoogle(request, "token")

        assertNull(auth.current.value)
        assertTrue((viewModel.state.value.error as LoginError.Server).message.contains("not enabled"))
    }
}
