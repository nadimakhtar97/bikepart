package com.nadim.bikepart.ui.theme

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.ThemeMode
import com.microsoft.fluentui.theme.token.*
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens.*
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralForegroundColorTokens.*
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralStrokeColorTokens.*
import com.microsoft.fluentui.theme.token.FluentAliasTokens.BrandBackgroundColorTokens.*
import com.microsoft.fluentui.theme.token.FluentAliasTokens.BrandForegroundColorTokens.*
import com.microsoft.fluentui.theme.token.FluentAliasTokens.ErrorAndStatusColorTokens.*
import com.nadim.bikepart.shop.Appearance

/** One scoped configuration, shared by native controls and the transitional Material bridge.
 * Future brand choices supply complete alias tokens here, rather than global SDK mutations.
 */
class ShopThemeConfiguration(
    val aliasTokens: IAliasTokens = AliasTokens(),
    val controlTokens: IControlTokens = ControlTokens(),
    val shapes: Shapes = ShopShapes,
    val icons: Map<Int, Int> = emptyMap(),
)
private val defaultConfiguration = ShopThemeConfiguration()

@Composable
fun BikepartTheme(appearance: Appearance = Appearance.System,
    configuration: ShopThemeConfiguration = defaultConfiguration, content: @Composable () -> Unit) {
    val mode = when (appearance) {
        Appearance.System -> ThemeMode.Auto
        Appearance.Light -> ThemeMode.Light
        Appearance.Dark -> ThemeMode.Dark
    }
    val dark = when (appearance) {
        Appearance.System -> isSystemInDarkTheme()
        Appearance.Light -> false
        Appearance.Dark -> true
    }
    val context = LocalContext.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    FluentTheme(aliasTokens = configuration.aliasTokens, controlTokens = configuration.controlTokens, themeMode = mode) {
        CompositionLocalProvider(LocalShopIcons provides configuration.icons) {
            MaterialTheme(colorScheme = fluentMaterialColors(FluentTheme.aliasTokens, dark),
                typography = fluentTypography(FluentTheme.aliasTokens), shapes = configuration.shapes, content = content)
        }
    }
}

/** Every Material role is derived from the same effective Fluent alias scope. */
internal fun fluentMaterialColors(tokens: IAliasTokens, dark: Boolean): ColorScheme {
    fun FluentColor.resolve() = if (dark) this.dark else light
    fun background(token: FluentAliasTokens.NeutralBackgroundColorTokens) = tokens.neutralBackgroundColor[token].resolve()
    fun foreground(token: FluentAliasTokens.NeutralForegroundColorTokens) = tokens.neutralForegroundColor[token].resolve()
    val brand = tokens.brandForegroundColor[BrandForeground1].resolve()
    val fill = tokens.brandBackgroundColor[BrandBackground1].resolve()
    val tint = tokens.brandBackgroundColor[BrandBackgroundTint].resolve()
    val onBrand = foreground(ForegroundOnColor)
    val text = foreground(Foreground1)
    val secondaryText = foreground(Foreground2)
    val surface = background(Background1)
    val canvas = background(CanvasBackground)
    val error = tokens.errorAndStatusColor[DangerForeground1].resolve()
    val errorContainer = tokens.errorAndStatusColor[DangerBackground1].resolve()
    val outline = tokens.neutralStrokeColor[StrokeAccessible].resolve()
    // Inverse roles use the opposite appearance's neutral semantic pair, not an unrelated palette.
    val inverse = if (dark) tokens.neutralBackgroundColor[Background1].light else tokens.neutralBackgroundColor[Background1].dark
    val inverseText = if (dark) tokens.neutralForegroundColor[Foreground1].light else tokens.neutralForegroundColor[Foreground1].dark
    return lightColorScheme(
        primary = brand, onPrimary = onBrand, primaryContainer = tint, onPrimaryContainer = text,
        secondary = brand, onSecondary = onBrand, secondaryContainer = tint, onSecondaryContainer = text,
        tertiary = brand, onTertiary = onBrand, tertiaryContainer = tint, onTertiaryContainer = text,
        error = error, onError = background(Background1), errorContainer = errorContainer, onErrorContainer = text,
        background = canvas, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = background(Background2), onSurfaceVariant = secondaryText,
        outline = outline, outlineVariant = tokens.neutralStrokeColor[Stroke1].resolve(),
        inverseSurface = inverse, inverseOnSurface = inverseText,
        inversePrimary = if (dark) tokens.brandForegroundColor[BrandForeground1].light else tokens.brandForegroundColor[BrandForeground1].dark,
        surfaceTint = fill, scrim = foreground(ForegroundDarkStatic),
        surfaceDim = background(Background3), surfaceBright = surface,
        surfaceContainerLowest = surface, surfaceContainerLow = background(Background2),
        surfaceContainer = background(Background2), surfaceContainerHigh = background(Background3),
        // Background4 gives only 4.4986:1 with the default dark link token.
        // Keep the next native neutral surface so normal text meets 4.5:1 without rounding.
        surfaceContainerHighest = background(if (dark) Background3 else Background4),
        primaryFixed = tint, primaryFixedDim = tint, onPrimaryFixed = text, onPrimaryFixedVariant = text,
        secondaryFixed = tint, secondaryFixedDim = tint, onSecondaryFixed = text, onSecondaryFixedVariant = text,
        tertiaryFixed = tint, tertiaryFixedDim = tint, onTertiaryFixed = text, onTertiaryFixedVariant = text,
    )
}

/** Resource identities remain semantic; replacing a family needs no screen edits. */
private val LocalShopIcons = staticCompositionLocalOf<Map<Int, Int>> { emptyMap() }

@Composable
fun shopIconPainter(resource: Int): androidx.compose.ui.graphics.painter.Painter =
    androidx.compose.ui.res.painterResource(LocalShopIcons.current[resource] ?: resource)
