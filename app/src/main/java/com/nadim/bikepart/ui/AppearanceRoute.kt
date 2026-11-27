package com.nadim.bikepart.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.Appearance
import com.nadim.bikepart.ui.theme.ShopBrand
import com.nadim.bikepart.ui.theme.ShopSpacing
import com.nadim.bikepart.ui.theme.ShopThemeCustomization

@Composable
internal fun AppearanceRoute(
    appearance: Appearance,
    busy: Boolean,
    customization: ShopThemeCustomization,
    onAppearance: (Appearance) -> Unit,
    onBrand: (ShopBrand) -> Unit,
    onAccent: (Boolean) -> Unit,
) {
    val title = stringResource(R.string.more_appearance)
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = title, showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            AppearanceSettings(appearance, busy, customization, onAppearance, onBrand, onAccent)
        }
    }
}

@Composable
private fun AppearanceSettings(
    appearance: Appearance,
    busy: Boolean,
    customization: ShopThemeCustomization,
    onAppearance: (Appearance) -> Unit,
    onBrand: (ShopBrand) -> Unit,
    onAccent: (Boolean) -> Unit,
) {
    ShopSectionHeader(stringResource(R.string.more_theme_mode))
    ShopGroup(spacing = ShopSpacing.small) {
        Column(Modifier.selectableGroup()) {
            Appearance.entries.forEach { mode ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Row(Modifier
                    .fillMaxWidth()
                    .heightIn(min = ShopSpacing.touchTarget)
                    .selectable(
                        appearance == mode, interactionSource = interaction, indication = null,
                        enabled = !busy, role = Role.RadioButton, onClick = { onAppearance(mode) })
                    .then(
                        if (focused) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.small
                        ) else Modifier
                    )
                    .padding(horizontal = ShopSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    ShopAppearanceIndicator(selected = appearance == mode, enabled = !busy, interaction = interaction)
                    Text(mode.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = ShopSpacing.medium))
                }
            }
        }
        Text(stringResource(R.string.more_appearance_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ShopGroup(spacing = ShopSpacing.small) {
        val accentInteraction = remember { MutableInteractionSource() }
        val accentFocused by accentInteraction.collectIsFocusedAsState()
        Row(Modifier
            .fillMaxWidth()
            .heightIn(min = ShopSpacing.touchTarget)
            .toggleable(
                value = customization.accent, enabled = !busy, role = Role.Switch,
                interactionSource = accentInteraction, indication = null, onValueChange = onAccent
            )
            .then(
                if (accentFocused) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.small
                ) else Modifier
            )
            .padding(horizontal = ShopSpacing.small), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShopSpacing.medium)) {
            Text(stringResource(R.string.more_accent), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            com.microsoft.fluentui.tokenized.controls.ToggleSwitch(
                modifier = Modifier
                    .focusProperties { canFocus = false }
                    .clearAndSetSemantics { }, checkedState = customization.accent,
                enabledSwitch = !busy, onValueChange = onAccent)
        }
    }
    ShopSectionHeader(stringResource(R.string.more_choose_brand_theme))
    ShopGroup(spacing = ShopSpacing.small) {
        Column(Modifier.selectableGroup()) {
            ShopBrand.entries.forEach { brand ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Row(Modifier
                    .fillMaxWidth()
                    .heightIn(min = ShopSpacing.touchTarget)
                    .selectable(
                        customization.brand == brand,
                        enabled = !busy,
                        role = Role.RadioButton,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onBrand(brand) })
                    .then(
                        if (focused) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.small
                        ) else Modifier
                    )
                    .padding(horizontal = ShopSpacing.small),
                    verticalAlignment = Alignment.CenterVertically) {
                    ShopAppearanceIndicator(selected = customization.brand == brand, enabled = !busy,
                        interaction = interaction)
                    Text(brand.label, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = ShopSpacing.medium))
                }
            }
        }
    }
}
