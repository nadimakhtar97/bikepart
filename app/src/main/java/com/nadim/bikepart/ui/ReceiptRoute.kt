package com.nadim.bikepart.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.Sale

@Composable
internal fun ReceiptRoute(sale: Sale?, onOpenSales: () -> Unit) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Receipt", showBack = true) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            if (sale != null) {
                SaleReceipt(sale, onOpenSales)
            } else {
                Text(stringResource(R.string.receipt_unavailable))
            }
        }
    }
}
