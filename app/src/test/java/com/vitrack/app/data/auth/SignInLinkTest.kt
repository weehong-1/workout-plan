package com.vitrack.app.data.auth

import com.vitrack.app.data.backend.Backend
import com.vitrack.app.data.backend.BackendConfig
import com.vitrack.app.data.backend.SupabaseClient
import com.vitrack.app.testing.BASE_URL
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeTransport
import com.vitrack.app.testing.InMemorySecureStore
import com.vitrack.app.testing.tokenJson
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SignInLinkTest {

    private val transport = FakeTransport()
    private val store = InMemorySecureStore()
    private val client = SupabaseClient(BackendConfig(BASE_URL, "key", "https://web.test"), transport, store, FIXED_CLOCK)
    private val auth = SupabaseAuthRepository(Backend(client), store)

    @Test
    fun `the email asks to return to the app, with a PKCE challenge`() = runTest {
        transport.on("POST", "/auth/v1/otp", 200, "{}")

        auth.sendCode("vernon@example.com")

        val request = transport.sent("POST", "/auth/v1/otp").single()
        assertTrue(request.url, request.url.endsWith("?redirect_to=vitrack%3A%2F%2Flogin-callback"))
        val body = request.body!!.decodeToString()
        val verifier = store.values["auth.pkce_verifier"]!!
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        assertTrue(body, body.contains("\"code_challenge\":\"$challenge\"") && body.contains("\"code_challenge_method\":\"s256\""))
        assertTrue(verifier.length >= 43)
    }

    @Test
    fun `the link's code and the kept verifier make the session`() = runTest {
        transport.on("POST", "/auth/v1/otp", 200, "{}")
        transport.on("POST", "/auth/v1/token", 200, tokenJson())
        auth.sendCode("vernon@example.com")
        val verifier = store.values["auth.pkce_verifier"]!!

        auth.completeSignInLink("vitrack://login-callback?code=abc-123")

        val exchange = transport.sent("POST", "/auth/v1/token").single()
        assertTrue(exchange.url.endsWith("?grant_type=pkce"))
        assertEquals("""{"auth_code":"abc-123","code_verifier":"$verifier"}""", exchange.body!!.decodeToString())
        assertNotNull(client.session.value)
        assertNull(store.values["auth.pkce_verifier"])
    }

    @Test
    fun `an expired link says so and signs nobody in`() = runTest {
        try {
            auth.completeSignInLink("vitrack://login-callback#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired")
            fail("expected SignInLinkException")
        } catch (e: SignInLinkException) {
            assertEquals(SignInLinkException.Reason.Expired, e.reason)
            assertNull(client.session.value)
        }
    }

    @Test
    fun `a link this phone didn't ask for is refused`() = runTest {
        try {
            auth.completeSignInLink("vitrack://login-callback?code=abc")
            fail("expected SignInLinkException")
        } catch (e: SignInLinkException) {
            assertEquals(SignInLinkException.Reason.OtherRequest, e.reason)
            assertTrue(transport.requests.isEmpty())
        }
    }

    @Test
    fun `a used or earlier link is reported as expired`() = runTest {
        store.put("auth.pkce_verifier", "v".repeat(43))
        transport.on("POST", "/auth/v1/token", 400, """{"error_code":"flow_state_not_found","msg":"invalid flow state, no valid flow state found"}""")

        try {
            auth.completeSignInLink("vitrack://login-callback?code=old")
            fail("expected SignInLinkException")
        } catch (e: SignInLinkException) {
            assertEquals(SignInLinkException.Reason.Expired, e.reason)
        }
    }

    @Test
    fun `only our redirect is read`() {
        assertNull(signInLinkParams("https://workout-plan-ruddy.vercel.app/account?code=x"))
        assertEquals(mapOf("code" to "a b"), signInLinkParams("vitrack://login-callback?code=a%20b"))
    }
}
