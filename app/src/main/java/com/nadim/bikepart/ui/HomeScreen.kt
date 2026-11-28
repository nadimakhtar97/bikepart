package com.nadim.bikepart.ui


import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.*


@Composable
internal fun HomeScreen(shop: ShopSnapshot, onAdd: () -> Unit, onStock: (StockHealth?) -> Unit, onNewSale: () -> Unit,
    onCarts: () -> Unit, onReceive: () -> Unit, onSales: () -> Unit, onSale: (String) -> Unit, busy: Boolean) {
    val activeProducts = shop.products.filterNot { it.archived }
    val lowStock = activeProducts.count { it.stockHealth == StockHealth.LowStock }
    val outOfStock = activeProducts.count { it.stockHealth == StockHealth.OutOfStock }
    val recentSales = shop.sales.sortedByDescending { it.recordedAt }.take(3)
//    ShopPageHeader(stringResource(R.string.home_header), "Work offline. Your stock stays on this phone.")
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
            stringResource(R.string.home_empty_desc), R.drawable.ic_stock)
        else ShopEmptyState(stringResource(R.string.home_no_active_title), stringResource(R.string.home_no_active_desc), R.drawable.ic_stock)
    }
    if (activeProducts.isNotEmpty()) {
        ShopSectionHeader(stringResource(R.string.home_stock_attention),
            supporting = if (lowStock + outOfStock == 0) stringResource(R.string.home_nothing_needs_attention) else stringResource(R.string.home_counts_at_threshold))
        ShopListGroup {
            ShopListRow(pluralStringResource(R.plurals.low_stock_product_count, lowStock, lowStock),
                supporting = stringResource(R.string.home_low_stock_supporting),
                leading = { ShopStatusIconTile(R.drawable.ic_warning, ShopStatusTone.Warning) },
                onClick = { onStock(StockHealth.LowStock) })
            ShopListRow(pluralStringResource(R.plurals.out_of_stock_product_count, outOfStock, outOfStock),
                supporting = stringResource(R.string.home_out_of_stock_supporting),
                leading = { ShopStatusIconTile(R.drawable.ic_no_stock, ShopStatusTone.Danger) },
                onClick = { onStock(StockHealth.OutOfStock) })
        }
    }
    ShopSectionHeader(stringResource(R.string.home_recent_sales), actionLabel = if (shop.sales.isNotEmpty()) stringResource(R.string.home_view_all) else null, onAction = onSales)
    if (recentSales.isEmpty()) Text(stringResource(R.string.home_no_recent_sales), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    else ShopListGroup {
        recentSales.forEach { sale ->
            ShopListRow(stringResource(R.string.cart_3, sale.cartNumber),
                supporting = listOfNotNull(sale.customer.ifBlank { null }, stringResource(R.string.lines_2, sale.lines.size)).joinToString(" · "),
                meta = stringResource(R.string.rupee_amount, indianNumber(sale.totalRupees)), status = { ShopStatusChip(stringResource(R.string.shared_paid), ShopStatusTone.Success) },
                actionLabel = stringResource(R.string.open_receipt_cart, sale.cartNumber), onClick = { onSale(sale.cartId) })
        }
    }
}
