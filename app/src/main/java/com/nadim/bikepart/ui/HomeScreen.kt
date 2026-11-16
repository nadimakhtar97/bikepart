package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun HomeScreen(shop: ShopSnapshot, onAdd: () -> Unit, onStock: (StockHealth?) -> Unit, onNewSale: () -> Unit,
    onCarts: () -> Unit, onReceive: () -> Unit, onSales: () -> Unit, onSale: (String) -> Unit, busy: Boolean) {
    val activeProducts = shop.products.filterNot { it.archived }
    val lowStock = activeProducts.count { it.stockHealth == StockHealth.LowStock }
    val outOfStock = activeProducts.count { it.stockHealth == StockHealth.OutOfStock }
    val recentSales = shop.sales.sortedByDescending { it.recordedAt }.take(3)
    ShopPageHeader(stringResource(R.string.home_header), "Work offline. Your stock stays on this phone.")
    ShopSectionHeader(stringResource(R.string.home_quick_actions))
    ShopTileGrid { tile ->
        ShopActionTile(stringResource(R.string.home_new_sale), R.drawable.ic_cart, tile, enabled = !busy, primary = true, onClick = onNewSale)
        ShopActionTile(stringResource(R.string.home_add_product), R.drawable.ic_add, tile, enabled = !busy, onClick = onAdd)
        ShopActionTile(stringResource(R.string.product_receive_stock), R.drawable.ic_inventory, tile, enabled = !busy, onClick = onReceive)
        ShopActionTile(stringResource(R.string.more_sales_history), R.drawable.ic_receipt, tile, enabled = !busy, onClick = onSales)
    }
    ShopSectionHeader(stringResource(R.string.home_shop_overview))
    ShopTileGrid { tile ->
        ShopStatCard(stringResource(R.string.cart_list_header), shop.carts.size.toString(), R.drawable.ic_pending,
            modifier = tile, enabled = !busy, onClick = onCarts)
        ShopStatCard(stringResource(R.string.home_active_products), activeProducts.size.toString(), R.drawable.ic_inventory,
            modifier = tile, enabled = !busy, onClick = { onStock(null) })
    }
    if (activeProducts.isEmpty()) {
        if (shop.products.isEmpty()) ShopEmptyState(stringResource(R.string.home_empty_title),
            "Record its opening stock and whole-rupee selling price.", R.drawable.ic_stock)
        else ShopEmptyState(stringResource(R.string.home_no_active_title), "Add a product for current work. Archived products and their history are available under More.", R.drawable.ic_stock)
    }
    if (activeProducts.isNotEmpty()) {
        ShopSectionHeader(stringResource(R.string.home_stock_attention),
            supporting = if (lowStock + outOfStock == 0) "Nothing needs attention today" else "Counts at or below your thresholds")
        ShopListGroup {
            ShopListRow("Low stock: $lowStock ${if (lowStock == 1) "product" else "products"}",
                supporting = "Positive stock at or below the set threshold",
                leading = { ShopStatusIconTile(R.drawable.ic_warning, ShopStatusTone.Warning) },
                onClick = { onStock(StockHealth.LowStock) })
            ShopListRow("Out of stock: $outOfStock ${if (outOfStock == 1) "product" else "products"}",
                supporting = "No pieces currently on hand",
                leading = { ShopStatusIconTile(R.drawable.ic_no_stock, ShopStatusTone.Danger) },
                onClick = { onStock(StockHealth.OutOfStock) })
        }
    }
    ShopSectionHeader(stringResource(R.string.home_recent_sales), actionLabel = if (shop.sales.isNotEmpty()) "View all" else null, onAction = onSales)
    if (recentSales.isEmpty()) Text(stringResource(R.string.home_no_recent_sales), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    else ShopListGroup {
        recentSales.forEach { sale ->
            ShopListRow("Cart ${sale.cartNumber}",
                supporting = listOfNotNull(sale.customer.ifBlank { null }, "${sale.lines.size} lines").joinToString(" · "),
                meta = "₹${indianNumber(sale.totalRupees)}", status = { ShopStatusChip(stringResource(R.string.shared_paid), ShopStatusTone.Success) },
                actionLabel = "Open receipt · Cart ${sale.cartNumber}", onClick = { onSale(sale.cartId) })
        }
    }
}
