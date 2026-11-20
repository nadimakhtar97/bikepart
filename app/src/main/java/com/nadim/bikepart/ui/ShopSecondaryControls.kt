package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.tokenized.controls.BasicChip as FluentBasicChip
import com.nadim.bikepart.ui.theme.ShopSpacing

/** Secondary actions share the same touch target and wrapping behavior as the primary action. */
@Composable
internal fun ShopSecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, shape: Shape? = null,
    colors: ButtonColors? = null, content: @Composable RowScope.() -> Unit) {
    ShopFluentButton(onClick, modifier, enabled,
        com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle.OutlinedButton, shape, colors, content)
}

@Composable
internal fun ShopIconActionButton(
    label: String,
    icon: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    com.microsoft.fluentui.tokenized.controls.Button(
        onClick = onClick,
        modifier = Modifier.sizeIn(
            minWidth = ShopSpacing.touchTarget,
            minHeight = ShopSpacing.touchTarget
        ),
        style = com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle.TextButton,
        size = com.microsoft.fluentui.theme.token.controlTokens.ButtonSize.Large,
        enabled = enabled,
        icon = com.nadim.bikepart.ui.theme.shopIconVector(icon),
        contentDescription = label,
    )
}

@Composable
internal fun ShopScanButton(label: String, enabled: Boolean = true, onClick: () -> Unit) =
    ShopIconActionButton(label, com.nadim.bikepart.R.drawable.ic_qr_code_scanner, enabled, onClick)

@Composable
internal fun ShopBackButton(onClick: () -> Unit, enabled: Boolean = true) =
    ShopIconActionButton("Back", com.nadim.bikepart.R.drawable.ic_arrow_back, enabled, onClick)

/** Fluent V2 chip with an explicit toggle action and an accessible 48dp target. */
@Composable
internal fun ShopChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    FluentBasicChip(
        label = label,
        modifier = modifier
            .heightIn(min = ShopSpacing.touchTarget)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onClick() }),
        enabled = enabled,
        selected = selected,
        leadingAccessory = leading,
        trailingAccessory = trailing,
    )
}
