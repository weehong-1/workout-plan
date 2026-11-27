package com.vitrack.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

internal val VitrackShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Card shapes used by the screens, on top of the Material scale. */
object CardShapes {
    val Hero = RoundedCornerShape(32.dp)
    val Card = RoundedCornerShape(24.dp)
    val Stat = RoundedCornerShape(20.dp)
    val IconTile = RoundedCornerShape(16.dp)
    val Bar = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
}

/** 4 dp grid; 16 dp is the screen margin and card padding. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}
