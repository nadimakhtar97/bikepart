package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.CorrectionKind
import com.nadim.bikepart.shop.Product
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun ProductDetailRoute(
    shop: ShopSnapshot,
    product: Product?,
    busy: Boolean,
    error: ShopError?,
    loadPhoto: PhotoLoader,
    onEdit: () -> Unit,
    onManageFields: () -> Unit,
    onCopy: () -> Unit,
    onMakeTemplate: () -> Unit,
    onCorrection: (CorrectionKind) -> Unit,
    onArchive: () -> Unit,
    onReceive: () -> Unit,
    onLabels: () -> Unit,
    onChange: () -> Unit,
    onThresholdSave: (String) -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = stringResource(R.string.title_product_details), showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            if (product != null) {
                ProductDetails(
                    shop = shop,
                    product = product,
                    onEdit = onEdit,
                    onManage = onManageFields,
                    loadPhoto = loadPhoto,
                    busy = busy,
                    onCopy = onCopy,
                    onTemplate = onMakeTemplate,
                    onCorrection = onCorrection,
                    onArchive = onArchive,
                    onReceive = onReceive,
                    onLabels = onLabels,
                )
                if (!product.archived) {
                    ThresholdForm(product, busy, error, onChange = onChange, save = onThresholdSave)
                }
                SaleStockHistory(shop, product.id)
                ArrivalHistory(shop.arrivals.filter { it.productId == product.id })
            }
        }
    }
}
