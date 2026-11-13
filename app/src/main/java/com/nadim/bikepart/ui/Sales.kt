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

@Composable
internal fun CashCheckout(shop: ShopSnapshot, cart: Cart, busy: Boolean, error: ShopError?,
    onChange: () -> Unit, edit: () -> Unit, complete: (String) -> Unit) {
    var cash by rememberSaveable(cart.id) { mutableStateOf("") }
    val errorView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null) errorView.bringIntoView() }
    ShopPageHeader(cart.name, "Review the quoted lines, then record the cash received.")
    ShopGroup(spacing = ShopSpacing.small) {
        if (cart.customer.isNotEmpty()) Text(cart.customer, style = MaterialTheme.typography.titleMedium)
        cart.lines.forEach { line ->
            val product = shop.products.single { it.id == line.productId }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(product.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("${indianNumber(line.quantity)} pieces × ₹${indianNumber(line.quotedPriceRupees)} = ₹${indianNumber(line.totalRupees)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Cart total: ₹${indianNumber(cart.totalRupees)}", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    ShopGroup("Cash payment") {
        OutlinedTextField(cash, { cash = it; onChange() }, enabled = !busy,
            label = { Text("Cash received (₹)") }, singleLine = true,
            isError = error?.field == ProductField.CashReceived, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { cash = cart.totalRupees.toString(); onChange() }, enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Exact amount") }
        val tender = cash.trim().takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull()
        Text(if (tender != null && tender >= cart.totalRupees) "Change: ₹${indianNumber(Math.subtractExact(tender, cart.totalRupees))}" else "Enter enough cash to calculate change.",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium)
        if (error != null) Column(Modifier.bringIntoViewRequester(errorView)) { FormError(error.message) }
    }
    ShopPrimaryButton(if (busy) "Saving sale…" else "Complete cash sale", enabled = !busy, onClick = { complete(cash) })
    TextButton(onClick = edit, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Edit cart") }
    Text("The whole sale is saved on this phone only when every line has enough stock.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SalesHistory(shop: ShopSnapshot, open: (String) -> Unit) {
    Text("${shop.sales.size} completed cash ${if (shop.sales.size == 1) "sale" else "sales"}",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (shop.sales.isEmpty()) {
        ShopEmptyState("No completed cash sales yet.", "Completed sales and their receipts appear here after cash checkout.", R.drawable.ic_carts)
        return
    }
    ShopSectionHeader("Receipts")
    ShopGroup(spacing = ShopSpacing.small) {
        shop.sales.forEach { sale ->
            ShopListRow("Cart ${sale.cartNumber}",
                supporting = listOfNotNull(sale.customer.ifBlank { null }, saleDate(sale.recordedAt)).joinToString(" · "),
                meta = "₹${indianNumber(sale.totalRupees)}", status = { ShopStatusChip("Paid", ShopStatusTone.Success) },
                actionLabel = "Open receipt · Cart ${sale.cartNumber}", onClick = { open(sale.cartId) })
        }
    }
}

@Composable
internal fun SaleReceipt(sale: Sale, history: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = .08f).compositeOver(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sale saved on this phone", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text("Cash sale · Cart ${sale.cartNumber}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(saleDate(sale.recordedAt), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Receipt: ${sale.id}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (sale.customer.isNotEmpty()) Text(sale.customer, style = MaterialTheme.typography.titleMedium)
            Text("Total: ₹${indianNumber(sale.totalRupees)}", style = MaterialTheme.typography.headlineSmall)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Cash received: ₹${indianNumber(sale.cashRupees)}", style = MaterialTheme.typography.bodyMedium)
                Text("Change: ₹${indianNumber(sale.changeRupees)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text("This receipt remains available offline in Sales history.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    ShopSectionHeader("Products", supporting = "${sale.lines.size} ${if (sale.lines.size == 1) "line" else "lines"}")
    ShopGroup(spacing = ShopSpacing.small) {
        sale.lines.forEach { line ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line.title, style = MaterialTheme.typography.titleMedium, maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(listOf(line.sku, line.category).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (line.fitment.isNotBlank()) Text(line.fitment, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (line.description.isNotEmpty()) Text(line.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${indianNumber(line.quantity)} pieces × ₹${indianNumber(line.quotedPriceRupees)} = ₹${indianNumber(line.totalRupees)}",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    TextButton(onClick = history, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Sales history") }
}

@Composable
internal fun SaleStockHistory(shop: ShopSnapshot, productId: String) {
    val sales = shop.sales.filter { sale -> sale.lines.any { it.productId == productId } }
    if (sales.isEmpty()) return
    ShopSection("Sale stock history") {
        sales.forEach { sale ->
            val line = sale.lines.single { it.productId == productId }
            Text("Cash sale · Cart ${sale.cartNumber} · ${saleDate(sale.recordedAt)}", style = MaterialTheme.typography.titleMedium)
            Text("Removed ${indianNumber(line.quantity)} pieces · ${indianNumber(line.previousBalance)} → ${indianNumber(line.resultingBalance)} pieces")
        }
    }
}

private fun saleDate(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
