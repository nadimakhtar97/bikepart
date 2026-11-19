package com.nadim.bikepart.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.Cart
import com.nadim.bikepart.shop.Sale
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun CheckoutRoute(
    shop: ShopSnapshot,
    cart: Cart?,
    sale: Sale?,
    busy: Boolean,
    error: ShopError?,
    onChange: () -> Unit,
    onEdit: (Cart) -> Unit,
    onComplete: (Cart, String) -> Unit,
    onOpenSales: () -> Unit,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Cash checkout", showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            when {
                sale != null -> SaleReceipt(sale, onOpenSales)
                cart != null -> CashCheckout(
                    shop, cart, busy, error, onChange,
                    edit = { onEdit(cart) },
                    complete = { cash -> onComplete(cart, cash) },
                )

                else -> Text(stringResource(R.string.cart_unavailable))
            }
        }
    }
}
