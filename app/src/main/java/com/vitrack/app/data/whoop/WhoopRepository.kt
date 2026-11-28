package com.vitrack.app.data.whoop

import com.vitrack.app.data.backend.Backend
import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.data.backend.SecureStore
import com.vitrack.app.data.backend.SupabaseClient
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** Where WHOOP's consent page sends the person back to (through the `whoop-callback` function). */
const val WHOOP_RETURN = "vitrack://whoop-callback"

enum class WhoopStatus {
    /** This build has no server, so WHOOP can't be connected. */
    NotSetUp,
    NotConnected,
    Connected,
}

/** How the trip to WHOOP's consent page ended. */
enum class WhoopReturn { Connected, Denied, Expired, Failed }

/**
 * The account's WHOOP connection. The tokens live on the server (the
 * `whoop-callback` Edge Function); the app only asks for records.
 */
interface WhoopRepository {
    val status: StateFlow<WhoopStatus>

    /** The latest return from WHOOP's consent page, until [clearReturn]. */
    val lastReturn: StateFlow<WhoopReturn?>

    /** Asks the server whether this account is connected. */
    suspend fun refreshStatus()

    /** WHOOP's consent page for this account, to open in the browser. */
    suspend fun connectUrl(): String

    suspend fun disconnect()

    /** WHOOP's records between [start] and [end]. Throws [WhoopNotConnectedException] when not connected. */
    suspend fun records(start: Instant, end: Instant): WhoopRecords

    /** Called with the `vitrack://whoop-callback?result=…` link; refreshes the status. */
    suspend fun completeReturn(link: String)

    fun clearReturn()
}

class WhoopNotConnectedException : Exception("WHOOP isn't connected")

/** The `result` of a return link, or null for a link that isn't WHOOP's. */
fun whoopReturnOf(link: String): WhoopReturn? {
    if (!link.startsWith(WHOOP_RETURN)) return null
    val result = link.substringAfter('?', "").split('&')
        .firstOrNull { it.startsWith("result=") }?.substringAfter('=')
    return when (result) {
        "connected" -> WhoopReturn.Connected
        "denied" -> WhoopReturn.Denied
        "expired" -> WhoopReturn.Expired
        else -> WhoopReturn.Failed
    }
}

@Singleton
class SupabaseWhoopRepository @Inject constructor(
    private val backend: Backend,
    private val store: SecureStore,
) : WhoopRepository {

    private val _status = MutableStateFlow(
        when {
            backend.client == null -> WhoopStatus.NotSetUp
            // The last known answer, so the Activity tab doesn't wait on the network.
            store.get(CONNECTED_KEY) == "1" -> WhoopStatus.Connected
            else -> WhoopStatus.NotConnected
        },
    )
    override val status: StateFlow<WhoopStatus> = _status.asStateFlow()

    private val _lastReturn = MutableStateFlow<WhoopReturn?>(null)
    override val lastReturn: StateFlow<WhoopReturn?> = _lastReturn.asStateFlow()

    private fun client(): SupabaseClient = backend.client ?: throw BackendException.NotConfigured()

    private fun setConnected(connected: Boolean) {
        if (backend.client == null) return
        store.put(CONNECTED_KEY, if (connected) "1" else null)
        _status.value = if (connected) WhoopStatus.Connected else WhoopStatus.NotConnected
    }

    override suspend fun refreshStatus() {
        if (backend.client == null) return
        val reply = try {
            call<StatusReply>(Action("status"))
        } catch (e: BackendException.NotSignedIn) {
            StatusReply(connected = false)
        }
        setConnected(reply.connected)
    }

    override suspend fun connectUrl(): String = call<ConnectReply>(Action("connect")).url

    override suspend fun disconnect() {
        call<StatusReply>(Action("disconnect"))
        setConnected(false)
    }

    override suspend fun records(start: Instant, end: Instant): WhoopRecords = try {
        call<WhoopRecords>(Action("data", start.toString(), end.toString()))
    } catch (e: BackendException.Server) {
        if (e.status != 409) throw e
        setConnected(false)
        throw WhoopNotConnectedException()
    }

    override suspend fun completeReturn(link: String) {
        val result = whoopReturnOf(link) ?: return
        _lastReturn.value = result
        val checked = runCatching { refreshStatus() }.isSuccess
        // Offline right now: trust WHOOP's answer until the next check.
        if (!checked && result == WhoopReturn.Connected) setConnected(true)
    }

    override fun clearReturn() {
        _lastReturn.value = null
    }

    private suspend inline fun <reified T> call(action: Action): T {
        val text = client().function(FUNCTION, SupabaseClient.json.encodeToString(action))
        return try {
            SupabaseClient.json.decodeFromString<T>(text)
        } catch (e: Exception) {
            throw BackendException.InvalidResponse(e)
        }
    }

    @Serializable
    private data class Action(val action: String, val start: String? = null, val end: String? = null)

    @Serializable
    private data class StatusReply(val connected: Boolean)

    @Serializable
    private data class ConnectReply(val url: String)

    private companion object {
        const val FUNCTION = "whoop-callback"
        const val CONNECTED_KEY = "whoop.connected"
    }
}
