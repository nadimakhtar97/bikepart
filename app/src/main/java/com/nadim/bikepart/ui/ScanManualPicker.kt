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
    ShopSearchField(query, { query = it }, stringResource(R.string.manual_search_products), enabled = !busy,
        scanLabel = if (catalog) stringResource(R.string.btn_scan_catalog_details) else stringResource(R.string.manual_scan_receiving), onScan = onScan)
    if (catalog) ShopSecondaryButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = create, enabled = !busy) { Text(stringResource(R.string.manual_continue_product)) }
    if (results.isEmpty()) ShopEmptyState(stringResource(R.string.manual_no_matching_title), stringResource(R.string.manual_no_matching_desc), com.nadim.bikepart.R.drawable.ic_stock)
    else ShopListGroup {
        results.take(limit).forEach { product -> StockProductRow(shop, product, loadPhoto, enabled = !busy) { select(product.id) } }
    }
    templates.take(limit).forEach {
        ShopSecondaryButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = { template(it.id) }, enabled = !busy) { Text(stringResource(R.string.use_template, it.catalog.title)) }
    }
    if (results.size > limit || templates.size > limit) ShopSecondaryButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = { limit += 30 }) { Text(stringResource(R.string.manual_show_more)) }
}
