package com.vitrack.app.data.activity

import kotlinx.coroutines.flow.Flow

/**
 * Activity data from WHOOP. ViewModels depend on this interface; the sample
 * implementation is replaced by the WHOOP API one later.
 */
interface ActivityRepository {
    fun observeSnapshot(): Flow<ActivitySnapshot>

    /** The last [days] days ending today, oldest first. */
    fun observeHistory(days: Int): Flow<List<DailyActivity>>
}
