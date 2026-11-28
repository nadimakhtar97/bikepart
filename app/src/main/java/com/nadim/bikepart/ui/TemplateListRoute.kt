package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun TemplateListRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onUse: (String) -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = stringResource(R.string.title_product_templates), showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            TemplateList(
                shop = shop,
                busy = busy,
                onCreate = onCreate,
                onOpen = onOpen,
                onUse = onUse,
            )
        }
    }
}
