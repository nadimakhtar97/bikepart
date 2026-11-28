package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
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
    onCreateMake: (String) -> Unit,
    onCreateModel: (String, String) -> Unit,
    onCreateLocation: (String) -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = stringResource(R.string.title_fitment_locations),
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
                createMake = onCreateMake,
                createModel = onCreateModel,
                createLocation = onCreateLocation,
            )
        }
    }
}
