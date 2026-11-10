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

// Generated from the reviewed Figma snapshot; see tools/theme/figma-tokens.json.
internal val lightShopColors = lightColorScheme(
    onBackground = Color(0xFF201A1F),
    primary = Color(0xFF712876),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFA9FE),
    onPrimaryContainer = Color(0xFF732A78),
    secondary = Color(0xFFA23F16),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFF8456),
    onSecondaryContainer = Color(0xFF6F2200),
    tertiary = Color(0xFF2E6578),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB3E9FF),
    onTertiaryContainer = Color(0xFF336A7D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFF7FA),
    surface = Color(0xFFFDF8F8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFF0DDEB),
    onSurfaceVariant = Color(0xFF4F434E),
    outline = Color(0xFF81737E),
    inverseSurface = Color(0xFF323030),
    inverseOnSurface = Color(0xFFF5F0EF),
    inversePrimary = Color(0xFFFFA9FE),
    surfaceContainer = Color(0xFFF2EDEC),
    surfaceDim = Color(0xFFDDD9D9),
    surfaceBright = Color(0xFFFDF8F8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2F2),
    surfaceContainerHigh = Color(0xFFECE7E7),
    surfaceContainerHighest = Color(0xFFE6E1E1),
    outlineVariant = Color(0xFFCAC5C6),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF712876),
    primaryFixed = Color(0xFFFFD6FA),
    primaryFixedDim = Color(0xFFFFA9FE),
    onPrimaryFixed = Color(0xFF37003C),
    onPrimaryFixedVariant = Color(0xFF702775),
    secondaryFixed = Color(0xFFFFDBCF),
    secondaryFixedDim = Color(0xFFFFB59B),
    onSecondaryFixed = Color(0xFF380D00),
    onSecondaryFixedVariant = Color(0xFF812800),
    tertiaryFixed = Color(0xFFB8EAFF),
    tertiaryFixedDim = Color(0xFF99CEE4),
    onTertiaryFixed = Color(0xFF001F28),
    onTertiaryFixedVariant = Color(0xFF0E4D5F),
)

internal val darkShopColors = darkColorScheme(
    primary = Color(0xFFFFA9FE),
    onPrimary = Color(0xFF560A5D),
    primaryContainer = Color(0xFF8C4190),
    onPrimaryContainer = Color(0xFFFFC8FB),
    secondary = Color(0xFFFFB59B),
    onSecondary = Color(0xFF5B1A00),
    secondaryContainer = Color(0xFFFF8456),
    onSecondaryContainer = Color(0xFF6F2200),
    tertiary = Color(0xFFFCFDFF),
    onTertiary = Color(0xFF003544),
    tertiaryContainer = Color(0xFFB3E9FF),
    onTertiaryContainer = Color(0xFF336A7D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF171217),
    onBackground = Color(0xFFECDFE7),
    surface = Color(0xFF141313),
    onSurface = Color(0xFFE6E1E1),
    surfaceVariant = Color(0xFF4F434E),
    onSurfaceVariant = Color(0xFFD3C1CE),
    outline = Color(0xFF9C8C98),
    inverseSurface = Color(0xFFE6E1E1),
    inverseOnSurface = Color(0xFF323030),
    surfaceContainer = Color(0xFF211F1F),
    inversePrimary = Color(0xFF8B418F),
    surfaceDim = Color(0xFF141313),
    surfaceBright = Color(0xFF3A3939),
    surfaceContainerLowest = Color(0xFF0F0E0E),
    surfaceContainerLow = Color(0xFF1C1B1B),
    surfaceContainerHigh = Color(0xFF2B2A2A),
    surfaceContainerHighest = Color(0xFF363434),
    outlineVariant = Color(0xFF484646),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFFFA9FE),
    primaryFixed = Color(0xFFFFD6FA),
    primaryFixedDim = Color(0xFFFFA9FE),
    onPrimaryFixed = Color(0xFF37003C),
    onPrimaryFixedVariant = Color(0xFF702775),
    secondaryFixed = Color(0xFFFFDBCF),
    secondaryFixedDim = Color(0xFFFFB59B),
    onSecondaryFixed = Color(0xFF380D00),
    onSecondaryFixedVariant = Color(0xFF812800),
    tertiaryFixed = Color(0xFFB8EAFF),
    tertiaryFixedDim = Color(0xFF99CEE4),
    onTertiaryFixed = Color(0xFF001F28),
    onTertiaryFixedVariant = Color(0xFF0E4D5F),
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
    MaterialTheme(colorScheme = if (dark) darkShopColors else lightShopColors, typography = Typography, shapes = ShopShapes, content = content)
}
