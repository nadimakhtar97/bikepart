package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
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
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = shop.categories.firstOrNull { it.id == categoryId }?.name
                    ?: stringResource(R.string.category_fields), showBack = true, enabled = !busy
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
