package com.nadim.bikepart.ui.theme

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nadim.bikepart.shop.Appearance

// Generated from #006A6A using @material/material-color-utilities 0.3.0.
internal val lightShopColors = lightColorScheme(
    primary = Color(0xFF006A6A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF7F6),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2D3131),
    inverseOnSurface = Color(0xFFEFF1F0),
    inversePrimary = Color(0xFF4CDADA),
    surfaceDim = Color(0xFFD8DADA),
    surfaceBright = Color(0xFFF7FAF9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F3),
    surfaceContainer = Color(0xFFECEEED),
    surfaceContainerHigh = Color(0xFFE6E9E8),
    surfaceContainerHighest = Color(0xFFE0E3E2),
    primaryFixed = Color(0xFF6FF7F6),
    primaryFixedDim = Color(0xFF4CDADA),
    onPrimaryFixed = Color(0xFF002020),
    onPrimaryFixedVariant = Color(0xFF004F4F),
    secondaryFixed = Color(0xFFCCE8E7),
    secondaryFixedDim = Color(0xFFB0CCCB),
    onSecondaryFixed = Color(0xFF051F1F),
    onSecondaryFixedVariant = Color(0xFF324B4B),
    tertiaryFixed = Color(0xFFD3E4FF),
    tertiaryFixedDim = Color(0xFFB3C8E8),
    onTertiaryFixed = Color(0xFF041C35),
    onTertiaryFixedVariant = Color(0xFF334863),
)

internal val darkShopColors = darkColorScheme(
    primary = Color(0xFF4CDADA),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = Color(0xFF6FF7F6),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF3F4948),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE0E3E2),
    inverseOnSurface = Color(0xFF2D3131),
    inversePrimary = Color(0xFF006A6A),
    surfaceDim = Color(0xFF101414),
    surfaceBright = Color(0xFF363A3A),
    surfaceContainerLowest = Color(0xFF0B0F0F),
    surfaceContainerLow = Color(0xFF191C1C),
    surfaceContainer = Color(0xFF1D2020),
    surfaceContainerHigh = Color(0xFF272B2A),
    surfaceContainerHighest = Color(0xFF323535),
    primaryFixed = Color(0xFF6FF7F6),
    primaryFixedDim = Color(0xFF4CDADA),
    onPrimaryFixed = Color(0xFF002020),
    onPrimaryFixedVariant = Color(0xFF004F4F),
    secondaryFixed = Color(0xFFCCE8E7),
    secondaryFixedDim = Color(0xFFB0CCCB),
    onSecondaryFixed = Color(0xFF051F1F),
    onSecondaryFixedVariant = Color(0xFF324B4B),
    tertiaryFixed = Color(0xFFD3E4FF),
    tertiaryFixedDim = Color(0xFFB3C8E8),
    onTertiaryFixed = Color(0xFF041C35),
    onTertiaryFixedVariant = Color(0xFF334863),
)

@Composable
fun BikepartTheme(appearance: Appearance = Appearance.System, content: @Composable () -> Unit) {
    val dark = when (appearance) { Appearance.System -> isSystemInDarkTheme(); Appearance.Light -> false; Appearance.Dark -> true }
    val context = LocalContext.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) darkShopColors else lightShopColors, typography = Typography, content = content)
}
