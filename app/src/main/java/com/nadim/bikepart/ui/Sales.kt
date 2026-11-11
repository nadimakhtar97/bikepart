package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
    ShopSection("${cart.name} · Cash checkout") {
        if (cart.customer.isNotEmpty()) Text(cart.customer, style = MaterialTheme.typography.titleMedium)
        cart.lines.forEach { line ->
            Text(shop.products.single { it.id == line.productId }.title, style = MaterialTheme.typography.titleMedium)
            Text("${indianNumber(line.quantity)} pieces × ₹${indianNumber(line.quotedPriceRupees)} = ₹${indianNumber(line.totalRupees)}")
        }
        Text("Cart total: ₹${indianNumber(cart.totalRupees)}", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(cash, { cash = it; onChange() }, enabled = !busy,
            label = { Text("Cash received (₹)") }, singleLine = true,
            isError = error?.field == ProductField.CashReceived, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { cash = cart.totalRupees.toString(); onChange() }, enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Exact amount") }
        val tender = cash.trim().takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull()
        Text(if (tender != null && tender >= cart.totalRupees) "Change: ₹${indianNumber(Math.subtractExact(tender, cart.totalRupees))}" else "Enter enough cash to calculate change.",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium)
        if (error != null) Column(Modifier.bringIntoViewRequester(errorView)) { FormError(error.message) }
        ShopPrimaryButton(if (busy) "Saving sale…" else "Complete cash sale", enabled = !busy, onClick = { complete(cash) })
        OutlinedButton(onClick = edit, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Edit cart") }
        Text("The whole sale is saved on this phone only when every line has enough stock.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SalesHistory(shop: ShopSnapshot, open: (String) -> Unit) {
    if (shop.sales.isEmpty()) ShopEmptyState("No completed cash sales yet.", "Completed sales and their receipts appear here after cash checkout.", R.drawable.ic_carts)
    shop.sales.forEach { sale ->
        ShopSection("Cart ${sale.cartNumber}") {
            Text(saleDate(sale.recordedAt), style = MaterialTheme.typography.bodyMedium)
            if (sale.customer.isNotEmpty()) Text(sale.customer, style = MaterialTheme.typography.titleMedium)
            Text("₹${indianNumber(sale.totalRupees)} · ${sale.lines.size} product lines", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { open(sale.cartId) }, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Open receipt · Cart ${sale.cartNumber}") }
        }
    }
}

@Composable
internal fun SaleReceipt(sale: Sale, history: () -> Unit) {
    ShopSection("Sale saved on this phone") {
        Text("Cash sale · Cart ${sale.cartNumber}", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(saleDate(sale.recordedAt), style = MaterialTheme.typography.bodyMedium)
        Text("Receipt: ${sale.id}", style = MaterialTheme.typography.bodyMedium)
        if (sale.customer.isNotEmpty()) Text(sale.customer, style = MaterialTheme.typography.titleMedium)
        Text("Total: ₹${indianNumber(sale.totalRupees)}", style = MaterialTheme.typography.headlineSmall)
        Text("Cash received: ₹${indianNumber(sale.cashRupees)}")
        Text("Change: ₹${indianNumber(sale.changeRupees)}")
        Text("This receipt remains available offline in Sales history.", style = MaterialTheme.typography.bodyMedium)
    }
    sale.lines.forEach { line -> ShopSection(line.title) {
        Text(line.sku, style = MaterialTheme.typography.bodyMedium)
        Text(line.category, style = MaterialTheme.typography.bodyMedium)
        Text(line.fitment, style = MaterialTheme.typography.bodyMedium)
        if (line.description.isNotEmpty()) Text(line.description, style = MaterialTheme.typography.bodyMedium)
        Text("${indianNumber(line.quantity)} pieces × ₹${indianNumber(line.quotedPriceRupees)}", style = MaterialTheme.typography.bodyLarge)
        Text("Line total: ₹${indianNumber(line.totalRupees)}", style = MaterialTheme.typography.titleMedium)
    } }
    OutlinedButton(onClick = history, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Sales history") }
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
