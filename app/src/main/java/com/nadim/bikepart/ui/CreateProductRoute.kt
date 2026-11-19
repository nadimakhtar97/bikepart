package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.nadim.bikepart.shop.CatalogDraft

@Composable
internal fun CreateProductRoute(context: ProductFormRouteContext, catalog: CatalogDraft?) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = "New product",
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
