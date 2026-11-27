package com.vitrack.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.auth.AuthRepository
import com.vitrack.app.data.backend.BackendException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LoginError {
    /** Google isn't set up in this build (no client ID). */
    data object GoogleNotSetUp : LoginError

    /** The phone has no Google account to offer. */
    data object NoGoogleAccount : LoginError
    data object Network : LoginError
    data class Server(val message: String) : LoginError
    data object Failed : LoginError
}

data class LoginUiState(
    val googleAvailable: Boolean = false,
    val isBusy: Boolean = false,
    val error: LoginError? = null,
)

/** What the screen asks Google for: the client, and the SHA-256 of a nonce kept here. */
data class GoogleRequest(val clientId: String, val hashedNonce: String, internal val rawNonce: String)

/** How asking Google for a credential ended, before Supabase is involved. */
enum class GoogleOutcome { Cancelled, NoAccount, Failed }

/**
 * Sign in (required when the build has a server): Google through Android's
 * Credential Manager, exchanged for a Supabase session, or an emailed code
 * (its own screen). A fresh nonce for each try ties Google's token to it.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState(googleAvailable = auth.googleWebClientId != null))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /** Starts a Google sign-in; null (with an error shown) when Google isn't set up. */
    fun startGoogle(): GoogleRequest? {
        if (_state.value.isBusy) return null
        val clientId = auth.googleWebClientId
        if (clientId == null) {
            _state.update { it.copy(error = LoginError.GoogleNotSetUp) }
            return null
        }
        val raw = newNonce()
        _state.update { it.copy(isBusy = true, error = null) }
        return GoogleRequest(clientId, sha256Hex(raw), raw)
    }

    /** Google gave an ID token: exchange it for the account's session. */
    fun finishGoogle(request: GoogleRequest, idToken: String) {
        viewModelScope.launch {
            val error = try {
                auth.signInWithGoogle(idToken, request.rawNonce)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendException.Network) {
                LoginError.Network
            } catch (e: BackendException.Server) {
                LoginError.Server(e.message.orEmpty())
            } catch (e: Exception) {
                LoginError.Failed
            }
            _state.update { it.copy(isBusy = false, error = error) }
        }
    }

    fun googleEnded(outcome: GoogleOutcome) {
        _state.update {
            it.copy(
                isBusy = false,
                error = when (outcome) {
                    GoogleOutcome.Cancelled -> null
                    GoogleOutcome.NoAccount -> LoginError.NoGoogleAccount
                    GoogleOutcome.Failed -> LoginError.Failed
                },
            )
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    companion object {
        private val random = SecureRandom()

        internal fun newNonce(): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

        internal fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }
}
