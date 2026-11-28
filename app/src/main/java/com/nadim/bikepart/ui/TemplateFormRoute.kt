package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.CatalogDraft

@Composable
internal fun TemplateFormRoute(
    context: ProductFormRouteContext,
    catalog: CatalogDraft?,
    editing: Boolean,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = if (editing) stringResource(R.string.title_edit_template) else stringResource(R.string.title_new_template),
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
