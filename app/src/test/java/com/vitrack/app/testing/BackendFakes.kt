package com.vitrack.app.testing

import com.vitrack.app.data.auth.Account
import com.vitrack.app.data.auth.AuthRepository
import com.vitrack.app.data.auth.SignInMethod
import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.data.backend.HttpRequest
import com.vitrack.app.data.backend.HttpResponse
import com.vitrack.app.data.backend.HttpTransport
import com.vitrack.app.data.backend.SecureStore
import com.vitrack.app.data.share.NewShareLink
import com.vitrack.app.data.share.ShareLink
import com.vitrack.app.data.share.ShareRepository
import com.vitrack.app.data.share.SharingNotSetUpException
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemorySecureStore : SecureStore {
    val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun clear() = values.clear()
    override fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/** Answers requests by method and path; records every request it gets. */
class FakeTransport : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    private val routes = mutableListOf<Pair<(HttpRequest) -> Boolean, (HttpRequest) -> HttpResponse>>()
    var offline = false

    fun on(method: String, pathPrefix: String, respond: (HttpRequest) -> HttpResponse) {
        routes.add(0, { r: HttpRequest -> r.method == method && path(r).startsWith(pathPrefix) } to respond)
    }

    fun on(method: String, pathPrefix: String, status: Int, body: String) = on(method, pathPrefix) { HttpResponse(status, body.encodeToByteArray()) }

    fun sent(method: String, pathPrefix: String) = requests.filter { it.method == method && path(it).startsWith(pathPrefix) }

    override suspend fun send(request: HttpRequest): HttpResponse {
        if (offline) throw IOException("offline")
        requests += request
        val route = routes.firstOrNull { it.first(request) } ?: error("No route for ${request.method} ${request.url}")
        return route.second(request)
    }

    companion object {
        fun path(request: HttpRequest): String = request.url.removePrefix(BASE_URL).substringBefore('?')
    }
}

const val BASE_URL = "https://test.supabase.co"

fun tokenJson(access: String = "access-1", refresh: String = "refresh-1", expiresIn: Long = 3600, userId: String = USER_ID) =
    """{"access_token":"$access","refresh_token":"$refresh","expires_in":$expiresIn,"token_type":"bearer","user":{"id":"$userId","email":"vernon@example.com"}}"""

const val USER_ID = "9B2D1C4E-0000-4000-8000-00000000ABCD"

class FakeShareRepository(
    override val isAvailable: Boolean = true,
    signedIn: Boolean = true,
) : ShareRepository {
    val signedInFlow = MutableStateFlow(signedIn)
    val links = MutableStateFlow<List<ShareLink>>(emptyList())
    val created = mutableListOf<NewShareLink>()
    val revoked = mutableListOf<String>()
    /** Thrown by [create] when set. */
    var createError: Exception? = null
    var progressSteps: List<Pair<Int, Int>> = emptyList()

    override val isSignedIn: Flow<Boolean> = signedInFlow

    override fun observeLinks(): Flow<List<ShareLink>> = links

    override suspend fun refresh() = Unit

    override suspend fun create(link: NewShareLink, onProgress: (done: Int, total: Int) -> Unit): ShareLink {
        if (!isAvailable) throw SharingNotSetUpException()
        createError?.let { throw it }
        progressSteps.forEach { (done, total) -> onProgress(done, total) }
        created += link
        return ShareLink("link-1", link.trainerName, link.period, link.expiresOn, false, "https://web.test/share/token")
    }

    override suspend fun revoke(id: String) {
        revoked += id
        links.value = links.value.filterNot { it.id == id }
    }
}

class FakeAuthRepository(override val isAvailable: Boolean = true) : AuthRepository {
    val current = MutableStateFlow<Account?>(null)
    val codesSent = mutableListOf<String>()
    /** The code [verify] accepts. */
    var validCode = "123456"
    var failNext: BackendException? = null

    override val account: Flow<Account?> = current.map { it }

    override suspend fun sendCode(email: String) {
        failNext?.let { failNext = null; throw it }
        codesSent += email
    }

    override suspend fun verify(email: String, code: String) {
        failNext?.let { failNext = null; throw it }
        if (code != validCode) throw BackendException.Server(403, "Token has expired or is invalid")
        current.value = Account("user-1", email)
    }

    override suspend fun signOut() {
        current.value = null
    }

    override var googleWebClientId: String? = "client.apps.googleusercontent.com"
    val googleSignIns = mutableListOf<Pair<String, String>>()

    override suspend fun signInWithGoogle(idToken: String, nonce: String) {
        failNext?.let { failNext = null; throw it }
        googleSignIns += idToken to nonce
        current.value = Account("user-1", "vernon@gmail.com", "Vernon Koh", SignInMethod.Google)
    }

    val links = mutableListOf<String>()
    /** Thrown by [completeSignInLink] when set. */
    var linkError: Exception? = null

    override suspend fun completeSignInLink(link: String) {
        links += link
        linkError?.let { throw it }
        current.value = Account("user-1", "vernon@example.com")
    }

    var deleted = false
    /** Records the order of account and local deletion. */
    val events = mutableListOf<String>()

    override suspend fun deleteAccount() {
        failNext?.let { failNext = null; throw it }
        deleted = true
        events += "server"
        current.value = null
    }
}

class FakeWhoopRepository(status: com.vitrack.app.data.whoop.WhoopStatus = com.vitrack.app.data.whoop.WhoopStatus.NotConnected) :
    com.vitrack.app.data.whoop.WhoopRepository {
    val statusFlow = MutableStateFlow(status)
    val returnFlow = MutableStateFlow<com.vitrack.app.data.whoop.WhoopReturn?>(null)
    var records = com.vitrack.app.data.whoop.WhoopRecords()
    var recordsError: Exception? = null
    val recordRequests = mutableListOf<Pair<java.time.Instant, java.time.Instant>>()
    val returns = mutableListOf<String>()
    var disconnected = false

    override val status: kotlinx.coroutines.flow.StateFlow<com.vitrack.app.data.whoop.WhoopStatus> = statusFlow
    override val lastReturn: kotlinx.coroutines.flow.StateFlow<com.vitrack.app.data.whoop.WhoopReturn?> = returnFlow
    override suspend fun refreshStatus() = Unit
    override suspend fun connectUrl() = "https://api.prod.whoop.com/oauth/oauth2/auth?state=s"
    override suspend fun disconnect() {
        disconnected = true
        statusFlow.value = com.vitrack.app.data.whoop.WhoopStatus.NotConnected
    }
    override suspend fun records(start: java.time.Instant, end: java.time.Instant): com.vitrack.app.data.whoop.WhoopRecords {
        recordRequests += start to end
        recordsError?.let { throw it }
        return records
    }
    override suspend fun completeReturn(link: String) {
        returns += link
        returnFlow.value = com.vitrack.app.data.whoop.whoopReturnOf(link)
    }
    override fun clearReturn() {
        returnFlow.value = null
    }
}
