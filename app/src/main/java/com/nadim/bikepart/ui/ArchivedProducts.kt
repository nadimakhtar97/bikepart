package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun ArchivedProducts(shop: ShopSnapshot, loadPhoto: PhotoLoader, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable(query) { mutableIntStateOf(40) }
    val archived = shop.products.filter { it.archived }
    if (archived.isEmpty()) ShopEmptyState(stringResource(R.string.archived_no_products_title), stringResource(R.string.archived_no_products_desc), R.drawable.ic_stock)
    else {
        ShopSearchField(query, { query = it }, stringResource(R.string.archived_search_placeholder))
        val found = archived.filter { LookupNames.key(it.title + " " + it.sku).contains(LookupNames.key(query)) }
        ShopSectionHeader(stringResource(R.string.archived_products, found.size), supporting = stringResource(R.string.archived_title_or_sku))
        if (found.isEmpty()) ShopEmptyState(stringResource(R.string.archived_no_matching_title), stringResource(R.string.archived_no_matching_desc), R.drawable.ic_stock)
        else ShopListGroup {
            found.take(limit).forEach { product -> StockProductRow(shop, product, loadPhoto) { onOpen(product.id) } }
        }
        if (found.size > limit) ShopSecondaryButton(onClick = { limit += 40 }, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.archived_show_more)) }
    }
}
