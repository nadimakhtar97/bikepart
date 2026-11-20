package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens.BrandBackgroundColorTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.BrandForegroundColorTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralForegroundColorTokens
import com.microsoft.fluentui.theme.token.FluentStyle
import com.nadim.bikepart.ui.theme.LocalShopFluentStyle
import com.nadim.bikepart.ui.theme.ShopSpacing
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource

/**
 * Shared layout primitives for the shop screens. They use scoped Fluent
 * semantic color roles while giving every screen the same page grid, row rhythm
 * and trailing alignment.
 */

@Composable
internal fun ShopPageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier
        .fillMaxWidth()
        .padding(vertical = ShopSpacing.small), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = FluentTheme.aliasTokens.neutralForegroundColor[NeutralForegroundColorTokens.Foreground2]
                .value(FluentTheme.themeMode)
        )
    }
}

@Composable
internal fun ShopSectionHeader(title: String, supporting: String? = null, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: (() -> Unit)? = null, actionEnabled: Boolean = true) {
    FlowRow(modifier
        .fillMaxWidth()
        .padding(top = ShopSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier
            .weight(1f)
            .widthIn(min = 160.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium,
                color = FluentTheme.aliasTokens.neutralForegroundColor[NeutralForegroundColorTokens.Foreground2]
                    .value(FluentTheme.themeMode)
            )
        }
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction, enabled = actionEnabled,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(actionLabel) }
    }
}

/** Equal columns at ordinary text sizes; full-width tiles when labels need room. */
@Composable
internal fun ShopTileGrid(content: @Composable (Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 320.dp && fontScale < 1.5f) 2 else 1
        val tileWidth = (maxWidth - ShopSpacing.medium * (columns - 1)) / columns
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(ShopSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ShopSpacing.medium)) {
            content(Modifier.width(tileWidth))
        }
    }
}

/** The approved primary-at-8%-over-surface grouped form/settings surface. */
@Composable
internal fun ShopGroup(title: String? = null, modifier: Modifier = Modifier,
    spacing: Dp = ShopSpacing.medium, content: @Composable ColumnScope.() -> Unit) {
    val aliases = FluentTheme.aliasTokens
    val mode = FluentTheme.themeMode
    val surface =
        aliases.neutralBackgroundColor[NeutralBackgroundColorTokens.Background1].value(mode)
    val foreground =
        aliases.neutralForegroundColor[NeutralForegroundColorTokens.Foreground1].value(mode)
    val primary =
        aliases.brandForegroundColor[BrandForegroundColorTokens.BrandForeground1].value(mode)
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = primary.copy(alpha = .08f).compositeOver(surface), contentColor = foreground
    ) {
        Column(Modifier
            .fillMaxWidth()
            .padding(ShopSpacing.content),
            verticalArrangement = Arrangement.spacedBy(spacing)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

/** Kept for the feature screens that have not moved to ShopGroup yet. */
@Composable
internal fun ShopSection(title: String, content: @Composable ColumnScope.() -> Unit) = ShopGroup(title, content = content)

@Composable
internal fun ShopIconTile(icon: Int, modifier: Modifier = Modifier, size: Dp = 40.dp,
                          container: Color = FluentTheme.aliasTokens.brandBackgroundColor[BrandBackgroundColorTokens.BrandBackgroundTint]
                              .value(FluentTheme.themeMode),
                          contentColor: Color = FluentTheme.aliasTokens.neutralForegroundColor[NeutralForegroundColorTokens.Foreground1]
                              .value(FluentTheme.themeMode)
) {
    Surface(modifier.size(size), shape = RoundedCornerShape(12.dp), color = container) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = contentColor,
                modifier = Modifier.size(if (size >= 40.dp) 22.dp else 18.dp))
        }
    }
}

@Composable
internal fun ShopStatCard(label: String, value: String, icon: Int, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    val content: @Composable ColumnScope.() -> Unit = {
        val aliases = FluentTheme.aliasTokens
        val mode = FluentTheme.themeMode
        val primary =
            aliases.brandForegroundColor[BrandForegroundColorTokens.BrandForeground1].value(mode)
        val supporting =
            aliases.neutralForegroundColor[NeutralForegroundColorTokens.Foreground2].value(mode)
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            Icon(
                painterResource(icon), contentDescription = null, tint = primary,
                modifier = Modifier.size(20.dp))
            Text(
                label, style = MaterialTheme.typography.labelMedium, color = supporting,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
    val aliases = FluentTheme.aliasTokens
    val mode = FluentTheme.themeMode
    val surface =
        aliases.neutralBackgroundColor[NeutralBackgroundColorTokens.Background1].value(mode)
    val isDark = surface.luminance() < 0.5f
    val containerColor = aliases.neutralBackgroundColor[
        if (isDark) NeutralBackgroundColorTokens.Background2 else NeutralBackgroundColorTokens.Background5
    ].value(mode)
    if (onClick != null) Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier
            .fillMaxWidth()
            .padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small), content = content)
    } else Surface(shape = MaterialTheme.shapes.medium, color = containerColor,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier
            .fillMaxWidth()
            .padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small), content = content)
    }
}

@Composable
internal fun ShopActionTile(label: String, icon: Int, modifier: Modifier = Modifier, enabled: Boolean = true,
    primary: Boolean = false, onClick: () -> Unit) {
    val aliases = FluentTheme.aliasTokens
    val mode = FluentTheme.themeMode
    val branded = LocalShopFluentStyle.current == FluentStyle.Brand
    val neutralSurface = aliases.neutralBackgroundColor[NeutralBackgroundColorTokens.Background1]
    val neutralForeground = aliases.neutralForegroundColor[NeutralForegroundColorTokens.Foreground1]
    val surface = neutralSurface.value(mode)
    val foreground = neutralForeground.value(mode)
    val brand =
        aliases.brandForegroundColor[BrandForegroundColorTokens.BrandForeground1].value(mode)
    val brandOn =
        aliases.neutralForegroundColor[NeutralForegroundColorTokens.ForegroundOnColor].value(mode)
    val brandContainer =
        aliases.brandBackgroundColor[BrandBackgroundColorTokens.BrandBackgroundTint].value(mode)
    val isDark = surface.luminance() < 0.5f
    val (container, content) = when {
        primary && branded -> brand to brandOn
        primary -> (if (isDark) neutralSurface.light else neutralSurface.dark) to
                (if (isDark) neutralForeground.light else neutralForeground.dark)

        branded -> brandContainer to foreground
        isDark -> aliases.neutralBackgroundColor[NeutralBackgroundColorTokens.Background2].value(
            mode
        ) to foreground

        else -> aliases.neutralBackgroundColor[NeutralBackgroundColorTokens.Background5].value(mode) to foreground
    }
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium, color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier
            .fillMaxWidth()
            .padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
