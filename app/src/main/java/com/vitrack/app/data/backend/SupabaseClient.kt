package com.vitrack.app.data.backend

import java.io.IOException
import java.net.URLEncoder
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A signed-in Supabase session. */
@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch seconds. */
    val expiresAt: Long,
    val userId: String,
    val email: String? = null,
    /** The account's name, from Google; null for an email-code account. */
    val name: String? = null,
    /** How the account signs in: "google" or "email". */
    val provider: String? = null,
) {
    /** `auth.uid()::text` in Postgres and storage folder names are lowercase. */
    val userFolder: String get() = userId.lowercase()
}

/**
 * Minimal Supabase client over plain HTTPS: Auth (email code, as in the web
 * app), PostgREST and Storage. Mirrors the iOS app's client. The session is
 * kept encrypted in [store].
 */
class SupabaseClient(
    val config: BackendConfig,
    private val transport: HttpTransport,
    private val store: SecureStore,
    private val clock: Clock,
) {
    private val _session = MutableStateFlow(
        store.get(SESSION_KEY)?.let { runCatching { json.decodeFromString<AuthSession>(it) }.getOrNull() },
    )
    val session: StateFlow<AuthSession?> = _session.asStateFlow()
    private val refreshLock = Mutex()

    // Auth

    /**
     * Emails a sign-in link (and a code, if the project's template shows one).
     * The link returns to [redirectTo] with a one-time `code`; [codeChallenge]
     * is the SHA-256 of a verifier kept on this phone (PKCE), so only this app
     * can turn that code into a session.
     */
    suspend fun sendCode(email: String, redirectTo: String? = null, codeChallenge: String? = null) {
        send(
            "POST", "/auth/v1/otp",
            query = listOfNotNull(redirectTo?.let { "redirect_to" to it }),
            body = json.encodeToString(
                OtpBody(
                    email = email.trim(),
                    createUser = true,
                    codeChallenge = codeChallenge,
                    codeChallengeMethod = codeChallenge?.let { "s256" },
                ),
            ),
            authorized = false,
        )
    }

    /** Turns the `code` from a sign-in link into a session, with the verifier kept when the email was sent. */
    suspend fun exchangeCode(authCode: String, codeVerifier: String): AuthSession {
        val response = send(
            "POST", "/auth/v1/token", query = listOf("grant_type" to "pkce"),
            body = json.encodeToString(PkceBody(authCode, codeVerifier)),
            authorized = false,
        )
        return store(response)
    }

    /**
     * Signs in with an ID token from Google (Android Credential Manager). [nonce]
     * is the raw nonce whose SHA-256 was given to Google; Supabase checks it.
     */
    suspend fun signInWithIdToken(provider: String, idToken: String, nonce: String): AuthSession {
        val response = send(
            "POST", "/auth/v1/token", query = listOf("grant_type" to "id_token"),
            body = json.encodeToString(IdTokenBody(provider, idToken, nonce)),
            authorized = false,
        )
        return store(response)
    }

    suspend fun verify(email: String, code: String): AuthSession {
        val response = send(
            "POST", "/auth/v1/verify",
            body = json.encodeToString(VerifyBody(email = email.trim(), token = code.trim())),
            authorized = false,
        )
        return store(response)
    }

    suspend fun signOut() {
        if (_session.value != null) runCatching { send("POST", "/auth/v1/logout", authorized = true) }
        clearSession()
    }

    fun clearSession() {
        _session.value = null
        store.put(SESSION_KEY, null)
    }

    /** The session, refreshed first when it expires within a minute. */
    suspend fun validSession(): AuthSession = refreshLock.withLock {
        val current = _session.value ?: throw BackendException.NotSignedIn()
        if (current.expiresAt - clock.instant().epochSecond > 60) return@withLock current
        try {
            val response = send(
                "POST", "/auth/v1/token", query = listOf("grant_type" to "refresh_token"),
                body = json.encodeToString(RefreshBody(current.refreshToken)),
                authorized = false,
            )
            store(response)
        } catch (e: BackendException.Server) {
            if (e.status == 400 || e.status == 401) {
                clearSession()
                throw BackendException.NotSignedIn()
            }
            throw e
        }
    }

    private fun store(response: String): AuthSession {
        val token = try {
            json.decodeFromString<TokenResponse>(response)
        } catch (e: Exception) {
            throw BackendException.InvalidResponse(e)
        }
        val session = AuthSession(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresAt = clock.instant().epochSecond + token.expiresIn,
            userId = token.user.id,
            email = token.user.email,
            name = token.user.userMetadata?.let { it.fullName ?: it.name }?.trim()?.ifEmpty { null },
            provider = token.user.appMetadata?.provider,
        )
        _session.value = session
        store.put(SESSION_KEY, json.encodeToString(session))
        return session
    }

    // Database (PostgREST)

    suspend fun rpc(function: String, body: String): String =
        send("POST", "/rest/v1/rpc/$function", body = body, authorized = true)

    suspend fun select(table: String, query: List<Pair<String, String>>): String =
        send("GET", "/rest/v1/$table", query = query, authorized = true)

    suspend fun insert(table: String, body: String) {
        send("POST", "/rest/v1/$table", body = body, authorized = true, headers = mapOf("Prefer" to "return=minimal"))
    }

    suspend fun delete(table: String, query: List<Pair<String, String>>) {
        send("DELETE", "/rest/v1/$table", query = query, authorized = true)
    }

    // Edge Functions

    /** Calls the Edge Function [name] with a JSON [body], signed in. */
    suspend fun function(name: String, body: String): String =
        send("POST", "/functions/v1/$name", body = body, authorized = true)

    // Storage (private "photos" bucket, one folder per account)

    suspend fun upload(jpeg: ByteArray, path: String) {
        send(
            "POST", "/storage/v1/object/photos/$path", bytes = jpeg, authorized = true,
            headers = mapOf("Content-Type" to "image/jpeg", "x-upsert" to "true"),
        )
    }

    /** Files directly inside [folder] (not sub-folders), as full paths. */
    suspend fun listObjects(folder: String): List<String> {
        val paths = mutableListOf<String>()
        var offset = 0
        while (true) {
            val body = send(
                "POST", "/storage/v1/object/list/photos",
                body = json.encodeToString(ListBody(prefix = folder, limit = 1_000, offset = offset)),
                authorized = true,
            )
            val items = try {
                json.decodeFromString<List<ListItem>>(body)
            } catch (e: Exception) {
                throw BackendException.InvalidResponse(e)
            }
            paths += items.filter { it.id != null }.map { "$folder/${it.name}" }
            if (items.size < 1_000) break
            offset += items.size
        }
        return paths
    }

    suspend fun removeObjects(paths: List<String>) {
        paths.chunked(500).forEach { chunk ->
            send("DELETE", "/storage/v1/object/photos", body = json.encodeToString(RemoveBody(chunk)), authorized = true)
        }
    }

    // Transport

    private suspend fun send(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: String? = null,
        bytes: ByteArray? = null,
        authorized: Boolean,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val url = buildString {
            append(config.supabaseUrl).append(path)
            if (query.isNotEmpty()) {
                append('?')
                append(query.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" })
            }
        }
        val allHeaders = buildMap {
            put("apikey", config.publishableKey)
            if (body != null) put("Content-Type", "application/json")
            if (authorized) put("Authorization", "Bearer ${validSession().accessToken}")
            putAll(headers)
        }
        val response = try {
            transport.send(HttpRequest(method, url, allHeaders, bytes ?: body?.encodeToByteArray()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw BackendException.Network(e)
        }
        if (response.status !in 200..299) {
            throw BackendException.Server(response.status, message(response.text, response.status))
        }
        return response.text
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    /** Supabase services name their error field differently. */
    private fun message(text: String, status: Int): String {
        val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        val found = listOf("msg", "message", "error_description", "error").firstNotNullOfOrNull { key ->
            (obj?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
        return found ?: "The server returned an error ($status)."
    }

    @Serializable
    private data class OtpBody(
        val email: String,
        @SerialName("create_user") val createUser: Boolean,
        @SerialName("code_challenge") val codeChallenge: String? = null,
        @SerialName("code_challenge_method") val codeChallengeMethod: String? = null,
    )

    @Serializable
    private data class PkceBody(@SerialName("auth_code") val authCode: String, @SerialName("code_verifier") val codeVerifier: String)

    @Serializable
    private data class VerifyBody(val type: String = "email", val email: String, val token: String)

    @Serializable
    private data class RefreshBody(@SerialName("refresh_token") val refreshToken: String)

    @Serializable
    private data class RemoveBody(val prefixes: List<String>)

    @Serializable
    private data class IdTokenBody(val provider: String, @SerialName("id_token") val idToken: String, val nonce: String)

    @Serializable
    private data class ListBody(val prefix: String, val limit: Int, val offset: Int)

    @Serializable
    private data class ListItem(val name: String, val id: String? = null)

    @Serializable
    private data class UserMetadata(@SerialName("full_name") val fullName: String? = null, val name: String? = null)

    @Serializable
    private data class AppMetadata(val provider: String? = null)

    @Serializable
    private data class TokenUser(
        val id: String,
        val email: String? = null,
        @SerialName("user_metadata") val userMetadata: UserMetadata? = null,
        @SerialName("app_metadata") val appMetadata: AppMetadata? = null,
    )

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        val user: TokenUser,
    )

    companion object {
        private const val SESSION_KEY = "supabase.session"
        internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
