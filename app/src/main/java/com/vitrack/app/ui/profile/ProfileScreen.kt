package com.vitrack.app.ui.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitrack.app.R
import com.vitrack.app.data.auth.SignInMethod
import com.vitrack.app.data.export.DataExporter
import com.vitrack.app.data.reminders.ReminderNotifications
import com.vitrack.app.data.settings.GoalKind
import com.vitrack.app.data.settings.HEIGHT_CM_RANGE
import com.vitrack.app.data.settings.ReminderKind
import com.vitrack.app.data.settings.STEP_GOAL_RANGE
import com.vitrack.app.data.settings.ThemeMode
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.whoop.WhoopStatus
import com.vitrack.app.ui.common.ConnectedChoiceRow
import com.vitrack.app.ui.common.Format
import com.vitrack.app.ui.common.rememberReducedMotion
import com.vitrack.app.ui.theme.MetricColor
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onOpenTrainerLinks: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    whoopViewModel: WhoopViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val whoop by whoopViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(whoop.openUrl) {
        val url = whoop.openUrl ?: return@LaunchedEffect
        runCatching { uriHandler.openUri(url) }
        whoopViewModel.urlOpened()
    }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val stream = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
        if (stream == null) viewModel.exportFailed() else viewModel.export(stream)
    }
    // A reminder switched on asks for notifications first (Android 13+).
    var pendingReminder by remember { mutableStateOf<ReminderKind?>(null) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingReminder?.let { viewModel.setReminder(it, on = true, allowed = granted) }
        pendingReminder = null
    }

    ProfileScreen(
        state = state,
        whoop = whoop,
        onBack = onBack,
        actions = ProfileActions(
            setGoalKind = viewModel::setGoalKind,
            setGoalWeight = viewModel::setGoalWeight,
            setStepGoal = viewModel::setStepGoal,
            setHeightCm = viewModel::setHeightCm,
            setUnit = viewModel::setUnit,
            setTheme = viewModel::setTheme,
            setName = viewModel::setDisplayName,
            setReminder = { kind, on ->
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ReminderNotifications.canPost(context)) {
                    pendingReminder = kind
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setReminder(kind, on)
                }
            },
            export = { exportFile.launch(DataExporter.fileName(LocalDate.now())) },
            openTrainerLinks = onOpenTrainerLinks,
            signOut = viewModel::signOut,
            deleteAccount = viewModel::deleteAccount,
            dismissMessage = viewModel::dismissMessage,
            connectWhoop = whoopViewModel::connect,
            disconnectWhoop = whoopViewModel::disconnect,
            dismissWhoopMessage = whoopViewModel::dismissMessage,
        ),
    )
}

class ProfileActions(
    val setGoalKind: (GoalKind) -> Unit,
    val setGoalWeight: (Double, WeightUnit) -> Unit,
    val setStepGoal: (Int) -> Unit,
    val setHeightCm: (Int?) -> Unit,
    val setUnit: (WeightUnit) -> Unit,
    val setTheme: (ThemeMode) -> Unit,
    val setName: (String) -> Unit,
    val setReminder: (ReminderKind, Boolean) -> Unit,
    val export: () -> Unit,
    val openTrainerLinks: () -> Unit,
    val signOut: () -> Unit,
    val deleteAccount: (String) -> Unit,
    val dismissMessage: () -> Unit,
    val connectWhoop: () -> Unit = {},
    val disconnectWhoop: () -> Unit = {},
    val dismissWhoopMessage: () -> Unit = {},
)

private enum class EditDialog { Name, GoalWeight, StepGoal, Height, Delete, DisconnectWhoop }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(state: ProfileUiState, onBack: () -> Unit, actions: ProfileActions, whoop: WhoopUiState = WhoopUiState()) {
    var dialog by rememberSaveable { mutableStateOf<EditDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { messageText(it, state.unit) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            actions.dismissMessage()
        }
    }
    val whoopText = whoop.message?.let { whoopMessageText(it) }
    LaunchedEffect(whoop.message) {
        if (whoopText != null) {
            actions.dismissWhoopMessage()
            snackbar.showSnackbar(whoopText)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (state.isLoading) {
            ContainedLoadingIndicator(Modifier.align(Alignment.Center))
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
                Row(Modifier.padding(start = Spacing.xs).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.profile_back))
                    }
                    Text(
                        stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(start = Spacing.xs).semantics { heading() },
                    )
                }
                Column(Modifier.padding(horizontal = Spacing.l).padding(bottom = 32.dp)) {
                    HeaderCard(state, whoopConnected = whoop.status == WhoopStatus.Connected, onEditName = { dialog = EditDialog.Name })
                    SectionTitle(R.string.profile_goal)
                    GoalCard(state, actions, onChangeGoal = { dialog = EditDialog.GoalWeight }, onEditSteps = { dialog = EditDialog.StepGoal }, onEditHeight = { dialog = EditDialog.Height })
                    SectionTitle(R.string.profile_display)
                    DisplayCard(state, actions)
                    SectionTitle(R.string.profile_connected)
                    WhoopCard(whoop, onConnect = actions.connectWhoop, onDisconnect = { dialog = EditDialog.DisconnectWhoop })
                    SectionTitle(R.string.profile_sharing)
                    SharingRow(state, actions.openTrainerLinks)
                    SectionTitle(R.string.profile_reminders)
                    RemindersList(state, actions.setReminder)
                    SectionTitle(R.string.profile_your_data)
                    DataCard(state, actions.export, actions.signOut)
                    DeleteCard(state, onDelete = { dialog = EditDialog.Delete })
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Spacing.l))
    }

    when (dialog) {
        EditDialog.Name -> TextDialog(
            title = stringResource(R.string.profile_edit_name),
            label = stringResource(R.string.profile_name),
            initial = state.name.orEmpty(),
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            valid = { it.isNotBlank() },
            onSave = { actions.setName(it); dialog = null },
            onDismiss = { dialog = null },
        )
        EditDialog.GoalWeight -> TextDialog(
            title = stringResource(R.string.profile_goal_weight),
            label = Format.unitLabel(state.unit),
            initial = state.goal.targetKg?.let { Format.weight(it, state.unit) }.orEmpty(),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supporting = state.weeklyAverageKg?.let {
                stringResource(R.string.profile_goal_weight_hint, Format.weight(it, state.unit), Format.unitLabel(state.unit))
            },
            valid = { parseNumber(it) != null },
            onSave = { text -> parseNumber(text)?.let { actions.setGoalWeight(it, state.unit) }; dialog = null },
            onDismiss = { dialog = null },
        )
        EditDialog.StepGoal -> TextDialog(
            title = stringResource(R.string.profile_step_goal),
            label = stringResource(R.string.profile_steps),
            initial = state.stepGoal.toString(),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
            supporting = stringResource(R.string.profile_step_goal_hint, Format.grouped(STEP_GOAL_RANGE.first), Format.grouped(STEP_GOAL_RANGE.last)),
            valid = { it.toIntOrNull() in STEP_GOAL_RANGE },
            onSave = { actions.setStepGoal(it.toInt()); dialog = null },
            onDismiss = { dialog = null },
        )
        EditDialog.Height -> HeightDialog(state.heightCm, state.unit, onSave = { actions.setHeightCm(it); dialog = null }, onDismiss = { dialog = null })
        EditDialog.Delete -> DeleteDialog(onDelete = { actions.deleteAccount(it); dialog = null }, onDismiss = { dialog = null })
        EditDialog.DisconnectWhoop -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.profile_whoop_disconnect_title)) },
            text = { Text(stringResource(R.string.profile_whoop_disconnect_body)) },
            confirmButton = {
                TextButton(onClick = { actions.disconnectWhoop(); dialog = null }) { Text(stringResource(R.string.profile_whoop_disconnect)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.profile_whoop_cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun whoopMessageText(message: WhoopMessage): String = stringResource(
    when (message) {
        WhoopMessage.Connected -> R.string.profile_whoop_msg_connected
        WhoopMessage.Denied -> R.string.profile_whoop_msg_denied
        WhoopMessage.Expired -> R.string.profile_whoop_msg_expired
        WhoopMessage.Failed -> R.string.profile_whoop_msg_failed
        WhoopMessage.Network -> R.string.profile_whoop_msg_network
        WhoopMessage.Disconnected -> R.string.profile_whoop_msg_disconnected
    },
)

@Composable
private fun messageText(message: ProfileMessage, unit: WeightUnit): String = when (message) {
    is ProfileMessage.Exported -> stringResource(R.string.profile_exported, message.weighIns, message.foodPhotos)
    ProfileMessage.ExportFailed -> stringResource(R.string.profile_export_failed)
    ProfileMessage.GoalWrongWay -> stringResource(R.string.profile_goal_wrong_way)
    ProfileMessage.NotificationsBlocked -> stringResource(R.string.profile_notifications_blocked)
    ProfileMessage.Network -> stringResource(R.string.profile_error_network)
    is ProfileMessage.Server -> message.message.ifBlank { stringResource(R.string.profile_error_network) }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(start = Spacing.xs, top = 28.dp, bottom = 10.dp).semantics { heading() },
    )
}

@Composable
private fun HeaderCard(state: ProfileUiState, whoopConnected: Boolean, onEditName: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(36.dp), color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                CookieAvatar(state.name)
                Column(Modifier.weight(1f)) {
                    Text(
                        state.name ?: stringResource(R.string.profile_no_name),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        when (state.method) {
                            SignInMethod.Google -> stringResource(R.string.profile_signed_in_google)
                            SignInMethod.Email -> state.email?.let { stringResource(R.string.profile_signed_in_email, it) }
                                ?: stringResource(R.string.profile_signed_in)
                            null -> stringResource(R.string.profile_not_signed_in)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(
                    onClick = onEditName,
                    modifier = Modifier.size(48.dp).background(scheme.onPrimaryContainer.copy(alpha = 0.14f), CircleShape),
                ) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.profile_edit_name), modifier = Modifier.size(20.dp))
                }
            }
            Row(Modifier.padding(top = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Chip(stringResource(if (whoopConnected) R.string.profile_whoop_connected_chip else R.string.profile_whoop_sample_chip))
                Chip(pluralStringResource(R.plurals.profile_links_chip, state.activeLinks, state.activeLinks))
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(horizontal = Spacing.m, vertical = 6.dp),
    )
}

/** The initial on a slowly turning cookie, as drawn. */
@Composable
private fun CookieAvatar(name: String?) {
    val reducedMotion = rememberReducedMotion()
    val spin by if (reducedMotion) remember { mutableStateOf(0f) } else rememberInfiniteTransition(label = "avatar")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "spin")
    val fill = MaterialTheme.colorScheme.primary
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = size.width / 2
            val path = Path().apply {
                var i = 0
                while (i <= 360) {
                    val a = i * PI.toFloat() / 180f
                    val r = c * (38f + 3.5f * cos(a * 8)) / 42f
                    if (i == 0) moveTo(c + r * cos(a), c + r * sin(a)) else lineTo(c + r * cos(a), c + r * sin(a))
                    i += 3
                }
                close()
            }
            rotate(spin, Offset(c, c)) { drawPath(path, fill) }
        }
        Text(
            name?.take(1)?.uppercase() ?: "?",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalCard(state: ProfileUiState, actions: ProfileActions, onChangeGoal: () -> Unit, onEditSteps: () -> Unit, onEditHeight: () -> Unit) {
    val weight = VitrackTheme.metrics.weight
    val unit = Format.unitLabel(state.unit)
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l)) {
            ConnectedChoiceRow(
                options = GoalKind.entries.toList(),
                selected = state.goal.kind,
                onSelect = actions.setGoalKind,
                label = {
                    stringResource(
                        when (it) {
                            GoalKind.Lose -> R.string.profile_goal_lose
                            GoalKind.Maintain -> R.string.profile_goal_maintain
                            GoalKind.Gain -> R.string.profile_goal_gain
                        },
                    )
                },
            )
            Row(Modifier.padding(start = Spacing.xs, top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Box(Modifier.size(10.dp).background(VitrackTheme.metrics.trend.goodDot, CircleShape))
                Text(
                    stringResource(
                        when (state.goal.kind) {
                            GoalKind.Lose -> R.string.profile_goal_note_lose
                            GoalKind.Maintain -> R.string.profile_goal_note_maintain
                            GoalKind.Gain -> R.string.profile_goal_note_gain
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(shape = RoundedCornerShape(24.dp), color = weight.hero, modifier = Modifier.fillMaxWidth().padding(top = Spacing.l)) {
                Column(Modifier.padding(Spacing.l)) {
                    val progress = state.progress
                    when {
                        state.goal.kind == GoalKind.Maintain -> Text(
                            state.goal.targetKg?.let { stringResource(R.string.profile_maintain_target, Format.weight(it, state.unit), unit) }
                                ?: stringResource(R.string.profile_maintain_no_weighins),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        progress != null -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                Text(stringResource(R.string.profile_goal_weight), style = MaterialTheme.typography.titleSmall, color = weight.text, modifier = Modifier.weight(1f))
                                Text(Format.weight(progress.targetKg, state.unit), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
                                Text(" $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearWavyProgressIndicator(
                                progress = { progress.fraction },
                                color = weight.graphic,
                                trackColor = weight.track,
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).height(14.dp),
                            )
                            Row(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
                                Text(
                                    progress.startedOn?.let {
                                        stringResource(R.string.profile_goal_started, Format.weight(progress.startKg, state.unit), unit, monthYear(it))
                                    } ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (progress.reached) stringResource(R.string.profile_goal_reached)
                                    else stringResource(R.string.profile_goal_to_go, Format.weight(progress.toGoKg, state.unit), unit),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                )
                            }
                            GoalButton(stringResource(R.string.profile_change_goal), onChangeGoal)
                        }
                        else -> {
                            Text(
                                state.weeklyAverageKg?.let {
                                    stringResource(
                                        if (state.goal.kind == GoalKind.Lose) R.string.profile_set_goal_below else R.string.profile_set_goal_above,
                                        Format.weight(it, state.unit), unit,
                                    )
                                } ?: stringResource(R.string.profile_set_goal_no_weighins),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            GoalButton(stringResource(R.string.profile_set_goal), onChangeGoal)
                        }
                    }
                }
            }

            ValueRow(stringResource(R.string.profile_step_goal), Format.grouped(state.stepGoal), onEditSteps)
            ValueRow(
                stringResource(R.string.profile_height),
                state.heightCm?.let { heightLabel(it, state.unit) } ?: stringResource(R.string.profile_add),
                onEditHeight,
            )
        }
    }
}

@Composable
private fun GoalButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).height(40.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    HorizontalDivider(Modifier.padding(top = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .padding(horizontal = 14.dp, vertical = Spacing.s),
        )
    }
}

@Composable
private fun DisplayCard(state: ProfileUiState, actions: ProfileActions) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = 6.dp)) {
            SettingRow(stringResource(R.string.profile_units)) {
                ConnectedChoiceRow(
                    options = WeightUnit.entries.toList(),
                    selected = state.unit,
                    onSelect = actions.setUnit,
                    label = { Format.unitLabel(it) },
                    modifier = Modifier.width(140.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow(stringResource(R.string.profile_theme)) {
                ConnectedChoiceRow(
                    options = ThemeMode.entries.toList(),
                    selected = state.theme,
                    onSelect = actions.setTheme,
                    label = {
                        stringResource(
                            when (it) {
                                ThemeMode.System -> R.string.profile_theme_system
                                ThemeMode.Light -> R.string.profile_theme_light
                                ThemeMode.Dark -> R.string.profile_theme_dark
                            },
                        )
                    },
                    modifier = Modifier.width(276.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        control()
    }
}

/** WHOOP's connection. Until it's connected, Activity shows sample data. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhoopCard(whoop: WhoopUiState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val strain = VitrackTheme.metrics.strain
    Surface(shape = RoundedCornerShape(28.dp), color = strain.hero) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Box(Modifier.size(48.dp).background(strain.strong, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_pulse), contentDescription = null, tint = strain.onStrong, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_whoop), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            when (whoop.status) {
                                WhoopStatus.Connected -> R.string.profile_whoop_status_connected
                                WhoopStatus.NotConnected -> R.string.profile_whoop_status
                                WhoopStatus.NotSetUp -> R.string.profile_whoop_status_not_set_up
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    whoop.busy -> LoadingIndicator(Modifier.size(40.dp))
                    whoop.status == WhoopStatus.Connected ->
                        OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.profile_whoop_disconnect)) }
                    else ->
                        Button(onClick = onConnect, enabled = whoop.status == WhoopStatus.NotConnected) { Text(stringResource(R.string.profile_whoop_connect)) }
                }
            }
            Text(
                stringResource(R.string.profile_whoop_data),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
    }
}

@Composable
private fun SharingRow(state: ProfileUiState, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(Modifier.size(40.dp).background(scheme.onTertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_link), contentDescription = null, tint = scheme.tertiaryContainer, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_trainer_links), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        state.activeLinks == 0 -> stringResource(R.string.profile_links_none)
                        state.nextLinkExpiry != null -> pluralStringResource(
                            R.plurals.profile_links_active_expires, state.activeLinks, state.activeLinks, fullDate(state.nextLinkExpiry),
                        )
                        else -> pluralStringResource(R.plurals.profile_links_active, state.activeLinks, state.activeLinks)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RemindersList(state: ProfileUiState, onToggle: (ReminderKind, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ReminderKind.entries.forEach { kind ->
            val metric: MetricColor = if (kind == ReminderKind.WeighIn) VitrackTheme.metrics.weight else VitrackTheme.metrics.food
            val on = state.reminders.isOn(kind)
            val name = stringResource(if (kind == ReminderKind.WeighIn) R.string.profile_remind_weigh else R.string.profile_remind_food)
            Surface(shape = RoundedCornerShape(24.dp), color = if (on) metric.hero else MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = Spacing.m, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Box(Modifier.size(40.dp).background(metric.badge, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (kind == ReminderKind.WeighIn) R.drawable.ic_nav_weight else R.drawable.ic_nav_food),
                            contentDescription = null,
                            tint = metric.text,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(if (kind == ReminderKind.WeighIn) R.string.profile_remind_weigh_when else R.string.profile_remind_food_when),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = on,
                        onCheckedChange = { onToggle(kind, it) },
                        thumbContent = if (on) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize), tint = metric.strong) }
                        } else {
                            null
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = metric.strong, checkedThumbColor = Color.White),
                        modifier = Modifier.semantics { this.contentDescription = name },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DataCard(state: ProfileUiState, onExport: () -> Unit, onSignOut: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clickable(enabled = state.busy == null, role = Role.Button, onClick = onExport)
                    .padding(horizontal = Spacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (state.busy == ProfileBusy.Exporting) {
                    LoadingIndicator(Modifier.size(22.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_export), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.profile_export_note),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.serverAvailable) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(enabled = state.busy == null, role = Role.Button, onClick = onSignOut)
                        .padding(horizontal = Spacing.l),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Icon(painterResource(R.drawable.ic_logout), contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.profile_sign_out), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteCard(state: ProfileUiState, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
    ) {
        Column(Modifier.padding(Spacing.l)) {
            Text(stringResource(R.string.profile_delete), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (state.serverAvailable) R.string.profile_delete_note else R.string.profile_delete_note_local),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Button(
                onClick = onDelete,
                enabled = state.busy == null,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).height(48.dp),
            ) {
                if (state.busy == ProfileBusy.Deleting) {
                    LoadingIndicator(Modifier.size(28.dp))
                } else {
                    Text(stringResource(R.string.profile_delete), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun TextDialog(
    title: String,
    label: String,
    initial: String,
    keyboard: KeyboardOptions,
    valid: (String) -> Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    supporting: String? = null,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = keyboard,
                supportingText = supporting?.let { { Text(it) } },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = valid(text.trim())) { Text(stringResource(R.string.profile_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_cancel)) } },
    )
}

/** Height in cm, or feet and inches when weights show in lb. Saving empty removes it. */
@Composable
private fun HeightDialog(heightCm: Int?, unit: WeightUnit, onSave: (Int?) -> Unit, onDismiss: () -> Unit) {
    val imperial = unit == WeightUnit.Lb
    val totalInches = heightCm?.let { (it / 2.54).roundToInt() }
    var cm by rememberSaveable { mutableStateOf(heightCm?.toString().orEmpty()) }
    var feet by rememberSaveable { mutableStateOf(totalInches?.let { (it / 12).toString() }.orEmpty()) }
    var inches by rememberSaveable { mutableStateOf(totalInches?.let { (it % 12).toString() }.orEmpty()) }
    val result: Int? = if (imperial) {
        val f = feet.toIntOrNull()
        val i = inches.toIntOrNull() ?: 0
        f?.let { ((it * 12 + i) * 2.54).roundToInt() }
    } else {
        cm.toIntOrNull()
    }
    val empty = if (imperial) feet.isBlank() && inches.isBlank() else cm.isBlank()
    val valid = empty || result in HEIGHT_CM_RANGE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_height)) },
        text = {
            if (imperial) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    OutlinedTextField(feet, { feet = it.filter(Char::isDigit).take(1) }, label = { Text(stringResource(R.string.profile_feet)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(inches, { inches = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.profile_inches)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            } else {
                OutlinedTextField(cm, { cm = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.profile_cm)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (empty) null else result) }, enabled = valid) { Text(stringResource(R.string.profile_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_cancel)) } },
    )
}

/** Account deletion can't be undone, so the word DELETE is typed to confirm, as on the web. */
@Composable
private fun DeleteDialog(onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.profile_delete_body, ProfileViewModel.DELETE_WORD))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(12) },
                    label = { Text(stringResource(R.string.profile_delete_type, ProfileViewModel.DELETE_WORD)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDelete(typed) },
                enabled = typed.trim() == ProfileViewModel.DELETE_WORD,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.profile_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_cancel)) } },
    )
}

private fun parseNumber(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

@Composable
private fun heightLabel(cm: Int, unit: WeightUnit): String = if (unit == WeightUnit.Lb) {
    val inches = (cm / 2.54).roundToInt()
    stringResource(R.string.profile_height_ft_in, inches / 12, inches % 12)
} else {
    stringResource(R.string.profile_height_cm, cm)
}

private fun monthYear(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))

private fun fullDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
