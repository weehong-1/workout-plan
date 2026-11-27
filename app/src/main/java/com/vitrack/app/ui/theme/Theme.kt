package com.vitrack.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LocalMetricColors = staticCompositionLocalOf { LightMetricColors }

/**
 * Material 3 Expressive theme. [dynamicColor] switches the colour roles to the
 * wallpaper on Android 12+; metric colours never change, so a metric looks the
 * same on every platform.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VitrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalMetricColors provides if (darkTheme) DarkMetricColors else LightMetricColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = VitrackTypography,
            shapes = VitrackShapes,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

object VitrackTheme {
    val metrics: MetricColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMetricColors.current
}
