package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold

@Composable
internal fun EditProductRoute(context: ProductFormRouteContext) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = stringResource(R.string.title_edit_details),
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
