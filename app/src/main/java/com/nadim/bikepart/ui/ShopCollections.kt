package com.nadim.bikepart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralStrokeColorTokens.Stroke2
import com.microsoft.fluentui.theme.token.StateBrush
import com.microsoft.fluentui.theme.token.controlTokens.ListItemInfo
import com.microsoft.fluentui.theme.token.controlTokens.ListItemTokens
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.menu.Menu
import com.nadim.bikepart.ui.theme.ShopSpacing
import com.nadim.bikepart.ui.theme.shopIconVector
import com.microsoft.fluentui.tokenized.controls.TextField as FluentTextField
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource

private val LocalShopListGroup = staticCompositionLocalOf { false }
private val LocalShopListGroupDividers = staticCompositionLocalOf { false }

/** A single quiet surface for a collection, without a card around every row. */
@Composable
internal fun ShopListGroup(
    modifier: Modifier = Modifier,
    showDividers: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(), shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        CompositionLocalProvider(
            LocalShopListGroup provides true,
            LocalShopListGroupDividers provides showDividers,
        ) {
            Column(Modifier
                .fillMaxWidth()
                .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(if (showDividers) 0.dp else 4.dp),
                content = content
            )
        }
    }
}

/** Fluent list-item tokens preserve the quiet grouped-row surface. */
private class ShopRowListItemTokens(private val background: Color) : ListItemTokens() {
    @Composable
    override fun backgroundBrush(listItemInfo: ListItemInfo): StateBrush =
        StateBrush(rest = SolidColor(background), pressed = SolidColor(background))
}

/**
 * One product, order or settings row: Fluent V2 ListItem owns its typography,
 * interaction and list semantics; caller slots retain shop-specific metadata.
 */
@Composable
internal fun ShopListRow(title: String, modifier: Modifier = Modifier, supporting: String? = null,
    meta: String? = null, icon: Int? = null, leading: (@Composable () -> Unit)? = null,
    status: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null, enabled: Boolean = true, actionLabel: String? = null,
                         showChevron: Boolean = false, dividerAbove: Boolean = false,
                         selected: Boolean? = null, role: Role? = null, minHeight: Dp = 64.dp,
                         containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    val metaStyle = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rowColor = if (LocalShopListGroup.current) Color.Transparent else containerColor
    val tokens = remember(rowColor) { ShopRowListItemTokens(rowColor) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val metaMax = (maxWidth - 96.dp).coerceAtLeast(48.dp) * 0.45f
        val stackedMeta = meta != null && (density.fontScale >= 1.5f ||
                measurer.measure(meta, metaStyle, maxLines = 1, softWrap = false).size.width > with(
            density
        ) { metaMax.toPx() })
        val bottomStatus = status
        val bottomMeta = meta.takeIf { stackedMeta }
        val bottomContent: (@Composable () -> Unit)? =
            if (bottomStatus != null || bottomMeta != null) {
                {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        bottomStatus?.let { slot ->
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) { slot() }
                        }
                        bottomMeta?.let {
                            Text(
                                it,
                                style = metaStyle,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else null
        val trailingMeta = meta.takeIf { !stackedMeta }
        val trailingContent: (@Composable () -> Unit)? =
            if (trailingMeta != null || trailing != null || showChevron) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        trailingMeta?.let {
                            Text(
                                it,
                                style = metaStyle,
                                maxLines = 2,
                                textAlign = TextAlign.End,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = metaMax)
                            )
                        }
                        trailing?.invoke()
                        if (showChevron) Icon(
                            painterResource(com.nadim.bikepart.R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else null
        Column(Modifier.fillMaxWidth()) {
            if (dividerAbove && LocalShopListGroupDividers.current) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(
                            FluentTheme.aliasTokens.neutralStrokeColor[Stroke2].value(FluentTheme.themeMode)
                        )
                )
            }
            ListItem.Item(
                text = title,
                modifier = Modifier.heightIn(min = minHeight),
                subText = supporting,
                textMaxLines = 2,
                subTextMaxLines = 2,
                onClick = onClick,
                enabled = enabled,
                leadingAccessoryContent = leading ?: icon?.let { { ShopIconTile(it) } },
                trailingAccessoryContent = trailingContent,
                bottomContent = bottomContent,
                textAccessibilityProperties = if (actionLabel != null || selected != null || role != null) {
                    ({
                        actionLabel?.let { text = AnnotatedString(it) }
                        selected?.let { this.selected = it }
                        role?.let { this.role = it }
                    })
                } else null,
                listItemTokens = tokens,
            )
        }
    }
}

@Composable
internal fun ShopSearchField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null) {
    if (trailing == null) {
        FluentTextField(
            value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
            enabled = enabled, label = label, assistiveText = supporting,
            leadingRestIcon = shopIconVector(com.nadim.bikepart.R.drawable.ic_search),
            leadingIconContentDescription = null, textFieldContentDescription = label
        )
    } else {
        Row(
            modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)
        ) {
            FluentTextField(
                value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f),
                enabled = enabled, label = label, assistiveText = supporting,
                leadingRestIcon = shopIconVector(com.nadim.bikepart.R.drawable.ic_search),
                leadingIconContentDescription = null, textFieldContentDescription = label
            )
            trailing()
        }
    }
}

/** Fluent V2 single-line field; multiline content keeps the tokenized Compose fallback. */
@Composable
internal fun ShopTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    contentDescription: String = label,
) {
    FluentTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label,
        assistiveText = supporting,
        errorString = error,
        keyboardOptions = keyboardOptions,
        textFieldContentDescription = contentDescription,
        trailingAccessoryIcon = null,
    )
}

internal data class ShopMenuAction(val label: String, val icon: Int, val onClick: () -> Unit)

@Composable
internal fun ShopOverflowMenu(label: String, actions: List<ShopMenuAction>, enabled: Boolean = true) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        ShopIconActionButton(
            label,
            com.nadim.bikepart.R.drawable.ic_more_vert,
            enabled
        ) { expanded = true }
        Menu(opened = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.widthIn(min = 200.dp)) {
                actions.forEach { action ->
                    ListItem.Item(
                        text = action.label, textMaxLines = 2, enabled = enabled,
                        leadingAccessoryContent = {
                            Icon(
                                painterResource(action.icon),
                                contentDescription = null
                            )
                        },
                        onClick = { expanded = false; action.onClick() })
                }
            }
        }
    }
}
