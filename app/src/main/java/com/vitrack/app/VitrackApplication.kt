package com.vitrack.app

import android.app.Application
import com.vitrack.app.data.account.SampleDataCleanup
import com.vitrack.app.data.reminders.ReminderScheduler
import com.vitrack.app.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltAndroidApp
class VitrackApplication : Application() {

    @Inject lateinit var sampleDataCleanup: SampleDataCleanup
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var reminders: ReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Alarms follow the reminder switches, including when account deletion clears them.
        scope.launch { settings.reminders.distinctUntilChanged().collect { reminders.sync(it) } }
        // Older debug builds seeded made-up weigh-ins and food photos; remove them once.
        scope.launch { sampleDataCleanup.runOnce() }
    }
}
