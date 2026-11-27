package com.vitrack.app.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun EmailSignInRoute(
    onBack: () -> Unit,
    viewModel: EmailSignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from the code step goes to the email step first.
    BackHandler { if (state.step == SignInStep.Code) viewModel.changeEmail() else onBack() }
    EmailSignInScreen(
        state = state,
        onBack = { if (state.step == SignInStep.Code) viewModel.changeEmail() else onBack() },
        onEmail = viewModel::setEmail,
        onSendCode = viewModel::sendCode,
        onCode = viewModel::setCode,
        onVerify = viewModel::verify,
        onChangeEmail = viewModel::changeEmail,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmailSignInScreen(
    state: EmailSignInUiState,
    onBack: () -> Unit,
    onEmail: (String) -> Unit,
    onSendCode: () -> Unit,
    onCode: (String) -> Unit,
    onVerify: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.padding(start = Spacing.xs, top = Spacing.xs).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.login_back))
            }
        }
        Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl)) {
            Text(
                stringResource(R.string.login_email_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
            )
            if (state.step == SignInStep.Email) {
                Text(
                    stringResource(R.string.login_email_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmail,
                    label = { Text(stringResource(R.string.login_email)) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendCode() }),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
                )
                PrimaryButton(stringResource(R.string.login_send_code), enabled = state.canSendCode, busy = state.isBusy, onClick = onSendCode)
            } else {
                Text(
                    stringResource(R.string.login_code_sent, state.email.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
                OutlinedTextField(
                    value = state.code,
                    onValueChange = onCode,
                    label = { Text(stringResource(R.string.login_code)) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(18.dp),
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onVerify() }),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
                )
                PrimaryButton(stringResource(R.string.login_verify), enabled = state.canVerify, busy = state.isBusy, onClick = onVerify)
                TextButton(onClick = onChangeEmail, enabled = !state.isBusy, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.s)) {
                    Text(stringResource(R.string.login_change_email))
                }
            }
            state.error?.let { error ->
                Text(
                    when (error) {
                        EmailSignInError.Network -> stringResource(R.string.login_error_network)
                        is EmailSignInError.Server -> error.message.ifBlank { stringResource(R.string.login_error_network) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.m, start = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
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
