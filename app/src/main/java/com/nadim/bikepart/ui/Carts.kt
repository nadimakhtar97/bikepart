package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.CartDraft
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.Cart
import com.nadim.bikepart.shop.CatalogSearch
import com.nadim.bikepart.shop.ProductField
import com.nadim.bikepart.shop.ProductFilter
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.shop.indianNumber
import com.nadim.bikepart.shop.otherCartDemand
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun CartList(shop: ShopSnapshot, busy: Boolean, onNew: () -> Unit, onOpen: (String) -> Unit) {
    ShopPageHeader(stringResource(R.string.cart_list_header), "Carts do not reserve stock. Product price changes update matching lines in every unfinished cart.")
    if (shop.carts.isEmpty()) {
        ShopEmptyState(stringResource(R.string.cart_no_carts_title), "Start a sale to create an automatically saved customer bill.", R.drawable.ic_carts)
        return
    }
    ShopSectionHeader("${shop.carts.size} unfinished ${if (shop.carts.size == 1) "cart" else "carts"}")
    ShopListGroup {
        shop.carts.forEachIndexed { index, cart ->
            ShopListRow(cart.name,
                supporting = listOfNotNull(cart.customer.ifBlank { null }, "${cart.lines.size} ${if (cart.lines.size == 1) "line" else "lines"}").joinToString(" · "),
                meta = "₹${indianNumber(cart.totalRupees)}", status = { ShopStatusChip(stringResource(R.string.shared_unfinished)) },
                enabled = !busy,
                actionLabel = "Open ${cart.name}",
                onClick = { onOpen(cart.id) },
                dividerAbove = index > 0
            )
        }
    }
}

@Composable
internal fun CartEditor(shop: ShopSnapshot, draft: CartDraft?, busy: Boolean, error: ShopError?,
    edit: (String?, String?, String?) -> Unit, pause: () -> Unit, add: () -> Unit,
    remove: (String) -> Unit, discard: () -> Unit, retry: () -> Unit, reload: () -> Unit, checkout: () -> Unit) {
    if (draft == null) { Text(stringResource(R.string.cart_unavailable)); return }
    val cart = shop.carts.find { it.id == draft.cart.id } ?: draft.cart
    var confirmDiscard by rememberSaveable(cart.id) { mutableStateOf(false) }
    val errorView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null && error.definitionId == null) errorView.bringIntoView() }
    ShopGroup {
        // Autosave buffers typing while a commit is pending; structural actions wait.
        ShopTextField(
            draft.customer,
            { edit(it, null, null) },
            stringResource(R.string.cart_customer_label)
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(when { busy -> "Saving cart…"; error != null -> "Changes not saved"; draft.dirty -> "Unsaved changes…"; else -> "Saved on this phone" },
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ShopStatusChip(when { error != null -> "Not saved"; busy -> "Saving"; draft.dirty -> "Unsaved"; else -> "Saved" },
                when { error != null -> ShopStatusTone.Danger; draft.dirty || busy -> ShopStatusTone.Warning; else -> ShopStatusTone.Success })
        }
    }
    if (error != null) ShopGroup {
        Column(Modifier.bringIntoViewRequester(errorView)) {
            FormError(error.message)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                TextButton(onClick = retry, enabled = !busy) { Text(stringResource(R.string.cart_retry_saving)) }
                TextButton(onClick = reload, enabled = !busy) { Text(stringResource(R.string.cart_reload_saved)) }
            }
        }
    }
    if (cart.lines.isEmpty()) Text(stringResource(R.string.cart_no_products), style = MaterialTheme.typography.bodyMedium)
    else {
        ShopSectionHeader(stringResource(R.string.products), supporting = "${cart.lines.size} quoted ${if (cart.lines.size == 1) "line" else "lines"}")
        ShopGroup(spacing = ShopSpacing.small) {
            cart.lines.forEach { line ->
                val product = shop.products.single { it.id == line.productId }
                val quantityError = error?.field == ProductField.CartQuantity && error.definitionId == product.id
                val quantityView = remember(product.id) { BringIntoViewRequester() }
                LaunchedEffect(error) { if (quantityError) quantityView.bringIntoView() }
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier
                        .fillMaxWidth()
                        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShopListRow(
                            product.title,
                            supporting = product.sku,
                            meta = "₹${indianNumber(line.quotedPriceRupees)}",
                            minHeight = 64.dp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.on_hand_pieces, indianNumber(product.quantity)), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.other_carts_pieces, shop.otherCartDemand(cart.id, product.id)), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.quoted_unit_price, indianNumber(line.quotedPriceRupees)), style = MaterialTheme.typography.bodyMedium)
                        ShopTextField(
                            value = draft.quantities[product.id].orEmpty(),
                            onValueChange = { edit(null, product.id, it) },
                            label = stringResource(R.string.cart_pieces_label),
                            supporting = if (quantityError) stringResource(R.string.cart_invalid_quantity) else null,
                            error = if (quantityError) stringResource(R.string.cart_invalid_quantity) else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            contentDescription = "Pieces · ${product.title}",
                            modifier = Modifier.bringIntoViewRequester(quantityView),
                        )
                        Text(stringResource(R.string.saved_line_total, indianNumber(line.totalRupees)), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { remove(product.id) }, enabled = !busy && !draft.dirty,
                            modifier = Modifier.semantics { contentDescription = "Remove ${product.title}" }) { Text(stringResource(R.string.cart_remove_line)) }
                    }
                }
            }
        }
    }
    ShopGroup {
        Text(stringResource(R.string.saved_cart_total, indianNumber(cart.totalRupees)), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite })
    }
    ShopListGroup {
        ShopListRow(stringResource(R.string.cart_add_products), supporting = "Search or scan another product", icon = R.drawable.ic_add,
            enabled = !busy && !draft.dirty, onClick = add)
    }
    ShopPrimaryButton(stringResource(R.string.cart_checkout), enabled = !busy && cart.lines.isNotEmpty(), onClick = checkout)
    TextButton(onClick = pause, enabled = !busy, modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.cart_save_return)) }
    TextButton(onClick = { confirmDiscard = true }, enabled = !busy && !draft.dirty,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ShopSpacing.touchTarget),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.cart_discard)) }
    if (confirmDiscard) AlertDialog(onDismissRequest = { if (!busy) confirmDiscard = false },
        title = { Text(stringResource(R.string.discard, cart.name)) }, text = { Text(stringResource(R.string.cart_discard_stock_note)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; discard() }, enabled = !busy) { Text(stringResource(R.string.cart_discard_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cart_keep)) } })
}

@Composable
internal fun CartProductPicker(shop: ShopSnapshot, cart: Cart, busy: Boolean, error: ShopError?, loadPhoto: PhotoLoader,
    onChange: () -> Unit, add: (String, String) -> Unit, onScan: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var limit by rememberSaveable(query) { mutableIntStateOf(30) }
    val search = remember(shop) { CatalogSearch(shop) }
    val results = remember(search, query) { search.find(ProductFilter(query = query)) }
    val product = shop.products.find { it.id == selected }
    val selectedView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected != null) selectedView.bringIntoView() }
    if (product != null) Column(Modifier.bringIntoViewRequester(selectedView)) { ShopSection(stringResource(R.string.cart_selected_product)) {
        Text(product.title, style = MaterialTheme.typography.titleLarge, maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(product.sku + " · " + shop.categories.single { it.id == product.categoryId }.name,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StockHealthIndicator(product)
        Text(stringResource(R.string.on_hand_pieces, indianNumber(product.quantity)))
        Text(stringResource(R.string.other_carts_pieces, shop.otherCartDemand(cart.id, product.id)))
        val line = cart.lines.find { it.productId == product.id }
        Text(stringResource(R.string.unit_price, indianNumber(line?.quotedPriceRupees ?: product.priceRupees)),
            style = MaterialTheme.typography.titleMedium)
        if (line != null) Text(stringResource(R.string.already_in_this_cart_pieces, indianNumber(line.quantity)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ShopTextField(
            quantity,
            { quantity = it; onChange() },
            stringResource(R.string.scanner_pieces_to_add),
            enabled = !busy,
            error = error?.takeIf { it.field == ProductField.CartQuantity }?.message,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (error != null && error.field != ProductField.CartQuantity) FormError(error.message)
        ShopPrimaryButton(stringResource(R.string.scanner_add_to_cart), enabled = !busy, onClick = { add(product.id, quantity) })
    } }
    ShopSearchField(query, { query = it; selected = null; onChange() }, "Search products for cart", enabled = !busy,
        trailing = { ShopScanButton(stringResource(R.string.scan_for_sale), !busy, onScan) })
    if (results.isEmpty()) Text(stringResource(R.string.manual_no_matching_title), style = MaterialTheme.typography.bodyMedium)
    else {
        ShopSectionHeader(stringResource(R.string.products), supporting = "${results.size} matching")
        ShopListGroup {
            results.take(limit).forEach { item ->
                StockProductRow(shop, item, loadPhoto, enabled = !busy) { selected = item.id; quantity = "1"; onChange() }
            }
        }
    }
    if (results.size > limit) ShopSecondaryButton(onClick = { limit += 30 }, modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.search_show_more)) }
}
