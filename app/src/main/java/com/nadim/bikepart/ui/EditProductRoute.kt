package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable

@Composable
internal fun EditProductRoute(context: ProductFormRouteContext) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = "Edit details",
                showBack = true,
                enabled = !context.busy
            )
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            ProductFormRoute(context, ProductFormMode.Edit)
        }
    }
}
