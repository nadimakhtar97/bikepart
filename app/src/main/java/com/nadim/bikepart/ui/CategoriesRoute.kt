package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun CategoriesRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onCreate: (String) -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = stringResource(R.string.title_categories), showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            CategorySettings(
                shop = shop,
                busy = busy,
                onOpen = onOpen,
                onRename = onRename,
                onCreate = onCreate
            )
        }
    }
}
