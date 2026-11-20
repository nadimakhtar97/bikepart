package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.ui.theme.ShopBrand
import com.nadim.bikepart.ui.theme.ShopThemeCustomization

@Composable
internal fun MoreRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    customization: ShopThemeCustomization,
    onAppearance: (com.nadim.bikepart.shop.Appearance) -> Unit,
    onBrand: (ShopBrand) -> Unit,
    onAccent: (Boolean) -> Unit,
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
                appearance = shop.appearance,
                busy = busy,
                onAppearance = onAppearance,
                customization = customization,
                onBrand = onBrand,
                onAccent = onAccent,
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
