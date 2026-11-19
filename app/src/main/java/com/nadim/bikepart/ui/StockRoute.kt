package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun StockRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    stateHolder: SaveableStateHolder,
    session: Int,
    initialHealth: String?,
    loadPhoto: PhotoLoader,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onScan: () -> Unit,
    onProductAction: (com.nadim.bikepart.shop.Product, String) -> Unit,
) {
    com.microsoft.fluentui.compose.Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Stock", showBack = false, enabled = !busy) },
        floatingActionButton = {
            if (!busy) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text("Add product") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    busy = busy,
                    onScan = onScan,
                    onProductAction = onProductAction,
                )
            }
        }
    }
}
