package com.vitrack.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.vitrack.app.data.food.SampleFoodSeeder
import com.vitrack.app.data.reminders.ReminderScheduler
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.weight.SampleWeightSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltAndroidApp
class VitrackApplication : Application() {

    @Inject lateinit var sampleWeightSeeder: SampleWeightSeeder
    @Inject lateinit var sampleFoodSeeder: SampleFoodSeeder
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var reminders: ReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Alarms follow the reminder switches, including when account deletion clears them.
        scope.launch { settings.reminders.distinctUntilChanged().collect { reminders.sync(it) } }
        // Debug builds only: fill Weight and Food with sample data for previews.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            scope.launch { sampleWeightSeeder.seedOnce() }
            scope.launch { sampleFoodSeeder.seedOnce() }
        }
    }
}
