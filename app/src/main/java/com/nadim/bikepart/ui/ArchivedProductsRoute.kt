package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun ArchivedProductsRoute(
    shop: ShopSnapshot,
    loadPhoto: suspend (com.nadim.bikepart.shop.PhotoReference) -> ByteArray?,
    onOpenProduct: (String) -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = stringResource(R.string.title_archived_products), showBack = true) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            ArchivedProducts(shop, loadPhoto, onOpenProduct)
        }
    }
}
