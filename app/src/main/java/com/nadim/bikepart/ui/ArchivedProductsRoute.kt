package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun ArchivedProductsRoute(
    shop: ShopSnapshot,
    loadPhoto: suspend (com.nadim.bikepart.shop.PhotoReference) -> ByteArray?,
    onOpenProduct: (String) -> Unit,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Archived products", showBack = true) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            ArchivedProducts(shop, loadPhoto, onOpenProduct)
        }
    }
}
