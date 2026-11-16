package com.nadim.bikepart.ui.theme

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens.*
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.ControlTokens.ControlType
import com.microsoft.fluentui.theme.token.controlTokens.AppBarInfo
import com.microsoft.fluentui.theme.token.controlTokens.AppBarTokens

/** Accent changes component style, while the chosen alias palette remains active. */
val LocalShopFluentStyle = staticCompositionLocalOf { FluentStyle.Brand }

data class ShopNavigationColors(val indicator: Color, val icon: Color, val label: Color)

/** Fluent semantic adaptation for the transitional Material navigation bar and rail. */
@Composable
fun shopNavigationColors(): ShopNavigationColors {
    val alias = FluentTheme.aliasTokens
    val mode = FluentTheme.themeMode
    val branded = LocalShopFluentStyle.current == FluentStyle.Brand
    return ShopNavigationColors(
        indicator = if (branded) alias.brandBackgroundColor[BrandBackgroundColorTokens.BrandBackgroundTint].value(mode)
            else alias.neutralBackgroundColor[NeutralBackgroundColorTokens.Background5].value(mode),
        icon = alias.neutralForegroundColor[NeutralForegroundColorTokens.Foreground1].value(mode),
        label = if (branded) alias.brandForegroundColor[BrandForegroundColorTokens.BrandForeground1].value(mode)
            else alias.neutralForegroundColor[NeutralForegroundColorTokens.Foreground1].value(mode),
    )
}

data class ShopAppBarColors(val background: Color, val title: Color, val navigation: Color)

@Composable
fun shopAppBarColors(): ShopAppBarColors {
    val tokens = FluentTheme.controlTokens.tokens[ControlType.AppBarControlType] as AppBarTokens
    val info = AppBarInfo(style = LocalShopFluentStyle.current)
    return ShopAppBarColors((tokens.backgroundBrush(info) as SolidColor).value,
        tokens.titleTextColor(info), tokens.navigationIconColor(info))
}

/** Pick icons from the actual rendered bar background, including branded light headers. */
@Composable
fun ShopSystemBarAppearance(statusBackground: Color, navigationBackground: Color) {
    val context = LocalContext.current
    fun darkIcons(background: Color): Boolean {
        val lightness = background.luminance()
        return (lightness + .05f) / .05f >= 1.05f / (lightness + .05f)
    }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = darkIcons(statusBackground)
                isAppearanceLightNavigationBars = darkIcons(navigationBackground)
            }
        }
    }
}
