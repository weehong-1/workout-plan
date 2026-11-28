package com.vitrack.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.data.whoop.WhoopRepository
import com.vitrack.app.data.whoop.WhoopReturn
import com.vitrack.app.data.whoop.WhoopStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WhoopMessage { Connected, Denied, Expired, Failed, Network, Disconnected }

data class WhoopUiState(
    val status: WhoopStatus = WhoopStatus.NotConnected,
    val busy: Boolean = false,
    /** WHOOP's consent page to open in the browser, once. */
    val openUrl: String? = null,
    val message: WhoopMessage? = null,
)

private data class Local(val busy: Boolean = false, val openUrl: String? = null, val message: WhoopMessage? = null)

/** The WHOOP card in Profile: connect, disconnect, and how the last try went. */
@HiltViewModel
class WhoopViewModel @Inject constructor(
    private val whoop: WhoopRepository,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<WhoopUiState> = combine(whoop.status, whoop.lastReturn, local) { status, back, l ->
        WhoopUiState(
            status = status,
            busy = l.busy,
            openUrl = l.openUrl,
            message = l.message ?: back?.let {
                when (it) {
                    WhoopReturn.Connected -> WhoopMessage.Connected
                    WhoopReturn.Denied -> WhoopMessage.Denied
                    WhoopReturn.Expired -> WhoopMessage.Expired
                    WhoopReturn.Failed -> WhoopMessage.Failed
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoopUiState(status = whoop.status.value))

    init {
        viewModelScope.launch { runCatching { whoop.refreshStatus() } }
    }

    fun connect() = run { local.update { it.copy(openUrl = whoop.connectUrl()) } }

    fun disconnect() = run {
        whoop.disconnect()
        local.update { it.copy(message = WhoopMessage.Disconnected) }
    }

    fun urlOpened() = local.update { it.copy(openUrl = null) }

    fun dismissMessage() {
        whoop.clearReturn()
        local.update { it.copy(message = null) }
    }

    private fun run(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val problem = try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendException.Network) {
                WhoopMessage.Network
            } catch (e: Exception) {
                WhoopMessage.Failed
            }
            local.update { it.copy(busy = false, message = problem ?: it.message) }
        }
    }
}
