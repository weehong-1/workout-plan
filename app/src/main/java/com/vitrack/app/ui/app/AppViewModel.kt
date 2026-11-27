package com.vitrack.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.auth.AuthRepository
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class AppGate {
    /** Settings and the session are still being read. */
    Loading,

    /** A server is set up and nobody is signed in: Sign in comes first. */
    SignIn,
    App,
}

data class AppState(val theme: ThemeMode = ThemeMode.System, val gate: AppGate = AppGate.Loading)

/**
 * What the whole app shows: Sign in or the tabs, in the theme chosen in
 * Profile. Sign-in is required whenever the build has a server; without one
 * (a development build) the app opens straight to the tabs.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    auth: AuthRepository,
) : ViewModel() {
    val state: StateFlow<AppState> = combine(settings.themeMode, auth.account) { theme, account ->
        AppState(
            theme = theme,
            gate = if (auth.isAvailable && account == null) AppGate.SignIn else AppGate.App,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState())
}
