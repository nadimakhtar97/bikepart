package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun MoreScreen(
    shop: ShopSnapshot,
    busy: Boolean,
    onOpenAppearance: () -> Unit,
    onDeveloperOption: () -> Unit,
    onCategories: () -> Unit,
    onFindingLookups: () -> Unit,
    onArchived: () -> Unit,
    onSales: () -> Unit,
    onTemplates: () -> Unit,
    onBackups: () -> Unit,
    updates: @Composable () -> Unit,
) {
    ShopListGroup {
        ShopListRow(
            title = stringResource(R.string.more_appearance),
            icon = R.drawable.ic_palette,
            enabled = !busy,
            onClick = onOpenAppearance,
            showChevron = true,
        )
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
        ShopListRow(
            stringResource(R.string.more_edit_categories),
            supporting = "${shop.categories.size} categories · shared fields and options",
            icon = R.drawable.ic_category,
            enabled = !busy, onClick = onCategories)
        ShopListRow(stringResource(R.string.more_manage_fitment), supporting = "${shop.makes.size} makes · ${shop.locations.size} locations",
            icon = R.drawable.ic_two_wheeler, enabled = !busy, onClick = onFindingLookups)
    }
    ShopListGroup {
        ShopListRow(
            title = stringResource(R.string.developer_option),
            icon = R.drawable.ic_settings,
            enabled = !busy,
            onClick = onDeveloperOption,
            showChevron = true,
        )
    }
}
