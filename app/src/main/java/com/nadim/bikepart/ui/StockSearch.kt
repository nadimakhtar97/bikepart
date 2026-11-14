package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PreparedCatalog(val snapshot: ShopSnapshot, val search: CatalogSearch)
private data class CatalogResult(val snapshot: ShopSnapshot, val filter: ProductFilter, val products: List<Product>)

@Composable
internal fun StockHealthIndicator(product: Product) {
    // The icon follows the state instead of repeating a neutral stock glyph.
    val icon = when (product.stockHealth) {
        StockHealth.LowStock -> R.drawable.ic_warning
        StockHealth.OutOfStock -> R.drawable.ic_no_stock
        StockHealth.InStock -> R.drawable.ic_stock
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null)
        Text(product.stockHealth.label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ThresholdForm(product: Product, busy: Boolean, error: ShopError?, onChange: () -> Unit, save: (String) -> Unit) {
    var pieces by rememberSaveable(product.id, product.lowStockThreshold) { mutableStateOf(product.lowStockThreshold?.toString().orEmpty()) }
    val focusError = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error?.field == ProductField.LowStockThreshold) focusError.bringIntoView() }
    ShopSection("Low-stock alert") {
        Text(product.lowStockThreshold?.let { "Threshold: ${indianNumber(it)} pieces" } ?: "Threshold: Not set",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text("Positive stock at or below this threshold is low. Zero is always out of stock. Leave blank to clear.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(pieces, { pieces = it; onChange() }, enabled = !busy, label = { Text("Low-stock threshold") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            isError = error?.field == ProductField.LowStockThreshold, modifier = Modifier.fillMaxWidth().bringIntoViewRequester(focusError))
        if (error?.field == ProductField.LowStockThreshold) FormError(error.message)
        ShopPrimaryButton("Save threshold", enabled = !busy, onClick = { save(pieces) })
    }
}

/** Searchable manual choices remain bounded even with a large lookup catalog. */
@Composable
private fun SearchFilter(label: String, choices: List<Pair<String, String>>, selected: String?, onSelect: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val selectedLabel = choices.find { it.first == selected }?.second ?: "Any"
    ShopSecondaryButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) {
        Text("$label: $selectedLabel")
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            OutlinedTextField(query, { query = it }, label = { Text("Find $label") }, modifier = Modifier.fillMaxWidth())
            val matching = choices.filter { LookupNames.key(it.second).contains(LookupNames.key(query)) }
            LazyColumn(Modifier.heightIn(max = ShopSpacing.formWidth)) {
                item { TextButton(onClick = { onSelect(null); open = false; query = "" }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget).semantics { this.selected = selected == null }) { Text("Any $label") } }
                items(matching, key = { it.first }) { (id, name) ->
                    TextButton(onClick = { onSelect(id); open = false; query = "" }, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget).semantics { this.selected = id == selected }) { Text(name) }
                }
                if (matching.isEmpty()) item { Text("No matching choices") }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } })
}

@Composable
internal fun StockSearch(shop: ShopSnapshot, initialHealth: String?, loadPhoto: PhotoLoader, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var make by rememberSaveable { mutableStateOf<String?>(null) }
    var model by rememberSaveable { mutableStateOf<String?>(null) }
    var location by rememberSaveable { mutableStateOf<String?>(null) }
    var health by rememberSaveable(initialHealth) { mutableStateOf(initialHealth?.let { StockHealth.valueOf(it) }) }
    var definition by rememberSaveable { mutableStateOf<String?>(null) }
    var decimal by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(40) }
    val decimalError = definition != null && decimal.isNotBlank() && runCatching { exactDecimal(decimal) }.isFailure
    val filter = ProductFilter(query, category, make?.takeUnless { it == "unknown" }, model, location?.takeUnless { it == "none" }, health,
        make == "unknown", location == "none", if (definition != null && decimal.isNotBlank() && !decimalError) DecimalFilter(requireNotNull(definition), decimal) else null)
    LaunchedEffect(filter) { limit = 40 }
    val prepared by produceState<PreparedCatalog?>(null, shop) {
        value = withContext(Dispatchers.Default) { PreparedCatalog(shop, CatalogSearch(shop)) }
    }
    val search = prepared?.takeIf { it.snapshot === shop }?.search
    val results by produceState<CatalogResult?>(null, search, filter, decimalError) {
        value = null
        val current = search
        if (current != null) value = withContext(Dispatchers.Default) { CatalogResult(shop, filter, if (decimalError) emptyList() else current.find(filter)) }
    }
    ShopSearchField(query, { query = it }, "Search products",
        supporting = "Title, SKU, description, fields, fitment or location")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        FilterChip(selected = health == null, onClick = { health = null }, label = { Text("All stock") },
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget))
        StockHealth.entries.forEach { state ->
            FilterChip(selected = health == state, onClick = { health = if (health == state) null else state },
                label = { Text(state.label) }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget))
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        ShopSecondaryButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) {
            Icon(painterResource(R.drawable.ic_filter), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(ShopSpacing.small))
            Text(if (expanded) "Hide filters" else "More filters")
        }
        TextButton(onClick = { query = ""; category = null; make = null; model = null; location = null; health = null; definition = null; decimal = "" },
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Clear search and filters") }
    }
    if (expanded) ShopSection("Catalog filters") {
        SearchFilter("Category", shop.categories.map { it.id to it.name }, category) { category = it; definition = null; decimal = "" }
        SearchFilter("Make", listOf("unknown" to "Unknown fitment") + shop.makes.map { it.id to (it.name + if (it.retired) " (retired)" else "") }, make) { make = it; model = null }
        if (make != "unknown") SearchFilter("Model", shop.models.filter { make == null || it.makeId == make }.map { item -> item.id to (shop.makes.single { it.id == item.makeId }.name + " · " + item.name + if (item.retired) " (retired)" else "") }, model) { model = it }
        SearchFilter("Location", listOf("none" to "No location assigned") + shop.locations.map { it.id to (it.name + if (it.retired) " (retired)" else "") }, location) { location = it }
        SearchFilter("Decimal field", shop.attributes.filter { it.type == AttributeType.Decimal && (category == null || it.categoryId == category) }.map { it.id to (it.name + " · " + it.unit + " · " + (it.productId?.let { id -> shop.products.find { it.id == id }?.title } ?: shop.categories.single { c -> c.id == it.categoryId }.name)) }, definition) { definition = it; decimal = "" }
        if (definition != null) {
            OutlinedTextField(decimal, { decimal = it }, label = { Text("Exact decimal value") }, isError = decimalError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            if (decimalError) FormError("Enter a plain exact decimal, without grouping or exponent")
        }
    }
    val found = results?.takeIf { it.snapshot === shop && it.filter == filter }?.products
    if (found == null) Text("Searching products…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    else {
        Text("${found.size} matching products", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (found.isEmpty()) ShopEmptyState("No matching products", "Try another description or clear the filters to browse all stock.", R.drawable.ic_stock)
        else ShopListGroup {
            found.take(limit).forEach { product -> StockProductRow(shop, product, loadPhoto) { onOpen(product.id) } }
        }
        if (found.size > limit) ShopSecondaryButton(onClick = { limit += 40 },
            modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Show more products") }
    }
}

@Composable
internal fun StockProductRow(shop: ShopSnapshot, product: Product, loadPhoto: PhotoLoader, enabled: Boolean = true, onOpen: () -> Unit) {
    Surface(onClick = onOpen, enabled = enabled, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        val density = LocalDensity.current
        val priceStyle = MaterialTheme.typography.titleMedium
        val measurer = rememberTextMeasurer()
        val price = "₹${indianNumber(product.priceRupees)}"
        BoxWithConstraints {
            val priceMax = (maxWidth - 24.dp) * 0.45f
            // A price that cannot fit its share of the row moves under the title so
            // a grouped amount never breaks across lines.
            val stackPrice = density.fontScale >= 1.5f ||
                measurer.measure(price, priceStyle, maxLines = 1, softWrap = false).size.width > with(density) { priceMax.toPx() }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ProductPhoto(product.photo, loadPhoto, Modifier.size(52.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(product.title, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(shop.categories.single { it.id == product.categoryId }.name + " · " + indianNumber(product.quantity) + " pieces",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (product.archived) ShopStatusChip("Archived")
                        else if (product.stockHealth != StockHealth.InStock) {
                            ShopStatusChip(product.stockHealth.label, when (product.stockHealth) {
                                StockHealth.LowStock -> ShopStatusTone.Warning
                                StockHealth.OutOfStock -> ShopStatusTone.Danger
                                StockHealth.InStock -> ShopStatusTone.Neutral
                            })
                        }
                        if (shop.missingRequired(product.id).isNotEmpty()) ShopStatusChip("Catalog incomplete", ShopStatusTone.Accent)
                    }
                    Text(product.sku, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (stackPrice) Text(price, style = priceStyle)
                }
                if (!stackPrice) Text(price, style = priceStyle, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = priceMax))
            }
        }
    }
}
