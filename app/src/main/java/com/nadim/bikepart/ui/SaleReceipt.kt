package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.Sale
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.shop.indianNumber
import com.nadim.bikepart.ui.theme.ShopSpacing
import java.text.DateFormat
import java.util.Date


@Composable
internal fun SaleReceipt(sale: Sale, history: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = .08f).compositeOver(MaterialTheme.colorScheme.surface)) {
        Column(Modifier
            .fillMaxWidth()
            .padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sale_saved_on_this_phone), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.cash_sale_cart, sale.cartNumber), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(saleDate(sale.recordedAt), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.receipt, sale.id), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sale.customer.isNotEmpty()) Text(sale.customer, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.total, indianNumber(sale.totalRupees)), style = MaterialTheme.typography.headlineSmall)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.cash_received_2, indianNumber(sale.cashRupees)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.change, indianNumber(sale.changeRupees)), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.this_receipt_remains_available_offline_in_sales), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    ShopSectionHeader(stringResource(R.string.products), supporting = "${sale.lines.size} ${if (sale.lines.size == 1) "line" else "lines"}")
    ShopListGroup(showDividers = true) {
        sale.lines.forEachIndexed { index, line ->
            ShopListRow(
                line.title,
                supporting = listOf(line.sku, line.category, line.fitment, line.description)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                meta = stringResource(
                    R.string.pieces_2,
                    indianNumber(line.quantity),
                    indianNumber(line.quotedPriceRupees),
                    indianNumber(line.totalRupees)
                ),
                dividerAbove = index > 0,
                minHeight = 72.dp
            )
        }
    }
    TextButton(onClick = history, modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.more_sales_history)) }
}

@Composable
internal fun SaleStockHistory(shop: ShopSnapshot, productId: String) {
    val sales = shop.sales.filter { sale -> sale.lines.any { it.productId == productId } }
    if (sales.isEmpty()) return
    ShopSection(stringResource(R.string.sale_stock_history)) {
        ShopListGroup(showDividers = true) {
            sales.forEachIndexed { index, sale ->
                val line = sale.lines.single { it.productId == productId }
                ShopListRow(
                    stringResource(
                        R.string.cash_sale_cart_2,
                        sale.cartNumber,
                        saleDate(sale.recordedAt)
                    ),
                    supporting = stringResource(
                        R.string.removed_pieces_pieces,
                        indianNumber(line.quantity),
                        indianNumber(line.previousBalance),
                        indianNumber(line.resultingBalance)
                    ),
                    dividerAbove = index > 0,
                    minHeight = 64.dp
                )
            }
        }
    }
}

private fun saleDate(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
