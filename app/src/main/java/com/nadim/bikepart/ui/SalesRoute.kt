package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun SalesRoute(shop: ShopSnapshot, onOpenReceipt: (String) -> Unit) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Sales history", showBack = true) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            SalesHistory(shop, onOpenReceipt)
        }
    }
}
