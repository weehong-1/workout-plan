package com.vitrack.app.data.auth

import com.vitrack.app.data.backend.Backend
import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.data.backend.SecureStore
import com.vitrack.app.data.backend.SupabaseClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SignInMethod { Google, Email }

/** The signed-in account. */
data class Account(
    val userId: String,
    val email: String?,
    /** From Google; null for an email-code account. */
    val name: String? = null,
    val method: SignInMethod = SignInMethod.Email,
)

/**
 * Sign-in to the web app's Supabase account: Google (an ID token from
 * Android's Credential Manager) or an emailed code. The app needs an account
 * when a server is set up in the build.
 */
interface AuthRepository {
    /** False when this build has no server set up: the app then runs without an account. */
    val isAvailable: Boolean

    /** The Google client ID to ask Credential Manager for, or null when Google sign-in isn't set up. */
    val googleWebClientId: String?

    val account: Flow<Account?>

    suspend fun signInWithGoogle(idToken: String, nonce: String)

    /** Emails a sign-in link that opens this app (and a code, when the email template shows one). */
    suspend fun sendCode(email: String)

    /**
     * Finishes sign-in from the emailed link ([SIGN_IN_REDIRECT] with a
     * `code`). Throws [SignInLinkException] for an expired, used or foreign link.
     */
    suspend fun completeSignInLink(link: String)

    suspend fun verify(email: String, code: String)

    suspend fun signOut()

    /**
     * Deletes the account on the server: its photos in storage, then the
     * account and every row it owns (the web app's `delete_own_account`).
     * Signs out when done.
     */
    suspend fun deleteAccount()
}

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val backend: Backend,
    private val secureStore: SecureStore,
) : AuthRepository {

    override val isAvailable: Boolean get() = backend.isConfigured

    override val googleWebClientId: String? get() = backend.client?.config?.googleWebClientId

    override val account: Flow<Account?> =
        backend.client?.session?.map { session ->
            session?.let {
                Account(
                    userId = it.userId,
                    email = it.email,
                    name = it.name,
                    method = if (it.provider == "google") SignInMethod.Google else SignInMethod.Email,
                )
            }
        } ?: flowOf(null)

    override suspend fun signInWithGoogle(idToken: String, nonce: String) {
        client().signInWithIdToken("google", idToken, nonce)
    }

    override suspend fun sendCode(email: String) {
        val client = client()
        // A new verifier for every email: only the newest link signs in.
        val verifier = Pkce.newVerifier()
        secureStore.put(PKCE_VERIFIER_KEY, verifier)
        client.sendCode(email, redirectTo = SIGN_IN_REDIRECT, codeChallenge = Pkce.challenge(verifier))
    }

    override suspend fun completeSignInLink(link: String) {
        val client = client()
        val params = signInLinkParams(link) ?: throw SignInLinkException(SignInLinkException.Reason.NotOurs)
        params["error_code"]?.let { code ->
            throw SignInLinkException(if (code == "otp_expired") SignInLinkException.Reason.Expired else SignInLinkException.Reason.Failed)
        }
        val code = params["code"] ?: throw SignInLinkException(SignInLinkException.Reason.Failed)
        val verifier = secureStore.get(PKCE_VERIFIER_KEY) ?: throw SignInLinkException(SignInLinkException.Reason.OtherRequest)
        try {
            client.exchangeCode(code, verifier)
        } catch (e: BackendException.Server) {
            // A link from an earlier email, or one already used.
            throw SignInLinkException(SignInLinkException.Reason.Expired)
        }
        secureStore.put(PKCE_VERIFIER_KEY, null)
    }

    override suspend fun verify(email: String, code: String) {
        client().verify(email, code)
    }

    override suspend fun signOut() {
        backend.client?.signOut()
    }

    override suspend fun deleteAccount() {
        val client = client()
        val session = client.validSession()
        // Storage files aren't removed with the account: remove the folder's photos first.
        val photos = client.listObjects(session.userFolder)
        if (photos.isNotEmpty()) client.removeObjects(photos)
        client.rpc(
            "delete_own_account",
            SupabaseClient.json.encodeToString(DeleteBody(expectedAccountId = session.userId, confirmation = "DELETE")),
        )
        client.clearSession()
    }

    private fun client() = backend.client ?: throw BackendException.NotConfigured()

    private companion object {
        const val PKCE_VERIFIER_KEY = "auth.pkce_verifier"
    }

    @Serializable
    private data class DeleteBody(
        @SerialName("expected_account_id") val expectedAccountId: String,
        val confirmation: String,
    )
}

/**
 * Where the emailed sign-in link returns: Android opens this app for it
 * (AndroidManifest). It must be in Supabase's Redirect URLs, or Supabase
 * sends the link to the web app's Site URL instead.
 */
const val SIGN_IN_REDIRECT = "vitrack://login-callback"

class SignInLinkException(val reason: Reason) : Exception("Sign-in link failed: $reason") {
    enum class Reason {
        /** Expired, already used, or from an earlier email. */
        Expired,

        /** This phone didn't ask for it (or the app was reinstalled since). */
        OtherRequest,

        /** Not a Vitrack sign-in link. */
        NotOurs,
        Failed,
    }
}

/**
 * The parameters of a sign-in link back to [SIGN_IN_REDIRECT], from its query
 * (`?code=…`) and fragment (`#error_code=…`), or null when it isn't one.
 */
internal fun signInLinkParams(link: String): Map<String, String>? {
    if (!link.startsWith(SIGN_IN_REDIRECT)) return null
    val rest = link.removePrefix(SIGN_IN_REDIRECT)
    val parts = rest.split('?', '#').drop(1).joinToString("&")
    return parts.split('&').filter { '=' in it }.associate {
        val (key, value) = it.split('=', limit = 2)
        java.net.URLDecoder.decode(key, Charsets.UTF_8) to java.net.URLDecoder.decode(value, Charsets.UTF_8)
    }
}

/** PKCE (RFC 7636, S256) for the emailed link. */
internal object Pkce {
    private val random = java.security.SecureRandom()

    fun newVerifier(): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    fun challenge(verifier: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
