package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing
import java.text.DateFormat
import java.util.Date
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


@Composable
internal fun SaleReceipt(sale: Sale, history: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = .08f).compositeOver(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    ShopGroup(spacing = ShopSpacing.small) {
        sale.lines.forEach { line ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line.title, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(line.sku, line.category).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (line.fitment.isNotBlank()) Text(line.fitment, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (line.description.isNotEmpty()) Text(line.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.pieces_2, indianNumber(line.quantity), indianNumber(line.quotedPriceRupees), indianNumber(line.totalRupees)),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    TextButton(onClick = history, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.more_sales_history)) }
}

@Composable
internal fun SaleStockHistory(shop: ShopSnapshot, productId: String) {
    val sales = shop.sales.filter { sale -> sale.lines.any { it.productId == productId } }
    if (sales.isEmpty()) return
    ShopSection(stringResource(R.string.sale_stock_history)) {
        sales.forEach { sale ->
            val line = sale.lines.single { it.productId == productId }
            Text(stringResource(R.string.cash_sale_cart_2, sale.cartNumber, saleDate(sale.recordedAt)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.removed_pieces_pieces, indianNumber(line.quantity), indianNumber(line.previousBalance), indianNumber(line.resultingBalance)))
        }
    }
}

private fun saleDate(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
