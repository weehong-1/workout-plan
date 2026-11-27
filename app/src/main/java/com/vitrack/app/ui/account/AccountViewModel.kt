package com.vitrack.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.auth.AuthRepository
import com.vitrack.app.data.backend.BackendException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInStep { Email, Code }

sealed interface AccountError {
    data object NotSetUp : AccountError
    data object Network : AccountError
    /** The server's own message, such as an expired code. */
    data class Server(val message: String) : AccountError
}

data class AccountUiState(
    val isAvailable: Boolean = false,
    /** Signed-in email, or null when signed out. */
    val signedInEmail: String? = null,
    val isSignedIn: Boolean = false,
    val step: SignInStep = SignInStep.Email,
    val email: String = "",
    val code: String = "",
    val isBusy: Boolean = false,
    val error: AccountError? = null,
) {
    val canSendCode: Boolean get() = isEmail(email) && !isBusy
    val canVerify: Boolean get() = code.length in CODE_LENGTHS && !isBusy
}

/** Supabase sends 6-digit codes by default; projects can set up to 10. */
internal val CODE_LENGTHS = 6..10

internal fun isEmail(text: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(text.trim())

private data class Form(
    val step: SignInStep = SignInStep.Email,
    val email: String = "",
    val code: String = "",
    val isBusy: Boolean = false,
    val error: AccountError? = null,
)

/** Optional sign-in with an emailed code (spec D11), the web app's account. */
@HiltViewModel
class AccountViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val form = MutableStateFlow(Form())

    val uiState: StateFlow<AccountUiState> = combine(form, auth.account) { f, account ->
        AccountUiState(
            isAvailable = auth.isAvailable,
            signedInEmail = account?.email,
            isSignedIn = account != null,
            step = f.step,
            email = f.email,
            code = f.code,
            isBusy = f.isBusy,
            error = f.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState(isAvailable = auth.isAvailable))

    fun setEmail(email: String) = form.update { it.copy(email = email.take(254), error = null) }

    fun setCode(code: String) = form.update { it.copy(code = code.filter(Char::isDigit).take(CODE_LENGTHS.last), error = null) }

    /** Back to the email step, for a typo or a code that never came. */
    fun changeEmail() = form.update { it.copy(step = SignInStep.Email, code = "", error = null) }

    fun sendCode() {
        val current = form.value
        if (!isEmail(current.email) || current.isBusy) return
        run { auth.sendCode(current.email.trim()); form.update { it.copy(step = SignInStep.Code, code = "") } }
    }

    fun verify() {
        val current = form.value
        if (current.code.length !in CODE_LENGTHS || current.isBusy) return
        run { auth.verify(current.email.trim(), current.code); form.value = Form() }
    }

    fun signOut() {
        run { auth.signOut(); form.value = Form() }
    }

    private fun run(block: suspend () -> Unit) {
        form.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val error = try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendException.NotConfigured) {
                AccountError.NotSetUp
            } catch (e: BackendException.Network) {
                AccountError.Network
            } catch (e: BackendException.Server) {
                AccountError.Server(e.message.orEmpty())
            } catch (e: Exception) {
                AccountError.Network
            }
            form.update { it.copy(isBusy = false, error = error) }
        }
    }
}
