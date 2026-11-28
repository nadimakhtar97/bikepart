package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.nadim.bikepart.CartDraft
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun CartList(shop: ShopSnapshot, busy: Boolean, onNew: () -> Unit, onOpen: (String) -> Unit) {
    ShopPageHeader(stringResource(R.string.cart_list_header), stringResource(R.string.cart_list_subtitle))
    if (shop.carts.isEmpty()) {
        ShopEmptyState(stringResource(R.string.cart_no_carts_title), stringResource(R.string.cart_no_carts_desc), R.drawable.ic_carts)
        return
    }
    ShopSectionHeader(pluralStringResource(R.plurals.unfinished_cart_count, shop.carts.size, shop.carts.size))
    ShopListGroup {
        shop.carts.forEach { cart ->
            ShopListRow(stringResource(R.string.cart_3, cart.number),
                supporting = listOfNotNull(cart.customer.ifBlank { null }, pluralStringResource(R.plurals.cart_line_count, cart.lines.size, cart.lines.size)).joinToString(" · "),
                meta = stringResource(R.string.rupee_amount, indianNumber(cart.totalRupees)), status = { ShopStatusChip(stringResource(R.string.shared_unfinished)) },
                enabled = !busy, actionLabel = stringResource(R.string.open, stringResource(R.string.cart_3, cart.number)), onClick = { onOpen(cart.id) })
        }
    }
}

@Composable
internal fun CartEditor(
    shop: ShopSnapshot,
    cart: Cart,
    draft: CartDraft,
    busy: Boolean,
    error: ShopError?,
    edit: (String?, String?, String?) -> Unit,
    add: () -> Unit,
    dismissStockNotice: () -> Unit,
    retry: () -> Unit,
    reload: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val errorView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null && error.definitionId == null) errorView.bringIntoView() }

    ShopTextField(draft.customer, { edit(it, null, null) }, stringResource(R.string.cart_customer_label))
    Text(
        stringResource(when {
            error != null -> R.string.cart_changes_not_saved
            busy -> R.string.cart_saving
            draft.dirty -> R.string.cart_unsaved_changes
            else -> R.string.cart_saved
        }),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (error != null) {
        FlowRow(
            Modifier.bringIntoViewRequester(errorView),
            horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small),
        ) {
            TextButton(onClick = retry, enabled = !busy) { Text(stringResource(R.string.cart_retry_saving)) }
            TextButton(onClick = reload, enabled = !busy) { Text(stringResource(R.string.cart_reload_saved)) }
        }
    }

    if (cart.stockReductions.isNotEmpty()) {
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            cart.stockReductions.forEach { (productId, quantity) ->
                val title = shop.products.find { it.id == productId }?.title ?: productId
                Text(if (quantity == 0L) stringResource(R.string.cart_stock_removed, title)
                    else stringResource(R.string.cart_stock_reduced, title, indianNumber(quantity)))
            }
            TextButton(onClick = dismissStockNotice, enabled = !busy && !draft.dirty) {
                Text(stringResource(R.string.cart_stock_dismiss))
            }
        }
    }

    if (cart.lines.isEmpty()) {
        Text(stringResource(R.string.cart_no_products), style = MaterialTheme.typography.bodyMedium)
        ShopPrimaryButton(stringResource(R.string.cart_add_products), enabled = !busy && !draft.dirty, onClick = add)
    } else {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = ShopSpacing.content)) {
                ShopSectionHeader(stringResource(R.string.products))
                cart.lines.forEach { line ->
                    key(line.productId) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val product = shop.products.single { it.id == line.productId }
                        val quantityError = error?.field == ProductField.CartQuantity && error.definitionId == product.id
                        val quantityView = remember { BringIntoViewRequester() }
                        var showDetails by rememberSaveable { mutableStateOf(false) }
                        val quantityText = draft.quantities[product.id].orEmpty()
                        val quantity = quantityText.trim().toLongOrNull() ?: 0L
                        val decreaseLabel = stringResource(R.string.cart_decrease_quantity, product.title)
                        val increaseLabel = stringResource(R.string.cart_increase_quantity, product.title)
                        val setQuantity: (String) -> Unit = { edit(null, product.id, it) }
                        LaunchedEffect(quantityError) { if (quantityError) quantityView.bringIntoView() }

                        Column(
                            Modifier.fillMaxWidth().padding(vertical = ShopSpacing.small),
                            verticalArrangement = Arrangement.spacedBy(ShopSpacing.small),
                        ) {
                            Text(product.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.saved_line_total, indianNumber(line.totalRupees)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small),
                            ) {
                                TextButton(
                                    onClick = { setQuantity((quantity - 1).toString()) },
                                    enabled = quantity > 0,
                                    modifier = Modifier.sizeIn(minWidth = ShopSpacing.touchTarget, minHeight = ShopSpacing.touchTarget)
                                        .semantics { contentDescription = decreaseLabel },
                                ) { Text(stringResource(R.string.decrease_quantity)) }
                                ShopTextField(
                                    value = quantityText,
                                    onValueChange = setQuantity,
                                    label = stringResource(R.string.cart_pieces_label),
                                    error = error?.message?.takeIf { quantityError },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    contentDescription = context.getString(R.string.pieces_4, product.title),
                                    modifier = Modifier.weight(1f).bringIntoViewRequester(quantityView),
                                )
                                TextButton(
                                    onClick = { setQuantity((quantity + 1).toString()) },
                                    enabled = quantity in 1L until product.quantity,
                                    modifier = Modifier.sizeIn(minWidth = ShopSpacing.touchTarget, minHeight = ShopSpacing.touchTarget)
                                        .semantics { contentDescription = increaseLabel },
                                ) { Text(stringResource(R.string.increase_quantity)) }
                            }
                            if (showDetails) {
                                Text(product.sku, style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.on_hand_pieces, indianNumber(product.quantity)), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.other_carts_pieces, shop.otherCartDemand(cart.id, product.id)), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.quoted_unit_price, indianNumber(line.quotedPriceRupees)), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { showDetails = !showDetails }) {
                                Text(stringResource(if (showDetails) R.string.cart_hide_details else R.string.cart_details))
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ShopListRow(
                    stringResource(R.string.cart_add_products),
                    icon = R.drawable.ic_add,
                    containerColor = MaterialTheme.colorScheme.surface,
                    enabled = !busy && !draft.dirty,
                    onClick = add,
                )
            }
        }
    }
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
        Text(stringResource(R.string.formatted_text_2, product.sku, shop.categories.single { it.id == product.categoryId }.name),
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
        ShopPrimaryButton(stringResource(R.string.scanner_add_to_cart), enabled = !busy, onClick = { add(product.id, quantity) })
    } }
    ShopSearchField(query, { query = it; selected = null; onChange() }, stringResource(R.string.cart_product_picker_search), enabled = !busy,
        scanLabel = stringResource(R.string.scan_for_sale), onScan = onScan)
    if (results.isEmpty()) Text(stringResource(R.string.manual_no_matching_title), style = MaterialTheme.typography.bodyMedium)
    else {
        ShopSectionHeader(stringResource(R.string.products), supporting = stringResource(R.string.matching, results.size))
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
