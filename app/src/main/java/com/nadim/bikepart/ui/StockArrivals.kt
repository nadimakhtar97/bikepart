package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*

@Composable
internal fun ScanManualPicker(shop: ShopSnapshot, catalog: Boolean, busy: Boolean, loadPhoto: PhotoLoader,
    select: (String) -> Unit, template: (String) -> Unit = {}, create: () -> Unit = {}, onScan: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable(query) { mutableIntStateOf(30) }
    val search = remember(shop) { CatalogSearch(shop) }
    val results = remember(search, query) { search.find(ProductFilter(query = query)) }
    val templates = if (catalog) shop.templates.filter { !it.retired && it.catalog.title.contains(query, ignoreCase = true) } else emptyList()
    ShopSearchField(query, { query = it }, "Search products manually", enabled = !busy,
        trailing = { ShopScanButton(if (catalog) "Scan catalog details" else "Scan for receiving", !busy, onScan) })
    if (catalog) ShopSecondaryButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = create, enabled = !busy) { Text(stringResource(R.string.manual_continue_product)) }
    if (results.isEmpty()) ShopEmptyState(stringResource(R.string.manual_no_matching_title), "Try a title, SKU, fitment or location.", com.nadim.bikepart.R.drawable.ic_stock)
    else ShopListGroup {
        results.take(limit).forEach { product -> StockProductRow(shop, product, loadPhoto, enabled = !busy) { select(product.id) } }
    }
    templates.take(limit).forEach {
        ShopSecondaryButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = { template(it.id) }, enabled = !busy) { Text(stringResource(R.string.use_template, it.catalog.title)) }
    }
    if (results.size > limit || templates.size > limit) ShopSecondaryButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = { limit += 30 }) { Text(stringResource(R.string.manual_show_more)) }
}

@Composable
internal fun StockArrivalForm(product: Product, busy: Boolean, error: ShopError?, onChange: () -> Unit, save: (String, String) -> Unit) {
    var pieces by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    ShopSection(stringResource(R.string.arrival_details)) {
        Text(product.title, style = MaterialTheme.typography.titleMedium)
        Text(product.sku)
        Text(stringResource(R.string.current_stock_pieces, indianNumber(product.quantity)))
        Text(stringResource(R.string.arrival_save_note))
        OutlinedTextField(pieces, { pieces = it; onChange() }, enabled = !busy, label = { Text(stringResource(R.string.arrival_received_pieces)) },
            isError = error?.field == ProductField.ArrivalQuantity, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(cost, { cost = it; onChange() }, enabled = !busy, label = { Text(stringResource(R.string.arrival_purchase_cost)) },
            isError = error?.field == ProductField.PurchaseCost, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (error != null) FormError(error.message)
        ShopPrimaryButton(stringResource(R.string.arrival_save), enabled = !busy) { save(pieces, cost) }
    }
}

@Composable
internal fun ArrivalHistory(arrivals: List<StockArrival>) {
    if (arrivals.isEmpty()) return
    ShopSection(stringResource(R.string.arrival_history)) {
        arrivals.asReversed().forEach {
            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.recordedAt)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.arrival_received_summary, indianNumber(it.quantity), indianNumber(it.unitCostRupees)))
            Text(stringResource(R.string.arrival_stock_change, indianNumber(it.previousBalance), indianNumber(it.resultingBalance)))
        }
    }
}
