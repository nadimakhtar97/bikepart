package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun MoreScreen(shop: ShopSnapshot, appearance: Appearance, busy: Boolean, onAppearance: (Appearance) -> Unit,
    customization: com.nadim.bikepart.ui.theme.ShopThemeCustomization, onBrand: (com.nadim.bikepart.ui.theme.ShopBrand) -> Unit,
    onCategories: () -> Unit, onFindingLookups: () -> Unit, onArchived: () -> Unit, onSales: () -> Unit, onTemplates: () -> Unit, onBackups: () -> Unit, updates: @Composable () -> Unit) {
    ShopPageHeader(stringResource(R.string.more_header), "Catalog, appearance and recovery.")
    ShopSectionHeader(stringResource(R.string.more_appearance))
    ShopGroup(spacing = ShopSpacing.small) {
        Column(Modifier.selectableGroup()) {
            Appearance.entries.forEach { mode ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Row(Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)
                    .selectable(appearance == mode, interactionSource = interaction, indication = null,
                        enabled = !busy, role = Role.RadioButton, onClick = { onAppearance(mode) })
                    .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small) else Modifier)
                    .padding(horizontal = ShopSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    ShopAppearanceIndicator(selected = appearance == mode, enabled = !busy, interaction = interaction)
                    Text(mode.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = ShopSpacing.medium))
                }
            }
        }
        Text(stringResource(R.string.more_appearance_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ShopSectionHeader("Choose brand theme")
    ShopGroup(spacing = ShopSpacing.small) {
        Column(Modifier.selectableGroup()) {
            com.nadim.bikepart.ui.theme.ShopBrand.entries.forEach { brand ->
                Row(Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)
                    .selectable(customization.brand == brand, enabled = !busy, role = Role.RadioButton,
                        onClick = { onBrand(brand) }).padding(horizontal = ShopSpacing.small),
                    verticalAlignment = Alignment.CenterVertically) {
                    ShopAppearanceIndicator(selected = customization.brand == brand, enabled = !busy,
                        interaction = remember { MutableInteractionSource() })
                    Text(brand.label, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = ShopSpacing.medium))
                }
            }
        }
    }
    updates()
    ShopSectionHeader(stringResource(R.string.more_shop))
    ShopListGroup {
        ShopListRow(stringResource(R.string.more_sales_history), supporting = "Completed cash sales and receipts", icon = R.drawable.ic_receipt,
            enabled = !busy, onClick = onSales)
        ShopListRow(stringResource(R.string.more_product_templates), supporting = "Reusable catalog details", icon = R.drawable.ic_template,
            enabled = !busy, onClick = onTemplates)
        ShopListRow(stringResource(R.string.more_archived_products), supporting = "Retained details and history", icon = R.drawable.ic_archive,
            enabled = !busy, onClick = onArchived)
    }
    ShopSectionHeader(stringResource(R.string.more_backup_recovery))
    ShopListGroup {
        ShopListRow(stringResource(R.string.more_shop_backups), supporting = "Drive, local copies and phone recovery", icon = R.drawable.ic_cloud,
            enabled = !busy, onClick = onBackups)
    }
    ShopSectionHeader(stringResource(R.string.more_catalog))
    ShopListGroup {
        ShopListRow(stringResource(R.string.more_edit_categories), supporting = "${shop.categories.size} categories", icon = R.drawable.ic_category,
            enabled = !busy, onClick = onCategories)
        ShopListRow(stringResource(R.string.more_manage_fitment), supporting = "${shop.makes.size} makes · ${shop.locations.size} locations",
            icon = R.drawable.ic_two_wheeler, enabled = !busy, onClick = onFindingLookups)
    }
}
