package com.vitrack.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitrack.app.data.reminders.ReminderNotifications
import com.vitrack.app.data.settings.ThemeMode
import com.vitrack.app.ui.app.AppGate
import com.vitrack.app.ui.app.AppViewModel
import com.vitrack.app.ui.login.LoginFlow
import com.vitrack.app.ui.navigation.VitrackApp
import com.vitrack.app.ui.theme.VitrackTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    /** The tab a reminder notification asked to open, until the app has opened it. */
    private val openTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTab.value = intent?.getStringExtra(ReminderNotifications.EXTRA_OPEN_TAB)
        setContent {
            val state by appViewModel.state.collectAsStateWithLifecycle()
            val dark = when (state.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // System bar icons follow the app's theme, which Profile can set apart from the phone's.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            VitrackTheme(darkTheme = dark) {
                when (state.gate) {
                    AppGate.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                    AppGate.SignIn -> LoginFlow()
                    AppGate.App -> VitrackApp(openTab = openTab.value, onTabOpened = { openTab.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(ReminderNotifications.EXTRA_OPEN_TAB)?.let { openTab.value = it }
    }
}
