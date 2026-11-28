package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ProductTemplate
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun TemplateDetailRoute(
    shop: ShopSnapshot,
    template: ProductTemplate?,
    busy: Boolean,
    loadPhoto: PhotoLoader,
    onEdit: () -> Unit,
    onUse: () -> Unit,
    onRetire: () -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = template?.catalog?.title ?: stringResource(R.string.title_template_fallback),
                showBack = true,
                enabled = !busy
            )
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            if (template != null) {
                TemplateDetails(shop, template, busy, loadPhoto, onEdit, onUse, onRetire)
            }
        }
    }
}
