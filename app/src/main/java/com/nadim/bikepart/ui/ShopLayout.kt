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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
 * Shared layout primitives for the shop screens. They keep the approved Now in
 * Android colour roles while giving every screen the same page grid, row rhythm
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

private val LocalShopListGroup = staticCompositionLocalOf { false }

/** Secondary actions share the same touch target and wrapping behavior as the primary action. */
@Composable
internal fun ShopSecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(), content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = ShopSpacing.touchTarget),
        enabled = enabled, shape = shape, colors = colors, content = content)
}

/** A single quiet surface for a collection, without a card around every row. */
@Composable
internal fun ShopListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        CompositionLocalProvider(LocalShopListGroup provides true) {
            Column(Modifier.fillMaxWidth().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
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

/** Keeps one tone-to-role mapping for chips and status tiles. */
@Composable
private fun shopStatusColors(tone: ShopStatusTone): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (tone) {
        ShopStatusTone.Neutral -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        ShopStatusTone.Accent -> colors.secondaryContainer to colors.onSecondaryContainer
        ShopStatusTone.Warning -> colors.secondaryFixed to colors.onSecondaryFixedVariant
        ShopStatusTone.Danger -> colors.errorContainer to colors.onErrorContainer
        ShopStatusTone.Success -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
}

@Composable
internal fun ShopStatusChip(label: String, tone: ShopStatusTone = ShopStatusTone.Neutral, modifier: Modifier = Modifier) {
    val (container, content) = shopStatusColors(tone)
    Surface(modifier.heightIn(min = 24.dp), shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

internal enum class ShopStatusTone { Neutral, Accent, Warning, Danger, Success }

/**
 * An icon tile that carries the same status tone as [ShopStatusChip], so attention
 * rows read at a glance without relying on copy alone.
 */
@Composable
internal fun ShopStatusIconTile(icon: Int, tone: ShopStatusTone, modifier: Modifier = Modifier) {
    val (container, content) = shopStatusColors(tone)
    ShopIconTile(icon, modifier, container = container, contentColor = content)
}

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
    showChevron: Boolean = false, containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow) {
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
            if (status != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) { status() }
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
    val rowColor = if (LocalShopListGroup.current) Color.Transparent else containerColor
    val contentModifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, shape = shape, color = rowColor,
            modifier = modifier.then(contentModifier).semantics { if (actionLabel != null) text = AnnotatedString(actionLabel) }) {
            BoxWithConstraints {
                val metaMax = (maxWidth - 24.dp) * 0.45f
                val stacked = meta != null && (density.fontScale >= 1.5f || measurer.measure(meta, metaStyle,
                    maxLines = 1, softWrap = false).size.width > with(density) { metaMax.toPx() })
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) { row(metaMax, stacked) }
            }
        }
    } else {
        Surface(shape = shape, color = rowColor, modifier = modifier.then(contentModifier)) {
            BoxWithConstraints {
                val metaMax = (maxWidth - 24.dp) * 0.45f
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
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
    if (onClick != null) Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small), content = content)
    } else Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small), content = content)
    }
}

@Composable
internal fun ShopActionTile(label: String, icon: Int, modifier: Modifier = Modifier, enabled: Boolean = true,
    primary: Boolean = false, onClick: () -> Unit) {
    val container = if (primary) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (primary) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium, color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = 88.dp)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
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
        trailingIcon = trailing, shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = Color.Transparent), modifier = modifier.fillMaxWidth())
}

@Composable
internal fun ShopScanButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.sizeIn(minWidth = ShopSpacing.touchTarget, minHeight = ShopSpacing.touchTarget)
        .semantics { text = AnnotatedString(label) }) {
        Icon(painterResource(com.nadim.bikepart.R.drawable.ic_qr_code_scanner), contentDescription = null)
    }
}

internal data class ShopMenuAction(val label: String, val icon: Int, val onClick: () -> Unit)

@Composable
internal fun ShopOverflowMenu(label: String, actions: List<ShopMenuAction>, enabled: Boolean = true) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.sizeIn(minWidth = ShopSpacing.touchTarget, minHeight = ShopSpacing.touchTarget)
                .semantics { contentDescription = label }) {
            Icon(painterResource(com.nadim.bikepart.R.drawable.ic_more_vert), contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action -> DropdownMenuItem(text = { Text(action.label) },
                leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) },
                enabled = enabled, onClick = { expanded = false; action.onClick() }) }
        }
    }
}

@Composable
internal fun ShopBackButton(onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.semantics { text = AnnotatedString("Back") }) {
        Icon(painterResource(com.nadim.bikepart.R.drawable.ic_arrow_back), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface)
    }
}
