package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import com.microsoft.fluentui.compose.Scaffold
import com.microsoft.fluentui.theme.token.controlTokens.FABSize
import com.microsoft.fluentui.theme.token.controlTokens.FABState
import com.microsoft.fluentui.tokenized.controls.FloatingActionButton as FluentFloatingActionButton
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.ui.theme.shopIconVector

@Composable
internal fun StockRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    stateHolder: SaveableStateHolder,
    session: Int,
    initialHealth: String?,
    initialQuery: String,
    loadPhoto: PhotoLoader,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onScan: () -> Unit,
    onProductAction: (com.nadim.bikepart.shop.Product, String) -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Stock", showBack = false, enabled = !busy) },
        floatingActionButton = {
            if (!busy) {
                FluentFloatingActionButton(
                    onClick = onAdd,
                    state = FABState.Expanded,
                    size = FABSize.Large,
                    icon = shopIconVector(R.drawable.ic_add),
                    text = "Add product",
                    modifier = Modifier.imePadding(),
                )
            }
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            stateHolder.SaveableStateProvider("stock:$session") {
                StockScreen(
                    shop = shop,
                    onAdd = onAdd,
                    onOpen = onOpen,
                    loadPhoto = loadPhoto,
                    initialHealth = initialHealth,
                    initialQuery = initialQuery,
                    busy = busy,
                    onScan = onScan,
                    onProductAction = onProductAction,
                )
            }
        }
    }
}
