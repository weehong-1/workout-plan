package com.vitrack.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.auth.AuthRepository
import com.vitrack.app.data.backend.BackendException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInStep { Email, Code }

sealed interface EmailSignInError {
    data object Network : EmailSignInError

    /** The server's own message, such as an expired code. */
    data class Server(val message: String) : EmailSignInError
}

data class EmailSignInUiState(
    val step: SignInStep = SignInStep.Email,
    val email: String = "",
    val code: String = "",
    val isBusy: Boolean = false,
    val error: EmailSignInError? = null,
) {
    val canSendCode: Boolean get() = isEmail(email) && !isBusy
    val canVerify: Boolean get() = code.length in CODE_LENGTHS && !isBusy
}

/** Supabase sends 6-digit codes by default; projects can set up to 10. */
internal val CODE_LENGTHS = 6..10

internal fun isEmail(text: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(text.trim())

/**
 * Sign in with an emailed code, the web app's way. Once the code is
 * accepted the session changes and the app opens; nothing to navigate.
 */
@HiltViewModel
class EmailSignInViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(EmailSignInUiState())
    val state: StateFlow<EmailSignInUiState> = _state.asStateFlow()

    fun setEmail(email: String) = _state.update { it.copy(email = email.take(254), error = null) }

    fun setCode(code: String) = _state.update { it.copy(code = code.filter(Char::isDigit).take(CODE_LENGTHS.last), error = null) }

    /** Back to the email step, for a typo or a code that never came. */
    fun changeEmail() = _state.update { it.copy(step = SignInStep.Email, code = "", error = null) }

    fun sendCode() {
        val current = _state.value
        if (!current.canSendCode) return
        run {
            auth.sendCode(current.email.trim())
            _state.update { it.copy(step = SignInStep.Code, code = "") }
        }
    }

    fun verify() {
        val current = _state.value
        if (!current.canVerify) return
        run { auth.verify(current.email.trim(), current.code) }
    }

    private fun run(block: suspend () -> Unit) {
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val error = try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendException.Server) {
                EmailSignInError.Server(e.message.orEmpty())
            } catch (e: Exception) {
                EmailSignInError.Network
            }
            _state.update { it.copy(isBusy = false, error = error) }
        }
    }
}
