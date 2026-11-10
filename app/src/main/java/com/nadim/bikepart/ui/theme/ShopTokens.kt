package com.nadim.bikepart.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Applied styles in Figma 12:138, rather than the occasionally inconsistent specimen captions.
private fun type(size: Int, height: Int, spacing: Float = 0f, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = height.sp,
    letterSpacing = spacing.sp, fontWeight = weight,
)

val Typography = Typography(
    displayLarge = type(57, 64, -.25f), displayMedium = type(45, 52), displaySmall = type(36, 44),
    headlineLarge = type(32, 40), headlineMedium = type(28, 36), headlineSmall = type(24, 32),
    titleLarge = type(22, 28, weight = FontWeight.Bold),
    titleMedium = type(16, 24, .15f, FontWeight.Bold),
    titleSmall = type(14, 20, .1f, FontWeight.Medium),
    bodyLarge = type(16, 24, .5f), bodyMedium = type(14, 20, .25f), bodySmall = type(12, 16, .4f),
    labelLarge = type(14, 20, .1f, FontWeight.Bold),
    labelMedium = type(12, 16, .5f, FontWeight.Bold),
    labelSmall = type(11, 16, .5f, FontWeight.Bold),
)

val ShopShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object ShopSpacing {
    val small = 8.dp
    val medium = 12.dp
    val content = 16.dp
    val section = 24.dp
    val touchTarget = 48.dp
    val formWidth = 640.dp
}
