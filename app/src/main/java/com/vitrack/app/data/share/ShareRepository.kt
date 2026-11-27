package com.vitrack.app.data.share

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/** The period a trainer link covers: a calendar month or a calendar year. */
sealed interface SharePeriod {
    val start: LocalDate
    val end: LocalDate

    data class Month(val month: YearMonth) : SharePeriod {
        override val start: LocalDate get() = month.atDay(1)
        override val end: LocalDate get() = month.atEndOfMonth()
    }

    data class Year(val year: Int) : SharePeriod {
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val end: LocalDate get() = LocalDate.of(year, 12, 31)
    }
}

/** What a link includes (spec 7). */
data class ShareContents(
    val activity: Boolean = true,
    val weight: Boolean = true,
    val weightPhotos: Boolean = false,
    val foodPhotos: Boolean = true,
) {
    val isEmpty: Boolean get() = !activity && !weight && !weightPhotos && !foodPhotos
}

data class NewShareLink(
    val trainerName: String,
    val period: SharePeriod,
    val contents: ShareContents,
    /** Last day the link works. Null: works until revoked (spec D13). */
    val expiresOn: LocalDate?,
)

data class ShareLink(
    val id: String,
    val trainerName: String,
    val period: SharePeriod,
    /** Last day the link works, or null for no expiry. */
    val expiresOn: LocalDate?,
    val isExpired: Boolean,
    /** The link again, when this phone made it; the token is kept only here. */
    val url: String?,
    /** Photos copied for the link, removed when it is revoked. */
    val photoPaths: List<String> = emptyList(),
)

/** Trainer links live on the server (spec D10): the existing Supabase project and web app. */
interface ShareRepository {
    /** False when this build has no server set up. */
    val isAvailable: Boolean

    val isSignedIn: Flow<Boolean>

    /** The account's links, newest first; refreshed from the server when collected. */
    fun observeLinks(): Flow<List<ShareLink>>

    suspend fun refresh()

    /**
     * Uploads what [link] includes, after the consent the page shows, and
     * returns the new link. [onProgress] reports photos uploaded of the total.
     */
    suspend fun create(link: NewShareLink, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ShareLink

    /** Deletes the link and its uploaded photos; the page stops working at once. */
    suspend fun revoke(id: String)
}

/** No server, or no web app host for the links, in this build. */
class SharingNotSetUpException : IllegalStateException("Sharing needs the server and web app, which aren't set up in this build")
