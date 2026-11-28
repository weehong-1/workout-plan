package com.vitrack.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitrack.app.R
import com.vitrack.app.data.whoop.WhoopStatus
import com.vitrack.app.ui.profile.WhoopMessage
import com.vitrack.app.ui.profile.WhoopViewModel
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme

/**
 * Shown where activity would be while WHOOP isn't connected: what it brings
 * and a button that opens WHOOP's consent page, the same as Profile's.
 */
@Composable
fun ConnectWhoopPrompt(modifier: Modifier = Modifier, viewModel: WhoopViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(state.openUrl) {
        val url = state.openUrl ?: return@LaunchedEffect
        runCatching { uriHandler.openUri(url) }
        viewModel.urlOpened()
    }
    ConnectWhoopContent(
        busy = state.busy,
        canConnect = state.status == WhoopStatus.NotConnected,
        problem = state.message?.takeIf { it != WhoopMessage.Connected && it != WhoopMessage.Disconnected },
        onConnect = viewModel::connect,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectWhoopContent(
    busy: Boolean,
    canConnect: Boolean,
    problem: WhoopMessage?,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strain = VitrackTheme.metrics.strain
    Box(modifier.fillMaxSize().padding(horizontal = Spacing.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(Modifier.size(72.dp).background(strain.strong, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_pulse), contentDescription = null, tint = strain.onStrong, modifier = Modifier.size(36.dp))
            }
            Text(
                stringResource(R.string.whoop_prompt_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.whoop_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (busy) {
                LoadingIndicator(Modifier.padding(top = Spacing.s).size(48.dp))
            } else {
                Button(
                    onClick = onConnect,
                    enabled = canConnect,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.padding(top = Spacing.s).fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.whoop_prompt_connect)) }
            }
            problem?.let {
                Text(
                    stringResource(
                        when (it) {
                            WhoopMessage.Denied -> R.string.profile_whoop_msg_denied
                            WhoopMessage.Expired -> R.string.profile_whoop_msg_expired
                            WhoopMessage.Network -> R.string.profile_whoop_msg_network
                            else -> R.string.profile_whoop_msg_failed
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
