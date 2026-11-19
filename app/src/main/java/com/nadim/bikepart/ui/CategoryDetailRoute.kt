package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun CategoryDetailRoute(
    shop: ShopSnapshot,
    categoryId: String,
    busy: Boolean,
    error: ShopError?,
    onChange: () -> Unit,
    actions: CategoryFieldActions,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = shop.categories.firstOrNull { it.id == categoryId }?.name
                    ?: "Category fields", showBack = true, enabled = !busy
            )
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            CategoryFieldManagement(
                shop = shop, categoryId = categoryId, busy = busy, error = error,
                onChange = onChange, actions = actions,
            )
        }
    }
}
