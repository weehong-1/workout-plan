package com.vitrack.app.data.backend

import com.vitrack.app.testing.BASE_URL
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeTransport
import com.vitrack.app.testing.InMemorySecureStore
import com.vitrack.app.testing.USER_ID
import com.vitrack.app.testing.tokenJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SupabaseClientTest {

    private val config = BackendConfig(BASE_URL, "sb_publishable_test", "https://web.test")
    private val transport = FakeTransport()
    private val store = InMemorySecureStore()

    private fun client() = SupabaseClient(config, transport, store, FIXED_CLOCK)

    @Test
    fun `config is empty until both host and key are real`() {
        assertNull(BackendConfig.from("", "key", ""))
        assertNull(BackendConfig.from("your-project.supabase.co", "key", ""))
        assertNull(BackendConfig.from("abc.supabase.co", "", ""))
        val config = BackendConfig.from(" https://abc.supabase.co/ ", "sb_publishable_x", "workout.example.com")!!
        assertEquals("https://abc.supabase.co", config.supabaseUrl)
        assertEquals("https://workout.example.com", config.webAppUrl)
        assertNull(BackendConfig.from("abc.supabase.co", "key", "")!!.webAppUrl)
    }

    @Test
    fun `sign-in sends a code, then the code gives a stored session`() = runTest {
        transport.on("POST", "/auth/v1/otp", 200, "{}")
        transport.on("POST", "/auth/v1/verify", 200, tokenJson())
        val client = client()

        client.sendCode(" vernon@example.com ")
        val session = client.verify("vernon@example.com", "123456")

        val otp = transport.sent("POST", "/auth/v1/otp").single()
        assertEquals("sb_publishable_test", otp.headers["apikey"])
        assertTrue(otp.body!!.decodeToString().contains("\"email\":\"vernon@example.com\""))
        assertTrue(transport.sent("POST", "/auth/v1/verify").single().body!!.decodeToString().contains("\"token\":\"123456\""))
        assertEquals(USER_ID.lowercase(), session.userFolder)
        assertEquals(FIXED_CLOCK.instant().epochSecond + 3600, session.expiresAt)
        // A new client on the same phone picks the session up again.
        assertEquals("access-1", client().session.value?.accessToken)
    }

    @Test
    fun `calls carry the access token`() = runTest {
        transport.on("POST", "/auth/v1/verify", 200, tokenJson())
        transport.on("GET", "/rest/v1/share_links", 200, "[]")
        val client = client()
        client.verify("vernon@example.com", "123456")

        client.select("share_links", listOf("select" to "id", "order" to "created_at.desc"))

        val request = transport.sent("GET", "/rest/v1/share_links").single()
        assertEquals("Bearer access-1", request.headers["Authorization"])
        assertTrue(request.url.endsWith("?select=id&order=created_at.desc"))
    }

    @Test
    fun `a session about to expire is refreshed first`() = runTest {
        transport.on("POST", "/auth/v1/verify", 200, tokenJson(expiresIn = 30))
        transport.on("POST", "/auth/v1/token", 200, tokenJson(access = "access-2", refresh = "refresh-2"))
        transport.on("GET", "/rest/v1/share_links", 200, "[]")
        val client = client()
        client.verify("vernon@example.com", "123456")

        client.select("share_links", emptyList())

        val refresh = transport.sent("POST", "/auth/v1/token").single()
        assertTrue(refresh.url.endsWith("?grant_type=refresh_token"))
        assertTrue(refresh.body!!.decodeToString().contains("refresh-1"))
        assertEquals("Bearer access-2", transport.sent("GET", "/rest/v1/share_links").single().headers["Authorization"])
    }

    @Test
    fun `a refused refresh signs out`() = runTest {
        transport.on("POST", "/auth/v1/verify", 200, tokenJson(expiresIn = 30))
        transport.on("POST", "/auth/v1/token", 400, """{"error_description":"Invalid Refresh Token"}""")
        val client = client()
        client.verify("vernon@example.com", "123456")

        try {
            client.select("share_links", emptyList())
            fail("expected NotSignedIn")
        } catch (e: BackendException.NotSignedIn) {
            assertNull(client.session.value)
            assertTrue(store.values.isEmpty())
        }
    }

    @Test
    fun `server errors carry the server's message`() = runTest {
        transport.on("POST", "/auth/v1/verify", 403, """{"code":403,"msg":"Token has expired or is invalid"}""")

        try {
            client().verify("vernon@example.com", "000000")
            fail("expected Server")
        } catch (e: BackendException.Server) {
            assertEquals(403, e.status)
            assertEquals("Token has expired or is invalid", e.message)
        }
    }

    @Test
    fun `no connection is a network error`() = runTest {
        transport.offline = true

        try {
            client().sendCode("vernon@example.com")
            fail("expected Network")
        } catch (e: BackendException.Network) {
            assertNotNull(e.cause)
        }
    }

    @Test
    fun `calls that need a session fail without one`() = runTest {
        try {
            client().select("share_links", emptyList())
            fail("expected NotSignedIn")
        } catch (e: BackendException.NotSignedIn) {
            assertTrue(transport.requests.isEmpty())
        }
    }

    @Test
    fun `sign out forgets the session`() = runTest {
        transport.on("POST", "/auth/v1/verify", 200, tokenJson())
        transport.on("POST", "/auth/v1/logout", 204, "")
        val client = client()
        client.verify("vernon@example.com", "123456")

        client.signOut()

        assertNull(client.session.value)
        assertNull(client().session.value)
    }
}
