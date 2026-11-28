package com.vitrack.app.data.whoop

import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.activity.ActivitySnapshot
import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.settings.DEFAULT_STEP_GOAL
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Activity from WHOOP once it's connected; none until then (a null snapshot,
 * empty history), so nothing made-up reaches the screens or a trainer link.
 * Records are kept for a few minutes so the tabs don't each ask WHOOP, and
 * asked for one request at a time (WHOOP's rate limit, one token refresh).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WhoopActivityRepository @Inject constructor(
    private val whoop: WhoopRepository,
    private val clock: Clock,
) : ActivityRepository {

    private class Cached(val from: LocalDate, val fetchedAt: Instant, val records: WhoopRecords)

    private val lock = Mutex()
    private var cached: Cached? = null

    override fun observeSnapshot(day: LocalDate?): Flow<ActivitySnapshot?> = whenConnected(null) {
        val shown = day ?: LocalDate.now(clock)
        WhoopMapper.snapshot(load(shown.minusDays(6)), shown, DEFAULT_STEP_GOAL, LocalTime.now(clock).withNano(0))
    }

    override fun observeHistory(days: Int): Flow<List<DailyActivity>> = whenConnected(emptyList()) {
        val today = LocalDate.now(clock)
        val from = today.minusDays(days - 1L)
        WhoopMapper.days(load(from), from, today)
    }

    private fun <T> whenConnected(notConnected: T, read: suspend () -> T): Flow<T> =
        whoop.status.flatMapLatest { status ->
            if (status != WhoopStatus.Connected) {
                cached = null
                flowOf(notConnected)
            } else {
                flow {
                    while (true) {
                        try {
                            emit(read())
                            return@flow
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: WhoopNotConnectedException) {
                            return@flow // The status turns NotConnected, which emits the empty value.
                        } catch (e: Exception) {
                            delay(RETRY_AFTER_MS) // Offline or WHOOP busy: try again shortly.
                        }
                    }
                }
            }
        }

    /** Records from [from] to now; WHOOP's cycle for [from] starts the evening before, so ask from then. */
    private suspend fun load(from: LocalDate): WhoopRecords = lock.withLock {
        val now = clock.instant()
        cached?.let { if (!it.from.isAfter(from) && Duration.between(it.fetchedAt, now) < FRESH_FOR) return it.records }
        val start = from.minusDays(1).atStartOfDay(clock.zone).toInstant()
        whoop.records(start, now).also { cached = Cached(from, now, it) }
    }

    private companion object {
        val FRESH_FOR: Duration = Duration.ofMinutes(5)
        const val RETRY_AFTER_MS = 30_000L
    }
}
