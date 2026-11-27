package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun MoreRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    onOpenAppearance: () -> Unit,
    onCategories: () -> Unit,
    onFindingLookups: () -> Unit,
    onArchived: () -> Unit,
    onSales: () -> Unit,
    onTemplates: () -> Unit,
    onBackups: () -> Unit,
    updates: @Composable () -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "More", showBack = false, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            MoreScreen(
                shop = shop,
                busy = busy,
                onOpenAppearance = onOpenAppearance,
                onCategories = onCategories,
                onFindingLookups = onFindingLookups,
                onArchived = onArchived,
                onSales = onSales,
                onTemplates = onTemplates,
                onBackups = onBackups,
                updates = updates,
            )
        }
    }
}
