package com.vitrack.app.ui.login

import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.testing.FakeAuthRepository
import com.vitrack.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailSignInViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `email, then code, signs in`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = EmailSignInViewModel(auth)

        assertFalse(viewModel.state.value.canSendCode)
        viewModel.setEmail("vernon@example.com")
        viewModel.sendCode()
        assertEquals(listOf("vernon@example.com"), auth.codesSent)
        assertEquals(SignInStep.Code, viewModel.state.value.step)

        viewModel.setCode("12 34-56")
        assertEquals("123456", viewModel.state.value.code)
        viewModel.verify()

        assertNotNull(auth.current.value)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a wrong code says so and stays on the code step`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = EmailSignInViewModel(auth)
        viewModel.setEmail("vernon@example.com")
        viewModel.sendCode()

        viewModel.setCode("000000")
        viewModel.verify()

        assertNull(auth.current.value)
        assertEquals(SignInStep.Code, viewModel.state.value.step)
        assertEquals(EmailSignInError.WrongCode, viewModel.state.value.error)
        viewModel.setCode("1")
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `the email limit reads as too many tries`() = runTest {
        val auth = FakeAuthRepository().apply { failNext = BackendException.Server(429, "email rate limit exceeded") }
        val viewModel = EmailSignInViewModel(auth)
        viewModel.setEmail("vernon@example.com")

        viewModel.sendCode()

        assertEquals(EmailSignInError.TooMany, viewModel.state.value.error)
        assertEquals(SignInStep.Email, viewModel.state.value.step)
    }

    @Test
    fun `no connection keeps the email step with an error`() = runTest {
        val auth = FakeAuthRepository().apply { failNext = BackendException.Network(RuntimeException()) }
        val viewModel = EmailSignInViewModel(auth)
        viewModel.setEmail("vernon@example.com")

        viewModel.sendCode()

        assertEquals(SignInStep.Email, viewModel.state.value.step)
        assertEquals(EmailSignInError.Network, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `bad emails and short codes can't be sent`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = EmailSignInViewModel(auth)

        viewModel.setEmail("vernon@")
        viewModel.sendCode()
        assertTrue(auth.codesSent.isEmpty())

        viewModel.setEmail("vernon@example.com")
        viewModel.sendCode()
        viewModel.setCode("123")
        assertFalse(viewModel.state.value.canVerify)
    }

    @Test
    fun `changing email goes back and clears the code`() = runTest {
        val viewModel = EmailSignInViewModel(FakeAuthRepository())
        viewModel.setEmail("vernon@example.com")
        viewModel.sendCode()
        viewModel.setCode("123456")

        viewModel.changeEmail()

        assertEquals(SignInStep.Email, viewModel.state.value.step)
        assertEquals("", viewModel.state.value.code)
        assertEquals("vernon@example.com", viewModel.state.value.email)
    }
}
