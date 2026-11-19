package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.CorrectionKind
import com.nadim.bikepart.shop.Product

@Composable
internal fun CorrectionRoute(
    product: Product?,
    operationId: String,
    kind: CorrectionKind,
    busy: Boolean,
    error: ShopError?,
    onChange: () -> Unit,
    save: (String, String) -> Unit,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = kind.label, showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            if (product != null) key(operationId) {
                StockCorrectionForm(product, kind, busy, error, onChange = onChange, save = save)
            }
        }
    }
}
