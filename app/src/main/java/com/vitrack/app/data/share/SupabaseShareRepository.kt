package com.vitrack.app.data.share

import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.backend.Backend
import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.data.backend.SecureStore
import com.vitrack.app.data.backend.SupabaseClient
import com.vitrack.app.data.food.FoodRepository
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.weight.WeightRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reads the phone's data for a link's period. */
fun interface ShareSourceLoader {
    suspend fun load(period: SharePeriod): ShareSource
}

class RepositoryShareSourceLoader @Inject constructor(
    private val activity: ActivityRepository,
    private val weight: WeightRepository,
    private val food: FoodRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ShareSourceLoader {
    override suspend fun load(period: SharePeriod): ShareSource {
        val today = LocalDate.now(clock)
        val range = period.daysSoFar(today)
        val daysBack = ChronoUnit.DAYS.between(range.start, today).toInt() + 1
        return ShareSource(
            period = period,
            today = today,
            unit = settings.weightUnit.first(),
            activity = if (range.start > range.endInclusive) emptyList() else activity.observeHistory(daysBack.coerceAtLeast(1)).first(),
            weighIns = weight.observeWeighIns().first(),
            foodPhotos = if (range.start > range.endInclusive) emptyList() else food.photosBetween(range.start, range.endInclusive),
        )
    }
}

/** Shrinks a stored photo for a link: JPEG, long side at most 1280 px. Null when it can't be read. */
fun interface SharePhotoEncoder {
    suspend fun encode(file: java.io.File): ByteArray?
}

/**
 * Trainer links in the existing Supabase project (spec 7, D10, D11), as the
 * iOS app makes them: the chosen summaries and photos are uploaded after
 * consent; the link holds a random token and only its SHA-256 hash goes to
 * the server. The token stays encrypted on this phone so the link can be
 * copied again.
 */
@Singleton
class SupabaseShareRepository @Inject constructor(
    private val backend: Backend,
    private val loader: ShareSourceLoader,
    private val encoder: SharePhotoEncoder,
    private val secureStore: SecureStore,
    private val clock: Clock,
) : ShareRepository {

    private val links = MutableStateFlow<List<ShareLink>>(emptyList())

    override val isAvailable: Boolean get() = backend.client != null && backend.webAppUrl != null

    override val isSignedIn: Flow<Boolean> = backend.client?.session?.map { it != null } ?: flowOf(false)

    override fun observeLinks(): Flow<List<ShareLink>> =
        combine(isSignedIn, links) { signedIn, all -> if (signedIn) all else emptyList() }
            .onStart { runCatching { refresh() } }

    override suspend fun refresh() {
        val client = backend.client
        if (client?.session?.value == null) {
            links.value = emptyList()
            return
        }
        val body = client.select(
            "share_links",
            listOf(
                "select" to "id,trainer_name,period_kind,period_start,expires_at,created_at,photo_paths",
                "order" to "created_at.desc",
            ),
        )
        val rows = try {
            SupabaseClient.json.decodeFromString<List<Row>>(body)
        } catch (e: Exception) {
            throw BackendException.InvalidResponse(e)
        }
        links.value = rows.mapNotNull { it.toLink() }
    }

    override suspend fun create(link: NewShareLink, onProgress: (done: Int, total: Int) -> Unit): ShareLink {
        val client = backend.client ?: throw SharingNotSetUpException()
        val base = backend.webAppUrl ?: throw SharingNotSetUpException()
        val session = client.validSession()
        val linkId = UUID.randomUUID().toString()
        val token = randomToken()
        val built = buildSharedReview(loader.load(link.period), link.contents, link.expiresOn, linkId, session.userFolder)

        // Photos first, then the row. On any failure, remove what was uploaded.
        val uploaded = mutableListOf<String>()
        try {
            built.uploads.forEachIndexed { index, upload ->
                onProgress(index, built.uploads.size)
                val jpeg = encoder.encode(upload.file) ?: return@forEachIndexed
                client.upload(jpeg, upload.path)
                uploaded += upload.path
            }
            onProgress(built.uploads.size, built.uploads.size)
            val row = Insert(
                id = linkId,
                tokenHash = sha256Hex(token),
                trainerName = link.trainerName.trim().take(80),
                periodKind = link.period.kind(),
                periodStart = link.period.start.toString(),
                periodLabel = built.summary.period.label,
                includes = link.contents.toIncludes(),
                summary = built.summary,
                photoPaths = uploaded,
                // Works through its last day: ends at the start of the next one, on this phone's clock.
                expiresAt = link.expiresOn?.plusDays(1)?.atStartOfDay(clock.zone)?.toOffsetDateTime()?.toString(),
            )
            client.insert("share_links", SupabaseClient.json.encodeToString(row))
        } catch (e: Throwable) {
            if (uploaded.isNotEmpty() && e !is CancellationException) runCatching { client.removeObjects(uploaded) }
            throw e
        }

        secureStore.put(TOKEN_PREFIX + linkId, token)
        runCatching { refresh() }
        return ShareLink(
            id = linkId,
            trainerName = link.trainerName.trim(),
            period = link.period,
            expiresOn = link.expiresOn,
            isExpired = false,
            url = shareUrl(base, token),
            photoPaths = uploaded,
        )
    }

    override suspend fun revoke(id: String) {
        val client = backend.client ?: throw SharingNotSetUpException()
        val photos = links.value.firstOrNull { it.id == id }?.photoPaths.orEmpty()
        client.delete("share_links", listOf("id" to "eq.$id"))
        if (photos.isNotEmpty()) runCatching { client.removeObjects(photos) }
        secureStore.put(TOKEN_PREFIX + id, null)
        links.value = links.value.filterNot { it.id == id }
    }

    private fun Row.toLink(): ShareLink? {
        val period = runCatching {
            val start = LocalDate.parse(periodStart)
            if (periodKind == "year") SharePeriod.Year(start.year) else SharePeriod.Month(YearMonth.from(start))
        }.getOrNull() ?: return null
        val ends = expiresAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        val token = secureStore.get(TOKEN_PREFIX + id)
        return ShareLink(
            id = id,
            trainerName = trainerName,
            period = period,
            expiresOn = ends?.atZoneSameInstant(clock.zone)?.toLocalDate()?.minusDays(1),
            isExpired = ends != null && !ends.toInstant().isAfter(clock.instant()),
            url = token?.let { t -> backend.webAppUrl?.let { shareUrl(it, t) } },
            photoPaths = photoPaths,
        )
    }

    @Serializable
    private data class Row(
        val id: String,
        @SerialName("trainer_name") val trainerName: String = "",
        @SerialName("period_kind") val periodKind: String,
        @SerialName("period_start") val periodStart: String,
        @SerialName("expires_at") val expiresAt: String? = null,
        @SerialName("photo_paths") val photoPaths: List<String> = emptyList(),
    )

    @Serializable
    private data class Insert(
        val id: String,
        @SerialName("token_hash") val tokenHash: String,
        @SerialName("trainer_name") val trainerName: String,
        @SerialName("period_kind") val periodKind: String,
        @SerialName("period_start") val periodStart: String,
        @SerialName("period_label") val periodLabel: String,
        val includes: ShareIncludes,
        val summary: SharedReviewPayload,
        @SerialName("photo_paths") val photoPaths: List<String>,
        @SerialName("expires_at") val expiresAt: String?,
    )

    companion object {
        private const val TOKEN_PREFIX = "share.token."
        private val random = SecureRandom()

        fun shareUrl(base: String, token: String) = "${base.trimEnd('/')}/share/$token"

        /** 32 random bytes, base64url without padding (43 characters). */
        fun randomToken(): String {
            val bytes = ByteArray(32).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }
}
