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

private val LocalShopListGroup = staticCompositionLocalOf { false }

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
