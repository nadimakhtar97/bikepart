package com.nadim.bikepart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens.CanvasBackground
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralForegroundColorTokens.Foreground1
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.controlTokens.AppBarSize
import com.microsoft.fluentui.tokenized.AppBar
import com.nadim.bikepart.ui.theme.LocalShopFluentStyle
import com.nadim.bikepart.ui.theme.ShopSpacing
import com.nadim.bikepart.ui.theme.shopAppBarColors

internal val LocalShopRouteBack = compositionLocalOf<() -> Unit> { {} }

internal val ShopRouteScaffoldModifier: Modifier = Modifier.fillMaxSize()

@Composable
internal fun ShopRouteColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val themeMode = FluentTheme.themeMode
    val canvas = FluentTheme.aliasTokens.neutralBackgroundColor[CanvasBackground].value(themeMode)
    val onBackground = FluentTheme.aliasTokens.neutralForegroundColor[Foreground1].value(themeMode)

    Box(
        Modifier
            .fillMaxSize()
            .background(canvas)
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalContentColor provides onBackground) {
            Column(
                modifier
                    .fillMaxWidth()
                    .widthIn(max = ShopSpacing.formWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(ShopSpacing.content),
                verticalArrangement = Arrangement.spacedBy(ShopSpacing.content),
                content = content,
            )
        }
    }
}

@Composable
internal fun ShopRouteAppBar(
    title: String,
    showBack: Boolean,
    enabled: Boolean = true,
    searchMode: Boolean = false,
    searchBar: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = shopAppBarColors()
    val back = LocalShopRouteBack.current
    val style: FluentStyle = LocalShopFluentStyle.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        AppBar(
            title = title,
            appBarSize = AppBarSize.Medium,
            style = style,
            searchMode = searchMode,
            searchBar = searchBar,
            centerAlignAppBar = true,
            rightAccessoryView = if (showBack) {
                { Spacer(Modifier.width(ShopSpacing.touchTarget + ShopSpacing.small)) }
            } else null,
            logo = if (showBack) {
                { ShopBackButton(onClick = back, enabled = enabled) }
            } else null,
        )
    }
}
