package com.vitrack.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Colour roles from the Android design system (blue seed #415F91).
 * Used when dynamic colour is off; every surface reads these roles, so
 * dynamic colour can replace them without touching the screens.
 */

internal val LightColors = lightColorScheme(
    primary = Color(0xFF415F91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF565F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF705575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD8FD),
    onTertiaryContainer = Color(0xFF28132E),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceDim = Color(0xFFD9D9E0),
    surfaceBright = Color(0xFFF9F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiaryContainer = Color(0xFFFAD8FD),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF37393E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)

/**
 * One colour per metric, shared with iOS and the trainer page.
 * [graphic] is for rings, bars and lines; [text] is for small text and
 * passes contrast on the current surface; [container] backs icon tiles;
 * [track] is the unfilled part of a ring or bar.
 */
@Immutable
data class MetricColor(
    val graphic: Color,
    val text: Color,
    val container: Color,
    val track: Color,
)

@Immutable
data class MetricColors(
    val steps: MetricColor,
    val strain: MetricColor,
    val recovery: MetricColor,
    val sleep: MetricColor,
    val heart: MetricColor,
    val weight: MetricColor,
    val food: MetricColor,
)

private fun metric(graphic: Long, textLight: Long, textDark: Long, dark: Boolean): MetricColor {
    val g = Color(graphic)
    return MetricColor(
        graphic = g,
        text = Color(if (dark) textDark else textLight),
        container = g.copy(alpha = if (dark) 0.2f else 0.14f),
        track = g.copy(alpha = 0.22f),
    )
}

private fun metricColors(dark: Boolean) = MetricColors(
    steps = metric(0xFFFF9500, 0xFFB25E00, 0xFFFF9F0A, dark),
    strain = metric(0xFFFA114F, 0xFFC8003C, 0xFFFF375F, dark),
    recovery = metric(0xFF34C759, 0xFF1E7B34, 0xFF30D158, dark),
    sleep = metric(0xFF8E5CF7, 0xFF6A3BD1, 0xFFA48BFF, dark),
    heart = metric(0xFFFF3B30, 0xFFC9201A, 0xFFFF453A, dark),
    weight = metric(0xFF0A84FF, 0xFF0062CC, 0xFF409CFF, dark),
    food = metric(0xFFFFB000, 0xFF946200, 0xFFFFC23D, dark),
)

internal val LightMetricColors = metricColors(dark = false)
internal val DarkMetricColors = metricColors(dark = true)

/** Sleep stage colours, the same in light and dark. */
object SleepStageColors {
    val Awake = Color(0xFFFF7A59)
    val Light = Color(0xFF8E5CF7)
    val Deep = Color(0xFF4B2FA8)
    val Rem = Color(0xFF5AC8FA)
}
