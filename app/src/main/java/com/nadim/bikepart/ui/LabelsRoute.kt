package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.ShopViewModel
import com.nadim.bikepart.shop.Product
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.printing.LabelUsbAccess

@Composable
internal fun LabelsRoute(
    shop: ShopSnapshot,
    product: Product?,
    busy: Boolean,
    error: ShopError?,
    labelUsb: (android.content.Context, () -> Boolean) -> LabelUsbAccess,
    model: ShopViewModel,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Product labels", showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            if (product != null) {
                LabelPreview(
                    shop = shop,
                    product = product,
                    busy = busy,
                    error = error,
                    usbAccess = labelUsb,
                    onChange = { model.error.value = null },
                    save = { media, copies -> model.write({ setLabelPreferences(media, copies) }) },
                )
            }
        }
    }
}
