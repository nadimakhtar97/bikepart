package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
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
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = stringResource(R.string.product_fields), showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            if (product != null) {
                CatalogManagement(shop, product, busy, error, onChange, actions)
            }
        }
    }
}
