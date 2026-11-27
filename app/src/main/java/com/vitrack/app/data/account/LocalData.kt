package com.vitrack.app.data.account

import com.vitrack.app.data.backend.SecureStore
import com.vitrack.app.data.food.FoodPhotos
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.weight.PhotoStore
import com.vitrack.app.data.weight.db.VitrackDatabase
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the app keeps on this phone. */
fun interface LocalData {
    /** Removes weigh-ins, food photos, settings and saved secrets (account deletion). */
    suspend fun wipe()
}

class RoomLocalData @Inject constructor(
    private val database: VitrackDatabase,
    private val weightPhotos: PhotoStore,
    @param:FoodPhotos private val foodPhotos: PhotoStore,
    private val settings: SettingsRepository,
    private val secureStore: SecureStore,
) : LocalData {
    override suspend fun wipe() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        weightPhotos.deleteAll()
        foodPhotos.deleteAll()
        // Reminders follow settings, so clearing them also cancels any scheduled reminder.
        settings.clear()
        secureStore.clear()
    }
}
