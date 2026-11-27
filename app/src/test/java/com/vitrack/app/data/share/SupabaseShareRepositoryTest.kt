package com.vitrack.app.data.share

import com.vitrack.app.data.backend.Backend
import com.vitrack.app.data.backend.BackendConfig
import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.data.backend.HttpResponse
import com.vitrack.app.data.backend.SupabaseClient
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.testing.BASE_URL
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeTransport
import com.vitrack.app.testing.InMemorySecureStore
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.USER_ID
import com.vitrack.app.testing.foodPhoto
import com.vitrack.app.testing.tokenJson
import com.vitrack.app.testing.weighIn
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SupabaseShareRepositoryTest {

    private val transport = FakeTransport()
    private val store = InMemorySecureStore()
    private val november = SharePeriod.Month(YearMonth.of(2026, 11))
    private val folder = USER_ID.lowercase()

    private val loader = ShareSourceLoader { period ->
        ShareSource(
            period = period,
            today = TODAY,
            unit = WeightUnit.Kg,
            activity = emptyList(),
            weighIns = listOf(weighIn(LocalDate.of(2026, 11, 2), 80.2, photoIds = listOf(1)), weighIn(TODAY, 78.4, photoIds = listOf(2))),
            foodPhotos = listOf(foodPhoto(10, TODAY.atTime(8, 0))),
        )
    }

    private fun client(web: String? = "https://web.test") =
        SupabaseClient(BackendConfig(BASE_URL, "key", web), transport, store, FIXED_CLOCK)

    private fun repository(client: SupabaseClient? = client()) =
        SupabaseShareRepository(Backend(client), loader, { ByteArray(3) }, store, FIXED_CLOCK)

    private suspend fun signIn(client: SupabaseClient) {
        transport.on("POST", "/auth/v1/verify", 200, tokenJson())
        client.verify("vernon@example.com", "123456")
    }

    private fun link(expiresOn: LocalDate? = LocalDate.of(2026, 12, 31)) =
        NewShareLink("Alex", november, ShareContents(weightPhotos = true), expiresOn)

    @Test
    fun `no server, or no web app for the links, means not available`() = runTest {
        assertFalse(repository(client = null).isAvailable)
        assertFalse(repository(client(web = null)).isAvailable)
        assertTrue(repository().isAvailable)
        try {
            repository(client = null).create(link())
            fail("expected SharingNotSetUpException")
        } catch (e: SharingNotSetUpException) {
            assertTrue(transport.requests.isEmpty())
        }
    }

    @Test
    fun `creating needs a signed-in account`() = runTest {
        try {
            repository().create(link())
            fail("expected NotSignedIn")
        } catch (e: BackendException.NotSignedIn) {
            assertTrue(transport.requests.isEmpty())
        }
    }

    @Test
    fun `create uploads photos, then a row holding only the token's hash`() = runTest {
        val client = client()
        signIn(client)
        transport.on("POST", "/storage/v1/object/photos/", 200, "{}")
        transport.on("POST", "/rest/v1/share_links", 201, "")
        transport.on("GET", "/rest/v1/share_links", 200, "[]")
        val progress = mutableListOf<Pair<Int, Int>>()

        val created = repository(client).create(link()) { done, total -> progress += done to total }

        val uploads = transport.sent("POST", "/storage/v1/object/photos/")
        assertEquals(3, uploads.size)
        uploads.forEach {
            assertEquals("image/jpeg", it.headers["Content-Type"])
            assertTrue(FakeTransport.path(it).startsWith("/storage/v1/object/photos/$folder/share-"))
        }
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), progress)

        val token = created.url!!.substringAfter("https://web.test/share/")
        assertEquals(43, token.length)
        val row = SupabaseClient.json.parseToJsonElement(transport.sent("POST", "/rest/v1/share_links").single().body!!.decodeToString()).jsonObject
        assertEquals(SupabaseShareRepository.sha256Hex(token), row["token_hash"]!!.jsonPrimitive.content)
        assertFalse(row.toString().contains(token))
        assertEquals("Alex", row["trainer_name"]!!.jsonPrimitive.content)
        assertEquals("month", row["period_kind"]!!.jsonPrimitive.content)
        assertEquals("2026-11-01", row["period_start"]!!.jsonPrimitive.content)
        assertEquals("2027-01-01T00:00Z", row["expires_at"]!!.jsonPrimitive.content)
        assertEquals(3, row["photo_paths"]!!.jsonArray.size)
        assertEquals("true", row["includes"]!!.jsonObject["food"]!!.jsonPrimitive.content)
        // The token stays on the phone so the link can be copied again.
        assertEquals(token, store.values["share.token.${created.id}"])
    }

    @Test
    fun `a failed row write removes the photos it uploaded`() = runTest {
        val client = client()
        signIn(client)
        transport.on("POST", "/storage/v1/object/photos/", 200, "{}")
        transport.on("POST", "/rest/v1/share_links", 500, """{"message":"boom"}""")
        transport.on("DELETE", "/storage/v1/object/photos", 200, "[]")

        try {
            repository(client).create(link())
            fail("expected Server")
        } catch (e: BackendException.Server) {
            val removed = transport.sent("DELETE", "/storage/v1/object/photos").single().body!!.decodeToString()
            assertEquals(3, Regex("share-").findAll(removed).count())
            assertTrue(store.values.keys.none { it.startsWith("share.token.") })
        }
    }

    @Test
    fun `links list with expiry, and a copy url only where this phone has the token`() = runTest {
        val client = client()
        signIn(client)
        store.put("share.token.a", "tok-a")
        transport.on("GET", "/rest/v1/share_links", 200,
            """[
              {"id":"a","trainer_name":"Alex","period_kind":"month","period_start":"2026-11-01","expires_at":"2027-01-01T00:00:00+00:00","created_at":"2026-11-26T08:00:00+00:00","photo_paths":["$folder/share-a-w1.jpg"]},
              {"id":"b","trainer_name":"Sam","period_kind":"year","period_start":"2025-01-01","expires_at":"2026-11-20T00:00:00+00:00","created_at":"2026-01-02T08:00:00+00:00","photo_paths":[]},
              {"id":"c","trainer_name":"Kim","period_kind":"month","period_start":"2026-10-01","expires_at":null,"created_at":"2026-10-02T08:00:00+00:00","photo_paths":[]}
            ]""")

        val links = repository(client).observeLinks().first { it.isNotEmpty() }

        assertEquals(listOf("a", "b", "c"), links.map { it.id })
        assertEquals(LocalDate.of(2026, 12, 31), links[0].expiresOn)
        assertFalse(links[0].isExpired)
        assertEquals("https://web.test/share/tok-a", links[0].url)
        assertEquals(SharePeriod.Year(2025), links[1].period)
        assertTrue(links[1].isExpired)
        assertNull(links[1].url)
        assertNull(links[2].expiresOn)
        assertFalse(links[2].isExpired)
    }

    @Test
    fun `signed out there are no links`() = runTest {
        assertEquals(emptyList<ShareLink>(), repository().observeLinks().first())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `revoke deletes the row, its photos and the token`() = runTest {
        val client = client()
        signIn(client)
        store.put("share.token.a", "tok-a")
        transport.on("GET", "/rest/v1/share_links", 200,
            """[{"id":"a","trainer_name":"Alex","period_kind":"month","period_start":"2026-11-01","photo_paths":["$folder/share-a-w1.jpg"]}]""")
        transport.on("DELETE", "/rest/v1/share_links") { HttpResponse(204, ByteArray(0)) }
        transport.on("DELETE", "/storage/v1/object/photos", 200, "[]")
        val repository = repository(client)
        repository.refresh()

        repository.revoke("a")

        assertTrue(transport.sent("DELETE", "/rest/v1/share_links").single().url.endsWith("?id=eq.a"))
        assertTrue(transport.sent("DELETE", "/storage/v1/object/photos").single().body!!.decodeToString().contains("share-a-w1.jpg"))
        assertNull(store.values["share.token.a"])
    }
}
