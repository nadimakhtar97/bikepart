package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
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
 * Shared layout primitives for the shop screens. They keep the approved Now in
 * Android colour roles while giving every screen the same page grid, row rhythm
 * and trailing alignment.
 */

@Composable
internal fun ShopPageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ShopSectionHeader(title: String, supporting: String? = null, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: (() -> Unit)? = null, actionEnabled: Boolean = true) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction, enabled = actionEnabled,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(actionLabel) }
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
internal fun ShopStatusChip(label: String, tone: ShopStatusTone = ShopStatusTone.Neutral, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        ShopStatusTone.Neutral -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        ShopStatusTone.Accent -> colors.secondaryContainer to colors.onSecondaryContainer
        ShopStatusTone.Warning -> colors.secondaryFixed to colors.onSecondaryFixedVariant
        ShopStatusTone.Danger -> colors.errorContainer to colors.onErrorContainer
        ShopStatusTone.Success -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(modifier.heightIn(min = 24.dp), shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal enum class ShopStatusTone { Neutral, Accent, Warning, Danger, Success }

/**
 * One product, order or settings row: leading icon/photo, title and supporting
 * copy, trailing value or control. [actionLabel] supplies the whole-row action
 * text for screen readers (and the workflow tests click that same label).
 */
@Composable
internal fun ShopListRow(title: String, modifier: Modifier = Modifier, supporting: String? = null,
    meta: String? = null, icon: Int? = null, leading: (@Composable () -> Unit)? = null,
    status: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null, enabled: Boolean = true, actionLabel: String? = null,
    showChevron: Boolean = onClick != null, containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow) {
    val metaStyle = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // At large system text, or when a price is too long for its share of the row,
    // the trailing value moves under the title instead of squeezing it or breaking
    // a grouped number across lines.
    val row: @Composable RowScope.(Dp, Boolean) -> Unit = { metaMax, stackedMeta ->
        if (leading != null) leading()
        else if (icon != null) ShopIconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (status != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { status() }
            if (stackedMeta) Text(meta.orEmpty(), style = metaStyle)
        }
        if (meta != null && !stackedMeta) Text(meta, style = metaStyle, maxLines = 2,
            textAlign = TextAlign.End, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = metaMax))
        trailing?.invoke()
        if (showChevron) Icon(painterResource(com.nadim.bikepart.R.drawable.ic_chevron_right),
            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
    val shape = MaterialTheme.shapes.small
    val contentModifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, shape = shape, color = containerColor,
            modifier = modifier.then(contentModifier).semantics { if (actionLabel != null) text = AnnotatedString(actionLabel) }) {
            BoxWithConstraints {
                val metaMax = maxWidth * 0.45f
                val stacked = meta != null && (density.fontScale >= 1.5f || measurer.measure(meta, metaStyle,
                    maxLines = 1, softWrap = false).size.width > with(density) { metaMax.toPx() })
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) { row(metaMax, stacked) }
            }
        }
    } else {
        Surface(shape = shape, color = containerColor, modifier = modifier.then(contentModifier)) {
            BoxWithConstraints {
                val metaMax = maxWidth * 0.45f
                val stacked = meta != null && (density.fontScale >= 1.5f || measurer.measure(meta, metaStyle,
                    maxLines = 1, softWrap = false).size.width > with(density) { metaMax.toPx() })
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) { row(metaMax, stacked) }
            }
        }
    }
}

@Composable
internal fun ShopStatCard(label: String, value: String, icon: Int, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    val content: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
    }
    if (onClick != null) Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween, content = content)
    } else Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween, content = content)
    }
}

@Composable
internal fun ShopActionTile(label: String, icon: Int, modifier: Modifier = Modifier, enabled: Boolean = true,
    primary: Boolean = false, onClick: () -> Unit) {
    val container = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium, color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ShopSearchField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null) {
    OutlinedTextField(value, onValueChange, enabled = enabled, label = { Text(label) }, singleLine = true,
        leadingIcon = { Icon(painterResource(com.nadim.bikepart.R.drawable.ic_search), contentDescription = null) },
        supportingText = if (supporting == null) null else ({ Text(supporting) }),
        trailingIcon = trailing, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth())
}

@Composable
internal fun ShopBackButton(onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.semantics { text = AnnotatedString("Back") }) {
        Icon(painterResource(com.nadim.bikepart.R.drawable.ic_arrow_back), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface)
    }
}
