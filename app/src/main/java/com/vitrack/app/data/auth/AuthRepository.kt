package com.vitrack.app.data.auth

import com.vitrack.app.data.backend.Backend
import com.vitrack.app.data.backend.BackendException
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

    suspend fun sendCode(email: String)

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
class SupabaseAuthRepository @Inject constructor(private val backend: Backend) : AuthRepository {

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

    override suspend fun sendCode(email: String) = client().sendCode(email)

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

    @Serializable
    private data class DeleteBody(
        @SerialName("expected_account_id") val expectedAccountId: String,
        val confirmation: String,
    )
}
