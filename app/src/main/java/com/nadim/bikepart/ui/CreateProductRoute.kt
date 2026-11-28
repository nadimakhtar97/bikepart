package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.CatalogDraft

@Composable
internal fun CreateProductRoute(context: ProductFormRouteContext, catalog: CatalogDraft?) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = stringResource(R.string.title_new_product),
                showBack = true,
                enabled = !context.busy
            )
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            ProductFormRoute(context, ProductFormMode.Create, catalog)
        }
    }
}
