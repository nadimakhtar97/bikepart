package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens
import com.microsoft.fluentui.theme.token.FluentStyle
import com.nadim.bikepart.ui.theme.LocalShopFluentStyle
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.ui.theme.ShopSpacing

/**
 * Shared layout primitives for the shop screens. They use scoped Fluent
 * semantic color roles while giving every screen the same page grid, row rhythm
 * and trailing alignment.
 */

@Composable
internal fun ShopPageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = ShopSpacing.small), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ShopSectionHeader(title: String, supporting: String? = null, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: (() -> Unit)? = null, actionEnabled: Boolean = true) {
    FlowRow(modifier.fillMaxWidth().padding(top = ShopSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.weight(1f).widthIn(min = 160.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = colors.primary.copy(alpha = .08f).compositeOver(colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content),
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
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
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
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerLow
        else FluentTheme.aliasTokens.neutralBackgroundColor[NeutralBackgroundColorTokens.Background5].value(FluentTheme.themeMode)
    if (onClick != null) Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small), content = content)
    } else Surface(shape = MaterialTheme.shapes.medium, color = containerColor,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small), content = content)
    }
}

@Composable
internal fun ShopActionTile(label: String, icon: Int, modifier: Modifier = Modifier, enabled: Boolean = true,
    primary: Boolean = false, onClick: () -> Unit) {
    val branded = LocalShopFluentStyle.current == FluentStyle.Brand
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (container, content) = when {
        primary && branded -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        primary -> MaterialTheme.colorScheme.inverseSurface to MaterialTheme.colorScheme.inverseOnSurface
        branded -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        isDark -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurface
        else -> FluentTheme.aliasTokens.neutralBackgroundColor[NeutralBackgroundColorTokens.Background5].value(FluentTheme.themeMode) to MaterialTheme.colorScheme.onSurface
    }
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium, color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
