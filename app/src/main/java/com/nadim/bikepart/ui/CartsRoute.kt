package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun CartsRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    onNewCart: () -> Unit,
    onOpenCart: (String) -> Unit,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Carts", showBack = false, enabled = !busy) },
        floatingActionButton = {
            if (!busy) {
                ExtendedFloatingActionButton(
                    onClick = onNewCart,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text("New sale") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
