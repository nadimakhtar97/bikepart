package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun SalesRoute(shop: ShopSnapshot, onOpenReceipt: (String) -> Unit) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = stringResource(R.string.title_sales_history), showBack = true) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            SalesHistory(shop, onOpenReceipt)
        }
    }
}
