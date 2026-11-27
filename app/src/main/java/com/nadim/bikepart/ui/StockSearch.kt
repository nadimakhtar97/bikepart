package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
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

/** Searchable manual choices remain bounded even with a large lookup catalog. */
@Composable
private fun SearchFilter(label: String, choices: List<Pair<String, String>>, selected: String?, onSelect: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val selectedLabel = choices.find { it.first == selected }?.second ?: "Any"
    ShopSecondaryButton(onClick = { open = true }, modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = ShopSpacing.touchTarget)) {
        Text(stringResource(R.string.formatted_text, label, selectedLabel))
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            ShopSearchField(query, { query = it }, stringResource(R.string.find, label))
            val matching = choices.filter { LookupNames.key(it.second).contains(LookupNames.key(query)) }
            LazyColumn(Modifier.heightIn(max = ShopSpacing.formWidth)) {
                item { TextButton(onClick = { onSelect(null); open = false; query = "" }, modifier = Modifier
                    .heightIn(min = ShopSpacing.touchTarget)
                    .semantics { this.selected = selected == null }) { Text(stringResource(R.string.any, label)) } }
                items(matching, key = { it.first }) { (id, name) ->
                    TextButton(onClick = { onSelect(id); open = false; query = "" }, modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ShopSpacing.touchTarget)
                        .semantics { this.selected = id == selected }) { Text(name) }
                }
                if (matching.isEmpty()) item { Text(stringResource(R.string.no_matching_choices)) }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.shared_close)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockSearch(shop: ShopSnapshot, initialHealth: String?, loadPhoto: PhotoLoader, onOpen: (String) -> Unit,
    busy: Boolean = false, onScan: () -> Unit = {}, onProductAction: (Product, String) -> Unit = { _, _ -> }, initialQuery: String = "") {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
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
        enabled = !busy, supporting = "Title, SKU, description, fields, fitment or location",
        scanLabel = stringResource(R.string.search_scan_stock), onScan = onScan)
    Row(Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        ShopChoiceChip(
            label = stringResource(R.string.search_all_stock),
            selected = health == null,
            onClick = { health = null },
            leading = if (health == null) ({
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null
                )
            }) else null
        )
        StockHealth.entries.forEach { state ->
            ShopChoiceChip(
                label = state.label,
                selected = health == state,
                onClick = { health = if (health == state) null else state },
                leading = if (health == state) ({
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null
                    )
                }) else null
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        ShopChoiceChip(
            selected = category != null, onClick = { expanded = true },
            label = category?.let { id -> shop.categories.find { it.id == id }?.name }
                ?: "All categories",
            trailing = {
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null
                )
            })
        ShopSecondaryButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) {
            Icon(painterResource(R.drawable.ic_filter), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(ShopSpacing.small))
            Text(stringResource(R.string.search_more_filters))
        }
        if (query.isNotEmpty() || category != null || make != null || model != null || location != null || health != null || definition != null) TextButton(onClick = { query = ""; category = null; make = null; model = null; location = null; health = null; definition = null; decimal = "" },
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.search_clear_and_filters)) }
    }
    val active = listOfNotNull(
        make?.let { id -> "Make: " + (shop.makes.find { it.id == id }?.name ?: "Unknown fitment") },
        model?.let { id -> "Model: " + shop.models.find { it.id == id }?.name },
        location?.let { id -> "Location: " + (shop.locations.find { it.id == id }?.name ?: "No location assigned") },
        definition?.let { id -> (shop.attributes.find { it.id == id }?.name ?: "Decimal field") + ": " + decimal })
    if (active.isNotEmpty()) Text(active.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (decimalError && !expanded) FormError(stringResource(R.string.search_decimal_error))
    if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
      Column(Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .imePadding()
          .padding(ShopSpacing.content),
          verticalArrangement = Arrangement.spacedBy(ShopSpacing.medium)) {
        ShopSectionHeader(stringResource(R.string.search_catalog_filters))
        TextButton(onClick = { category = null; make = null; model = null; location = null; health = null; definition = null; decimal = "" },
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.search_clear_filters)) }
        SearchFilter("Category", shop.categories.map { it.id to it.name }, category) { category = it; definition = null; decimal = "" }
        SearchFilter("Make", listOf("unknown" to "Unknown fitment") + shop.makes.map { it.id to (it.name + if (it.retired) " (retired)" else "") }, make) { make = it; model = null }
        if (make != "unknown") SearchFilter("Model", shop.models.filter { make == null || it.makeId == make }.map { item -> item.id to (shop.makes.single { it.id == item.makeId }.name + " · " + item.name + if (item.retired) " (retired)" else "") }, model) { model = it }
        SearchFilter("Location", listOf("none" to "No location assigned") + shop.locations.map { it.id to (it.name + if (it.retired) " (retired)" else "") }, location) { location = it }
        SearchFilter("Decimal field", shop.attributes.filter { it.type == AttributeType.Decimal && (category == null || it.categoryId == category) }.map { it.id to (it.name + " · " + it.unit + " · " + (it.productId?.let { id -> shop.products.find { it.id == id }?.title } ?: shop.categories.single { c -> c.id == it.categoryId }.name)) }, definition) { definition = it; decimal = "" }
        if (definition != null) {
            ShopTextField(
                decimal, { decimal = it }, stringResource(R.string.search_exact_decimal),
                error = if (decimalError) stringResource(R.string.search_decimal_error) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        ShopPrimaryButton(stringResource(R.string.search_show_results), onClick = { expanded = false }, enabled = !decimalError)
      }
    }
    val found = results?.takeIf { it.snapshot === shop && it.filter == filter }?.products
    if (found == null) Text(stringResource(R.string.search_searching), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    else {
        Text(stringResource(R.string.matching_products, found.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (found.isEmpty()) ShopEmptyState(stringResource(R.string.manual_no_matching_title), "Try another description or clear the filters to browse all stock.", R.drawable.ic_stock)
        else ShopListGroup {
            found.take(limit).forEach { product -> StockProductRow(shop, product, loadPhoto, enabled = !busy,
                menu = { ShopOverflowMenu(stringResource(R.string.actions_for)+product.title, listOf(
                    ShopMenuAction(stringResource(R.string.product_edit_details), R.drawable.ic_edit) { onProductAction(product, "edit") },
                    ShopMenuAction(stringResource(R.string.product_receive_stock), R.drawable.ic_inventory) { onProductAction(product, "receive") },
                    ShopMenuAction(stringResource(R.string.scanner_copy_catalog), R.drawable.ic_content_copy) { onProductAction(product, "copy") },
                    ShopMenuAction(stringResource(R.string.product_preview_labels), R.drawable.ic_qr_code_scanner) { onProductAction(product, "labels") }
                ), enabled = !busy) }) { onOpen(product.id) } }
        }
        if (found.size > limit) ShopSecondaryButton(onClick = { limit += 40 },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.search_show_more)) }
    }
}

@Composable
internal fun StockProductRow(shop: ShopSnapshot, product: Product, loadPhoto: PhotoLoader, enabled: Boolean = true, menu: (@Composable () -> Unit)? = null, onOpen: () -> Unit) {
    Surface(onClick = onOpen, enabled = enabled, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)) {
        val density = LocalDensity.current
        val priceStyle = MaterialTheme.typography.titleMedium
        val measurer = rememberTextMeasurer()
        val price = "₹${indianNumber(product.priceRupees)}"
        BoxWithConstraints {
            val priceMax = (maxWidth - 24.dp) * 0.4f
            // A price that cannot fit its share of the row moves under the title so
            // a grouped amount never breaks across lines.
            val stackPrice = density.fontScale >= 1.5f ||
                measurer.measure(price, priceStyle, maxLines = 1, softWrap = false).size.width > with(density) { priceMax.toPx() }
            Row(Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ProductPhoto(product.photo, loadPhoto, Modifier.size(52.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(product.title, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(shop.categories.single { it.id == product.categoryId }.name + " · " + indianNumber(product.quantity) + " pieces",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (product.archived) ShopStatusChip(stringResource(R.string.shared_archived))
                        else if (product.stockHealth != StockHealth.InStock) {
                            ShopStatusChip(product.stockHealth.label, when (product.stockHealth) {
                                StockHealth.LowStock -> ShopStatusTone.Warning
                                StockHealth.OutOfStock -> ShopStatusTone.Danger
                                StockHealth.InStock -> ShopStatusTone.Neutral
                            })
                        }
                        if (shop.missingRequired(product.id).isNotEmpty()) ShopStatusChip(stringResource(R.string.shared_catalog_incomplete), ShopStatusTone.Accent)
                    }
                    Text(product.sku, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (stackPrice) Text(price, style = priceStyle)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                  if (!stackPrice) Text(price, style = priceStyle, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = priceMax))
                menu?.invoke()
                }
            }
        }
    }
}
