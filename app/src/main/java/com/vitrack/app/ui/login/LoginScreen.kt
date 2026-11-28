package com.vitrack.app.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitrack.app.R
import com.vitrack.app.ui.app.LinkProblem
import com.vitrack.app.ui.common.rememberReducedMotion
import com.vitrack.app.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Sign in, then (for email) the code screen. The app opens by itself once a session exists. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginFlow(
    openingLink: Boolean = false,
    linkProblem: LinkProblem? = null,
    onDismissLinkProblem: () -> Unit = {},
) {
    var withEmail by rememberSaveable { mutableStateOf(false) }
    // Sign in sits outside the app's Scaffold: the Surface gives text its colour on this background.
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Box {
            if (withEmail) EmailSignInRoute(onBack = { withEmail = false }) else LoginRoute(onEmail = { withEmail = true })
            if (openingLink) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) { ContainedLoadingIndicator() }
            }
        }
    }
    linkProblem?.let { problem ->
        AlertDialog(
            onDismissRequest = onDismissLinkProblem,
            title = { Text(stringResource(R.string.login_link_failed_title)) },
            text = {
                Text(
                    stringResource(
                        when (problem) {
                            LinkProblem.Expired -> R.string.login_link_expired
                            LinkProblem.OtherRequest -> R.string.login_link_other_request
                            LinkProblem.Network -> R.string.login_error_network
                            LinkProblem.Failed -> R.string.login_error_failed
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onDismissLinkProblem(); withEmail = true }) { Text(stringResource(R.string.login_link_new)) }
            },
            dismissButton = { TextButton(onClick = onDismissLinkProblem) { Text(stringResource(R.string.login_link_close)) } },
        )
    }
}

@Composable
fun LoginRoute(
    onEmail: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LoginScreen(
        state = state,
        onGoogle = {
            val request = viewModel.startGoogle() ?: return@LoginScreen
            scope.launch {
                when (val result = requestGoogleIdToken(context, request)) {
                    is GoogleResult.Token -> viewModel.finishGoogle(request, result.idToken)
                    is GoogleResult.Ended -> viewModel.googleEnded(result.outcome)
                }
            }
        },
        onEmail = { viewModel.dismissError(); onEmail() },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(state: LoginUiState, onGoogle: () -> Unit, onEmail: () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // At least a screen tall, so the spacer pushes the buttons and the two
        // notes to the bottom; on a short screen it all scrolls instead.
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
            Illustration(animate = !reducedMotion, modifier = Modifier.padding(top = Spacing.s))
            Rise(0, !reducedMotion) {
                Text(
                    stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).padding(top = Spacing.s).semantics { heading() },
                )
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl)) {
                Rise(1, !reducedMotion) {
                    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onGoogle,
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        ) {
                            if (state.isBusy) {
                                LoadingIndicator(modifier = Modifier.size(32.dp))
                            } else {
                                Icon(painterResource(R.drawable.ic_account), contentDescription = null, modifier = Modifier.size(22.dp))
                                Text(stringResource(R.string.login_google), style = ButtonText, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                        Button(
                            onClick = onEmail,
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_mail), contentDescription = null, modifier = Modifier.size(22.dp))
                            Text(stringResource(R.string.login_with_email), style = ButtonText, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
                state.error?.let { error ->
                    Text(
                        when (error) {
                            LoginError.GoogleNotSetUp -> stringResource(R.string.login_google_not_set_up)
                            LoginError.NoGoogleAccount -> stringResource(R.string.login_no_google_account)
                            LoginError.Network -> stringResource(R.string.login_error_network)
                            is LoginError.Server -> error.message.ifBlank { stringResource(R.string.login_error_failed) }
                            LoginError.Failed -> stringResource(R.string.login_error_failed)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.m, start = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Rise(2, !reducedMotion) {
                    Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_shield),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(R.string.login_private),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Rise(3, !reducedMotion) {
                    Text(
                        stringResource(R.string.login_subtitle),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Lines up with the privacy text: past its 16 dp shield and 10 dp gap.
                        modifier = Modifier.padding(start = 26.dp, top = Spacing.m),
                    )
                }
            }
        }
    }
}

private val ButtonText @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

/** Fades and lifts [content] in, each [index] 100 ms after the one before. */
@Composable
private fun Rise(index: Int, animate: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, tween(600, delayMillis = 100 * index, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 16.dp.toPx() }) { content() }
}

/**
 * The design's picture, drawn on a 412 × 440 grid and scaled to the width:
 * a turning wavy disc with a trend line, and the four things the app tracks
 * floating around it (activity, strain, sleep, weight, food).
 */
@Composable
private fun Illustration(animate: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "login")
    val spin by if (animate) transition.animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "spin") else remember { mutableStateOf(0f) }
    val float by if (animate) transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5_000, easing = LinearEasing), RepeatMode.Restart), label = "float") else remember { mutableStateOf(0f) }
    val container = MaterialTheme.colorScheme.primaryContainer
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = maxWidth / 412.dp
        Box(Modifier.fillMaxWidth().height(440.dp * scale)) {
            Canvas(Modifier.fillMaxSize()) {
                val unit = size.width / 412f
                rotate(spin, pivot = Offset(206f * unit, 210f * unit)) {
                    drawPath(wavyShape(206f, 210f, 118f, 9f, 12, unit), container)
                }
                val wave = Path().apply {
                    var x = 0f
                    while (x <= 112f) {
                        val y = 180f + x * 0.3f + 10f * sin(x / 9f)
                        if (x == 0f) moveTo((150f + x) * unit, y * unit) else lineTo((150f + x) * unit, y * unit)
                        x += 4f
                    }
                }
                drawPath(wave, onContainer, style = Stroke(width = 10f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(Color(0xFF34C759), radius = 13f * unit, center = Offset(262f * unit, 212f * unit))
            }
            Badge(76f, 96f, 76f, 76f, CircleShape, Color(0xFFFF9500), R.drawable.ic_nav_activity, Color.White, 34.dp, scale, float, 0f, 0f)
            Badge(340f, 94f, 96f, 48f, RoundedCornerShape(50), Color(0xFFFA114F), R.drawable.ic_pulse, Color.White, 26.dp, scale, float, 0.24f, -14f)
            FlowerBadge(352f, 330f, scale, float, 0.48f)
            Badge(80f, 340f, 80f, 80f, RoundedCornerShape(26.dp * scale), Color(0xFF0A84FF), R.drawable.ic_nav_weight, Color.White, 36.dp, scale, float, 0.72f, -12f)
            Badge(208f, 400f, 44f, 44f, CircleShape, Color(0xFFFFB000), R.drawable.ic_nav_food, Color(0xFF3D2800), 24.dp, scale, float, 0.12f, 0f)
        }
    }
}

/** A badge centred at ([cx], [cy]) on the 412-wide grid, bobbing up and tilting with [float]. */
@Composable
private fun Badge(
    cx: Float, cy: Float, w: Float, h: Float, shape: Shape, color: Color, icon: Int, tint: Color, iconSize: Dp,
    scale: Float, float: Float, phase: Float, tilt: Float,
) {
    val t = ((float + phase) % 1f) * 2f * PI.toFloat()
    val bob = (1f - cos(t)) / 2f
    Box(
        Modifier
            .offset((cx - w / 2).dp * scale, (cy - h / 2).dp * scale)
            .size(w.dp * scale, h.dp * scale)
            .graphicsLayer {
                translationY = -10.dp.toPx() * scale * bob
                rotationZ = tilt + 6f * bob
            }
            .clip(shape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(iconSize * scale))
    }
}

@Composable
private fun FlowerBadge(cx: Float, cy: Float, scale: Float, float: Float, phase: Float) {
    val t = ((float + phase) % 1f) * 2f * PI.toFloat()
    val bob = (1f - cos(t)) / 2f
    val size = 96f
    Box(
        Modifier
            .offset((cx - size / 2).dp * scale, (cy - size / 2).dp * scale)
            .size(size.dp * scale)
            .graphicsLayer {
                translationY = -10.dp.toPx() * scale * bob
                rotationZ = 6f * bob
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = this.size.width / size
            drawPath(wavyShape(size / 2, size / 2, 40f, 7f, 6, unit), Color(0xFF8E5CF7))
        }
        Icon(painterResource(R.drawable.ic_sleep), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp * scale))
    }
}

/** A disc whose edge waves [lobes] times, in grid units scaled by [unit]. */
private fun wavyShape(cx: Float, cy: Float, r: Float, amp: Float, lobes: Int, unit: Float): Path = Path().apply {
    var i = 0
    while (i <= 360) {
        val a = i * PI.toFloat() / 180f
        val rr = r + amp * cos(a * lobes)
        val x = (cx + rr * cos(a)) * unit
        val y = (cy + rr * sin(a)) * unit
        if (i == 0) moveTo(x, y) else lineTo(x, y)
        i += 2
    }
    close()
}
