package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun CategoriesRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Categories", showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            CategorySettings(shop = shop, busy = busy, onOpen = onOpen, onRename = onRename)
        }
    }
}
