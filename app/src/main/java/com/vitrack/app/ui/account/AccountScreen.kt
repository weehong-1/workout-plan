package com.vitrack.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitrack.app.R
import com.vitrack.app.ui.theme.Spacing

@Composable
fun AccountRoute(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountScreen(
        state = state,
        onBack = onBack,
        onEmail = viewModel::setEmail,
        onSendCode = viewModel::sendCode,
        onCode = viewModel::setCode,
        onVerify = viewModel::verify,
        onChangeEmail = viewModel::changeEmail,
        onSignOut = viewModel::signOut,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountScreen(
    state: AccountUiState,
    onBack: () -> Unit,
    onEmail: (String) -> Unit,
    onSendCode: () -> Unit,
    onCode: (String) -> Unit,
    onVerify: () -> Unit,
    onChangeEmail: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs).size(48.dp)) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.account_back))
        }
        Column(Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.xxl)) {
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    Box(
                        Modifier.size(72.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.onPrimaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(30.dp))
                    }
                    Column {
                        Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text(
                            stringResource(if (state.isSignedIn) R.string.account_signed_in_subtitle else R.string.account_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }

            when {
                !state.isAvailable -> Note(stringResource(R.string.account_not_set_up))
                state.isSignedIn -> SignedIn(state, onSignOut)
                state.step == SignInStep.Email -> EmailStep(state, onEmail, onSendCode)
                else -> CodeStep(state, onCode, onVerify, onChangeEmail)
            }

            state.error?.let { error ->
                Text(
                    when (error) {
                        AccountError.NotSetUp -> stringResource(R.string.account_not_set_up)
                        AccountError.Network -> stringResource(R.string.account_error_network)
                        is AccountError.Server -> error.message.ifBlank { stringResource(R.string.account_error_network) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.m, start = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun EmailStep(state: AccountUiState, onEmail: (String) -> Unit, onSendCode: () -> Unit) {
    OutlinedTextField(
        value = state.email,
        onValueChange = onEmail,
        label = { Text(stringResource(R.string.account_email)) },
        singleLine = true,
        enabled = !state.isBusy,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSendCode() }),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
    )
    PrimaryButton(stringResource(R.string.account_send_code), enabled = state.canSendCode, busy = state.isBusy, onClick = onSendCode)
    Note(stringResource(R.string.account_optional))
}

@Composable
private fun ColumnScope.CodeStep(state: AccountUiState, onCode: (String) -> Unit, onVerify: () -> Unit, onChangeEmail: () -> Unit) {
    Text(
        stringResource(R.string.account_code_sent, state.email.trim()),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = Spacing.xl, start = Spacing.xs),
    )
    OutlinedTextField(
        value = state.code,
        onValueChange = onCode,
        label = { Text(stringResource(R.string.account_code)) },
        singleLine = true,
        enabled = !state.isBusy,
        shape = RoundedCornerShape(18.dp),
        textStyle = MaterialTheme.typography.titleLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onVerify() }),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
    )
    PrimaryButton(stringResource(R.string.account_verify), enabled = state.canVerify, busy = state.isBusy, onClick = onVerify)
    TextButton(onClick = onChangeEmail, enabled = !state.isBusy, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.s)) {
        Text(stringResource(R.string.account_change_email))
    }
}

@Composable
private fun ColumnScope.SignedIn(state: AccountUiState, onSignOut: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = 14.dp)) {
            Text(stringResource(R.string.account_signed_in_as), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.signedInEmail ?: "–", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
        }
    }
    Note(stringResource(R.string.account_signed_in_note))
    Button(
        onClick = onSignOut,
        enabled = !state.isBusy,
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.l).height(56.dp),
    ) {
        Text(stringResource(R.string.account_sign_out), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrimaryButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(64.dp),
    ) {
        if (busy) {
            LoadingIndicator(modifier = Modifier.size(32.dp))
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.m, start = Spacing.xs, end = Spacing.xs),
    )
}
