package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.microsoft.fluentui.compose.Scaffold
import com.microsoft.fluentui.theme.token.controlTokens.FABSize
import com.microsoft.fluentui.theme.token.controlTokens.FABState
import com.microsoft.fluentui.tokenized.controls.FloatingActionButton as FluentFloatingActionButton
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.ui.theme.shopIconVector

@Composable
internal fun CartsRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    onNewCart: () -> Unit,
    onOpenCart: (String) -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Carts", showBack = false, enabled = !busy) },
        floatingActionButton = {
            if (!busy) {
                FluentFloatingActionButton(
                    onClick = onNewCart,
                    state = FABState.Expanded,
                    size = FABSize.Large,
                    icon = shopIconVector(R.drawable.ic_add),
                    text = "New sale",
                    modifier = Modifier.imePadding(),
                )
            }
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            CartList(shop, busy, onNewCart, onOpenCart)
        }
    }
}
