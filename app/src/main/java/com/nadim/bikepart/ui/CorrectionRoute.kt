package com.nadim.bikepart.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.microsoft.fluentui.compose.Scaffold
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
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = stringResource(kind.label), showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(modifier = Modifier.imePadding(), contentPadding = padding) {
            if (product != null) key(operationId) {
                StockCorrectionForm(product, kind, busy, error, onChange = onChange, save = save)
            }
        }
    }
}
