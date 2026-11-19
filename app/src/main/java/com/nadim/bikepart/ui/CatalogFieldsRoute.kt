package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.Product
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun CatalogFieldsRoute(
    shop: ShopSnapshot,
    product: Product?,
    busy: Boolean,
    error: ShopError?,
    onChange: () -> Unit,
    actions: CatalogActions,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Product fields", showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            if (product != null) {
                CatalogManagement(shop, product, busy, error, onChange, actions)
            }
        }
    }
}
