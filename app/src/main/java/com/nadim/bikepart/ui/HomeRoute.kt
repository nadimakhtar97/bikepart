package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.shop.StockHealth

@Composable
internal fun HomeRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    onAdd: () -> Unit,
    onStock: (StockHealth?) -> Unit,
    onNewSale: () -> Unit,
    onCarts: () -> Unit,
    onReceive: () -> Unit,
    onSales: () -> Unit,
    onSale: (String) -> Unit,
    searchBar: @Composable RowScope.() -> Unit = {},
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = "Home",
                showBack = false,
                enabled = !busy,
                searchMode = true,
                searchBar = searchBar,
            )
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            HomeScreen(
                shop = shop,
                onAdd = onAdd,
                onStock = onStock,
                onNewSale = onNewSale,
                onCarts = onCarts,
                onReceive = onReceive,
                onSales = onSales,
                onSale = onSale,
                busy = busy,
            )
        }
    }
}
