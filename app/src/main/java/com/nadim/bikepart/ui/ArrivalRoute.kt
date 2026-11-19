package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.Product

@Composable
internal fun ArrivalRoute(
    product: Product?,
    operationId: String,
    busy: Boolean,
    error: ShopError?,
    onChange: () -> Unit,
    save: (String, String) -> Unit,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Receive stock", showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            if (product != null && !product.archived) key(operationId) {
                StockArrivalForm(product, busy, error, onChange = onChange, save = save)
            }
        }
    }
}
