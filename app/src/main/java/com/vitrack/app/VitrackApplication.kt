package com.vitrack.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.vitrack.app.data.weight.SampleWeightSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VitrackApplication : Application() {

    @Inject lateinit var sampleWeightSeeder: SampleWeightSeeder

    override fun onCreate() {
        super.onCreate()
        // Debug builds only: fill Weight with sample weigh-ins for previews.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { sampleWeightSeeder.seedOnce() }
        }
    }
}
