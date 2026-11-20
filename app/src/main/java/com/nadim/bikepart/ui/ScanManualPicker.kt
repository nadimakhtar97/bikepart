package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.CatalogSearch
import com.nadim.bikepart.shop.ProductFilter
import com.nadim.bikepart.shop.ShopSnapshot

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
    if (templates.take(limit).isNotEmpty()) ShopListGroup(showDividers = true) {
        templates.take(limit).forEachIndexed { index, item ->
            ShopListRow(
                title = item.catalog.title,
                supporting = shop.categories.single { it.id == item.catalog.categoryId }.name,
                icon = R.drawable.ic_template,
                actionLabel = stringResource(R.string.use_template, item.catalog.title),
                enabled = !busy,
                onClick = { template(item.id) },
                showChevron = true,
                dividerAbove = index > 0,
            )
        }
    }
    if (results.size > limit || templates.size > limit) ShopSecondaryButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = { limit += 30 }) { Text(stringResource(R.string.manual_show_more)) }
}
