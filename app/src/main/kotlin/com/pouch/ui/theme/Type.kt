package com.pouch.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Typography scale from the design system.
// TODO(design): add Outfit + DM Serif Display .ttf files to res/font and use them here.
private val Body = FontFamily.Default
private val Display = FontFamily.Serif

val PouchTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontSize = 36.sp),
    displayMedium = TextStyle(fontFamily = Display, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Body, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Body, fontSize = 10.sp),
)
