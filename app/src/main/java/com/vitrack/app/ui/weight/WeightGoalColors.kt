package com.vitrack.app.ui.weight

import androidx.compose.runtime.staticCompositionLocalOf
import com.vitrack.app.data.settings.WeightGoal

/**
 * The goal set in Profile, for the Weight tab's colours: green for changes
 * the goal's way (down to lose, up to gain, near the target to maintain).
 */
val LocalWeightGoal = staticCompositionLocalOf { WeightGoal() }
