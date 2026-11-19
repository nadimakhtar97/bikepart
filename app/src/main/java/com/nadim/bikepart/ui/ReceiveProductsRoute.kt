package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
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
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Receive stock", showBack = true, enabled = !busy) },
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
