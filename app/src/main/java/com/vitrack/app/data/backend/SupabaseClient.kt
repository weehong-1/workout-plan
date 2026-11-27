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

    suspend fun sendCode(email: String) {
        send("POST", "/auth/v1/otp", body = json.encodeToString(OtpBody(email.trim(), createUser = true)), authorized = false)
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
        )
        _session.value = session
        store.put(SESSION_KEY, json.encodeToString(session))
        return session
    }

    // Database (PostgREST)

    suspend fun select(table: String, query: List<Pair<String, String>>): String =
        send("GET", "/rest/v1/$table", query = query, authorized = true)

    suspend fun insert(table: String, body: String) {
        send("POST", "/rest/v1/$table", body = body, authorized = true, headers = mapOf("Prefer" to "return=minimal"))
    }

    suspend fun delete(table: String, query: List<Pair<String, String>>) {
        send("DELETE", "/rest/v1/$table", query = query, authorized = true)
    }

    // Storage (private "photos" bucket, one folder per account)

    suspend fun upload(jpeg: ByteArray, path: String) {
        send(
            "POST", "/storage/v1/object/photos/$path", bytes = jpeg, authorized = true,
            headers = mapOf("Content-Type" to "image/jpeg", "x-upsert" to "true"),
        )
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
    private data class OtpBody(val email: String, @SerialName("create_user") val createUser: Boolean)

    @Serializable
    private data class VerifyBody(val type: String = "email", val email: String, val token: String)

    @Serializable
    private data class RefreshBody(@SerialName("refresh_token") val refreshToken: String)

    @Serializable
    private data class RemoveBody(val prefixes: List<String>)

    @Serializable
    private data class TokenUser(val id: String, val email: String? = null)

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
