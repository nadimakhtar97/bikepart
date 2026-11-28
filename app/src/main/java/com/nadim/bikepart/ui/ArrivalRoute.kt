package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.Product

@Composable
internal fun ArrivalRoute(
    product: Product?,
    operationId: String,
    busy: Boolean,
    error: ShopError?,
    onChange: () -> Unit,
    save: (String, String, String?) -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = stringResource(R.string.title_receive_stock), showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            if (product != null && !product.archived) key(operationId) {
                StockArrivalForm(product, busy, error, onChange = onChange, save = save)
            }
        }
    }
}
