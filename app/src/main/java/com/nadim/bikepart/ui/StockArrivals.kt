package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*

@Composable
internal fun ScanManualPicker(shop: ShopSnapshot, catalog: Boolean, busy: Boolean, loadPhoto: PhotoLoader,
    select: (String) -> Unit, template: (String) -> Unit = {}, create: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable(query) { mutableIntStateOf(30) }
    val search = remember(shop) { CatalogSearch(shop) }
    val results = remember(search, query) { search.find(ProductFilter(query = query)) }
    val templates = if (catalog) shop.templates.filter { !it.retired && it.catalog.title.contains(query, ignoreCase = true) } else emptyList()
    OutlinedTextField(query, { query = it }, label = { Text("Search products manually") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
    if (catalog) OutlinedButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = create, enabled = !busy) { Text("Continue new product") }
    if (results.isEmpty()) Text("No matching products")
    results.take(limit).forEach { product -> StockProductRow(shop, product, loadPhoto, enabled = !busy) { select(product.id) } }
    templates.take(limit).forEach {
        OutlinedButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = { template(it.id) }, enabled = !busy) { Text("Use template: ${it.catalog.title}") }
    }
    if (results.size > limit || templates.size > limit) OutlinedButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = { limit += 30 }) { Text("Show more results") }
}

@Composable
internal fun StockArrivalForm(product: Product, busy: Boolean, error: ShopError?, onChange: () -> Unit, save: (String, String) -> Unit) {
    var pieces by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    ShopSection("Receive stock") {
        Text(product.title, style = MaterialTheme.typography.titleMedium)
        Text(product.sku)
        Text("Current stock: ${indianNumber(product.quantity)} pieces")
        Text("Save the arrival to add these pieces to stock.")
        OutlinedTextField(pieces, { pieces = it; onChange() }, enabled = !busy, label = { Text("Received pieces") },
            isError = error?.field == ProductField.ArrivalQuantity, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(cost, { cost = it; onChange() }, enabled = !busy, label = { Text("Purchase unit cost (₹)") },
            isError = error?.field == ProductField.PurchaseCost, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (error != null) FormError(error.message)
        ShopPrimaryButton("Save arrival", enabled = !busy) { save(pieces, cost) }
    }
}

@Composable
internal fun ArrivalHistory(arrivals: List<StockArrival>) {
    if (arrivals.isEmpty()) return
    ShopSection("Stock arrival history") {
        arrivals.asReversed().forEach {
            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.recordedAt)), style = MaterialTheme.typography.titleMedium)
            Text("Received ${indianNumber(it.quantity)} pieces · ₹${indianNumber(it.unitCostRupees)} per piece")
            Text("${indianNumber(it.previousBalance)} → ${indianNumber(it.resultingBalance)} pieces")
        }
    }
}
