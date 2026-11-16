package com.nadim.bikepart.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.theme.token.FluentAliasTokens.TypographyTokens.*
import com.microsoft.fluentui.theme.token.FluentGlobalTokens.CornerRadiusTokens.*

// Fluent roles remain the source when a later brand supplies a different type ramp.
internal fun fluentTypography(tokens: com.microsoft.fluentui.theme.token.IAliasTokens): Typography {
    val type = tokens.typography
    return Typography(
        displayLarge = type[Display], displayMedium = type[Display], displaySmall = type[LargeTitle],
        headlineLarge = type[LargeTitle], headlineMedium = type[Title1], headlineSmall = type[Title2],
        titleLarge = type[Title2], titleMedium = type[Title3], titleSmall = type[Body1Strong],
        bodyLarge = type[Body1], bodyMedium = type[Body2], bodySmall = type[Caption1],
        labelLarge = type[Body1Strong], labelMedium = type[Body2Strong], labelSmall = type[Caption1Strong],
    )
}

val ShopShapes = Shapes(
    extraSmall = RoundedCornerShape(CornerRadius20.value), small = RoundedCornerShape(CornerRadius40.value),
    medium = RoundedCornerShape(CornerRadius80.value), large = RoundedCornerShape(CornerRadius120.value),
    extraLarge = RoundedCornerShape(CornerRadius160.value),
)

object ShopSpacing {
    val small = 8.dp
    val medium = 12.dp
    val content = 16.dp
    val section = 24.dp
    val touchTarget = 48.dp
    val formWidth = 640.dp
}
