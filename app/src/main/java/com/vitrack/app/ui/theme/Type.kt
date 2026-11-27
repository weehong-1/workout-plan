package com.vitrack.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Material type roles from the design system. The design uses Roboto Flex;
 * the system Roboto is used until the font is bundled.
 */
private val Sans = FontFamily.Default

internal val VitrackTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

/** Emphasized number styles: metric values are always extra bold. */
object NumberStyles {
    val Hero = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 52.sp)
    val Large = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp)
    val Medium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp)
    val Small = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp)
    val XSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp)
}
