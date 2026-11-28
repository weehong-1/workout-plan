package com.vitrack.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.auth.AuthRepository
import com.vitrack.app.data.auth.SIGN_IN_REDIRECT
import com.vitrack.app.data.auth.SignInLinkException
import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.settings.ThemeMode
import com.vitrack.app.data.whoop.WhoopRepository
import com.vitrack.app.data.whoop.whoopReturnOf
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

enum class AppGate {
    /** Settings and the session are still being read. */
    Loading,

    /** A server is set up and nobody is signed in: Sign in comes first. */
    SignIn,
    App,
}

/** How opening an emailed sign-in link went, while still on Sign in. */
enum class LinkProblem { Expired, OtherRequest, Network, Failed }

data class AppState(
    val theme: ThemeMode = ThemeMode.System,
    val gate: AppGate = AppGate.Loading,
    /** An emailed sign-in link is being turned into a session. */
    val openingLink: Boolean = false,
    val linkProblem: LinkProblem? = null,
)

private data class LinkState(val opening: Boolean = false, val problem: LinkProblem? = null)

/**
 * What the whole app shows: Sign in or the tabs, in the theme chosen in
 * Profile. Sign-in is required whenever the build has a server; without one
 * (a development build) the app opens straight to the tabs. Also finishes
 * sign-in when the emailed link opens the app.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    private val auth: AuthRepository,
    private val whoop: WhoopRepository,
) : ViewModel() {

    private val link = MutableStateFlow(LinkState())

    val state: StateFlow<AppState> = combine(settings.themeMode, auth.account, link) { theme, account, l ->
        AppState(
            theme = theme,
            gate = if (auth.isAvailable && account == null) AppGate.SignIn else AppGate.App,
            openingLink = l.opening,
            linkProblem = l.problem,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState())

    /** Called with the URI the app was opened with: a sign-in link, or WHOOP sending the person back. */
    fun openLink(uri: String?) {
        if (uri != null && whoopReturnOf(uri) != null) {
            viewModelScope.launch { whoop.completeReturn(uri) }
            return
        }
        if (uri == null || !uri.startsWith(SIGN_IN_REDIRECT) || !auth.isAvailable) return
        link.value = LinkState(opening = true)
        viewModelScope.launch {
            val problem = try {
                auth.completeSignInLink(uri)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignInLinkException) {
                when (e.reason) {
                    SignInLinkException.Reason.Expired -> LinkProblem.Expired
                    SignInLinkException.Reason.OtherRequest -> LinkProblem.OtherRequest
                    else -> LinkProblem.Failed
                }
            } catch (e: BackendException.Network) {
                LinkProblem.Network
            } catch (e: Exception) {
                LinkProblem.Failed
            }
            link.value = LinkState(problem = problem)
        }
    }

    fun dismissLinkProblem() = link.update { it.copy(problem = null) }
}
