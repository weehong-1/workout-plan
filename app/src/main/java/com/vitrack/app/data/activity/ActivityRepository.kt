package com.vitrack.app.data.activity

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Activity data from WHOOP. ViewModels depend on this interface. With WHOOP
 * not connected there is no activity: the snapshot is null and the history
 * empty, so screens ask to connect instead of showing made-up numbers.
 */
interface ActivityRepository {
    /**
     * The activity of [day] (today when null) with the week up to it, or null
     * while WHOOP isn't connected.
     */
    fun observeSnapshot(day: LocalDate? = null): Flow<ActivitySnapshot?>

    /** The last [days] days ending today, oldest first; empty while WHOOP isn't connected. */
    fun observeHistory(days: Int): Flow<List<DailyActivity>>
}
