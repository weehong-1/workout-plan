package com.vitrack.app.data.share

import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
    /** Null: works until revoked (spec D13). */
    val expiresOn: LocalDate?,
)

data class ShareLink(
    val id: String,
    val trainerName: String,
    val period: SharePeriod,
    val expiresOn: LocalDate?,
    val url: String,
)

/** Trainer links live on the server (spec D10): the existing Supabase project and web app. */
interface ShareRepository {
    /** False until sign-in and the server are set up in this build. */
    val isAvailable: Boolean

    fun observeLinks(): Flow<List<ShareLink>>

    /** Uploads what [link] includes and returns the new link. */
    suspend fun create(link: NewShareLink): ShareLink

    /** Deletes the link and its uploaded photos. */
    suspend fun revoke(id: String)
}

class SharingNotSetUpException : IllegalStateException("Sharing needs sign-in and the server, which aren't set up yet")

/**
 * Stands in until Supabase is connected: no links, and creating one fails
 * clearly. It never pretends a link was made.
 */
@Singleton
class NotConnectedShareRepository @Inject constructor() : ShareRepository {
    override val isAvailable: Boolean = false
    override fun observeLinks(): Flow<List<ShareLink>> = flowOf(emptyList())
    override suspend fun create(link: NewShareLink): ShareLink = throw SharingNotSetUpException()
    override suspend fun revoke(id: String) = throw SharingNotSetUpException()
}
