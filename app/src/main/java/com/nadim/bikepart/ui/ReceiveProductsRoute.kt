package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun ReceiveProductsRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    loadPhoto: PhotoLoader,
    onScan: () -> Unit,
    onSelect: (String) -> Unit,
    onUseTemplate: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = stringResource(R.string.title_receive_stock), showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            ScanManualPicker(
                shop, false, busy, loadPhoto,
                onScan = onScan,
                select = onSelect,
                template = onUseTemplate,
                create = onCreate,
            )
        }
    }
}
