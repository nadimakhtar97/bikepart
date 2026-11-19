package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.nadim.bikepart.CartDraft
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun CartProductsRoute(
    shop: ShopSnapshot,
    draft: CartDraft?,
    busy: Boolean,
    error: ShopError?,
    loadPhoto: PhotoLoader,
    onScan: () -> Unit,
    onChange: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Add to cart", showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            draft?.let {
                CartProductPicker(
                    shop = shop,
                    cart = shop.carts.find { cart -> cart.id == it.cart.id } ?: it.cart,
                    busy = busy,
                    error = error,
                    loadPhoto = loadPhoto,
                    onScan = onScan,
                    onChange = onChange,
                    add = onAdd,
                )
            }
        }
    }
}
