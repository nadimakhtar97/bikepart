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
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.Appearance
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun MoreScreen(shop: ShopSnapshot, appearance: Appearance, busy: Boolean, onAppearance: (Appearance) -> Unit,
    customization: com.nadim.bikepart.ui.theme.ShopThemeCustomization, onBrand: (com.nadim.bikepart.ui.theme.ShopBrand) -> Unit, onAccent: (Boolean) -> Unit,
    onCategories: () -> Unit, onFindingLookups: () -> Unit, onArchived: () -> Unit, onSales: () -> Unit, onTemplates: () -> Unit, onBackups: () -> Unit, updates: @Composable () -> Unit) {
    ShopPageHeader(stringResource(R.string.more_header), "Catalog, appearance and recovery.")
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
    ShopSectionHeader(stringResource(R.string.more_appearance))
    Column(Modifier.selectableGroup()) {
        ShopListGroup(showDividers = true) {
            Appearance.entries.forEachIndexed { index, mode ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                ShopListRow(
                    title = mode.name,
                    modifier = Modifier
                        .selectable(
                            selected = appearance == mode,
                            interactionSource = interaction,
                            indication = null,
                            enabled = !busy,
                            role = Role.RadioButton,
                            onClick = { onAppearance(mode) },
                        )
                        .then(
                            if (focused) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.shapes.small
                            ) else Modifier
                        ),
                    leading = {
                        ShopAppearanceIndicator(
                            selected = appearance == mode,
                            enabled = !busy,
                            interaction = interaction
                        )
                    },
                    enabled = !busy,
                    dividerAbove = index > 0,
                )
            }
        }
        Text(stringResource(R.string.more_appearance_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ShopSectionHeader(stringResource(R.string.more_choose_brand_theme))
    Column(Modifier.selectableGroup()) {
        ShopListGroup(showDividers = true) {
            com.nadim.bikepart.ui.theme.ShopBrand.entries.forEachIndexed { index, brand ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                ShopListRow(
                    title = brand.label,
                    modifier = Modifier
                        .selectable(
                            selected = customization.brand == brand,
                            enabled = !busy,
                            role = Role.RadioButton,
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onBrand(brand) },
                        )
                        .then(
                            if (focused) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.shapes.small
                            ) else Modifier
                        ),
                    leading = {
                        ShopAppearanceIndicator(
                            selected = customization.brand == brand,
                            enabled = !busy,
                            interaction = interaction
                        )
                    },
                    enabled = !busy,
                    dividerAbove = index > 0,
                )
            }
        }
    }
    updates()
    ShopSectionHeader(stringResource(R.string.more_shop))
    ShopListGroup(showDividers = true, shape = MaterialTheme.shapes.large) {
        ShopListRow(
            stringResource(R.string.more_sales_history),
            supporting = "Completed cash sales and receipts",
            enabled = !busy,
            onClick = onSales,
            showChevron = true
        )
        ShopListRow(
            stringResource(R.string.more_product_templates),
            supporting = "Reusable catalog details",
            enabled = !busy,
            onClick = onTemplates,
            showChevron = true,
            dividerAbove = true
        )
        ShopListRow(
            stringResource(R.string.more_archived_products),
            supporting = "Retained details and history",
            enabled = !busy,
            onClick = onArchived,
            showChevron = true,
            dividerAbove = true
        )
    }
    ShopSectionHeader(stringResource(R.string.more_backup_recovery))
    ShopListGroup(showDividers = true, shape = MaterialTheme.shapes.large) {
        ShopListRow(
            stringResource(R.string.more_shop_backups),
            supporting = "Drive, local copies and phone recovery",
            enabled = !busy,
            onClick = onBackups,
            showChevron = true
        )
    }
    ShopSectionHeader(stringResource(R.string.more_catalog))
    ShopListGroup(showDividers = true, shape = MaterialTheme.shapes.large) {
        ShopListRow(
            stringResource(R.string.more_edit_categories),
            supporting = "${shop.categories.size} categories · shared fields and options",
            enabled = !busy, onClick = onCategories, showChevron = true
        )
        ShopListRow(stringResource(R.string.more_manage_fitment), supporting = "${shop.makes.size} makes · ${shop.locations.size} locations",
            enabled = !busy,
            onClick = onFindingLookups,
            showChevron = true,
            dividerAbove = true
        )
    }
}
