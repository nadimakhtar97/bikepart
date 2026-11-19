package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.nadim.bikepart.shop.CatalogDraft

@Composable
internal fun TemplateFormRoute(
    context: ProductFormRouteContext,
    catalog: CatalogDraft?,
    editing: Boolean,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = if (editing) "Edit template" else "New template",
                showBack = true,
                enabled = !context.busy,
            )
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            ProductFormRoute(context, ProductFormMode.Template, catalog)
        }
    }
}
