package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun FindingLookupsRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    onRetireMake: (String) -> Unit,
    onRetireModel: (String) -> Unit,
    onRetireLocation: (String) -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = "Fitment and locations",
                showBack = true,
                enabled = !busy
            )
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            FindingLookupSettings(
                shop = shop,
                busy = busy,
                retireMake = onRetireMake,
                retireModel = onRetireModel,
                retireLocation = onRetireLocation,
            )
        }
    }
}
