package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.shop.StockHealth
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun HomeRoute(
    shop: ShopSnapshot,
    busy: Boolean,
    onAdd: () -> Unit,
    onStock: (StockHealth?) -> Unit,
    onNewSale: () -> Unit,
    onCarts: () -> Unit,
    onReceive: () -> Unit,
    onSales: () -> Unit,
    onSale: (String) -> Unit,
    searchBar: @Composable RowScope.() -> Unit = {},
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = stringResource(R.string.dest_home),
                showBack = false,
                enabled = !busy,
                searchMode = true,
                searchBar = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(
                            Modifier.widthIn(max = ShopSpacing.formWidth)
                                .fillMaxWidth()
                                .padding(
                                    start = ShopSpacing.content,
                                    end = ShopSpacing.content,
                                    bottom = ShopSpacing.small,
                                ),
                            content = searchBar,
                        )
                    }
                },
            )
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            HomeScreen(
                shop = shop,
                onAdd = onAdd,
                onStock = onStock,
                onNewSale = onNewSale,
                onCarts = onCarts,
                onReceive = onReceive,
                onSales = onSales,
                onSale = onSale,
                busy = busy,
            )
        }
    }
}
