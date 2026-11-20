package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.CartDraft
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun CartRoute(
    shop: ShopSnapshot,
    draft: CartDraft?,
    busy: Boolean,
    error: ShopError?,
    edit: (String?, String?, String?) -> Unit,
    pause: () -> Unit,
    addProduct: () -> Unit,
    retry: () -> Unit,
    reload: () -> Unit,
    checkout: () -> Unit,
    removeProduct: (String) -> Unit,
    discard: () -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = draft?.cart?.name ?: "Unfinished bill",
                showBack = true,
                enabled = !busy
            )
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            CartEditor(
                shop = shop,
                draft = draft,
                busy = busy,
                error = error,
                edit = edit,
                pause = pause,
                add = addProduct,
                retry = retry,
                reload = reload,
                checkout = checkout,
                remove = removeProduct,
                discard = discard,
            )
        }
    }
}
