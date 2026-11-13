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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.CartDraft
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun CartList(shop: ShopSnapshot, busy: Boolean, onNew: () -> Unit, onOpen: (String) -> Unit) {
    ShopPageHeader("Unfinished carts", "Carts do not reserve stock. Prices stay quoted until a line is removed.")
    ShopPrimaryButton("New sale", enabled = !busy, onClick = onNew)
    if (shop.carts.isEmpty()) {
        ShopEmptyState("No unfinished carts", "Start a sale to create an automatically saved customer bill.", R.drawable.ic_carts)
        return
    }
    ShopSectionHeader("${shop.carts.size} unfinished ${if (shop.carts.size == 1) "cart" else "carts"}")
    ShopGroup(spacing = ShopSpacing.small) {
        shop.carts.forEach { cart ->
            ShopListRow(cart.name,
                supporting = listOfNotNull(cart.customer.ifBlank { null }, "${cart.lines.size} ${if (cart.lines.size == 1) "line" else "lines"}").joinToString(" · "),
                meta = "₹${indianNumber(cart.totalRupees)}", status = { ShopStatusChip("Unfinished") },
                enabled = !busy, actionLabel = "Open ${cart.name}", onClick = { onOpen(cart.id) })
        }
    }
}

@Composable
internal fun CartEditor(shop: ShopSnapshot, draft: CartDraft?, busy: Boolean, error: ShopError?,
    edit: (String?, String?, String?) -> Unit, pause: () -> Unit, add: () -> Unit,
    remove: (String) -> Unit, discard: () -> Unit, retry: () -> Unit, reload: () -> Unit, checkout: () -> Unit) {
    if (draft == null) { Text("This cart is no longer available. Return to the cart list."); return }
    val cart = shop.carts.find { it.id == draft.cart.id } ?: draft.cart
    var confirmDiscard by rememberSaveable(cart.id) { mutableStateOf(false) }
    val errorView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null && error.definitionId == null) errorView.bringIntoView() }
    ShopGroup {
        // Autosave buffers typing while a commit is pending; structural actions wait.
        OutlinedTextField(draft.customer, { edit(it, null, null) }, label = { Text("Customer label (optional)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(when { busy -> "Saving cart…"; error != null -> "Changes not saved"; draft.dirty -> "Unsaved changes…"; else -> "Saved on this phone" },
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ShopStatusChip(when { error != null -> "Not saved"; busy -> "Saving"; draft.dirty -> "Unsaved"; else -> "Saved" },
                when { error != null -> ShopStatusTone.Danger; draft.dirty || busy -> ShopStatusTone.Warning; else -> ShopStatusTone.Success })
        }
    }
    if (error != null) ShopGroup {
        Column(Modifier.bringIntoViewRequester(errorView)) {
            FormError(error.message)
            Row(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                TextButton(onClick = retry, enabled = !busy) { Text("Retry saving cart") }
                TextButton(onClick = reload, enabled = !busy) { Text("Reload saved cart") }
            }
        }
    }
    if (cart.lines.isEmpty()) Text("No products in this cart. Add a product to begin.", style = MaterialTheme.typography.bodyMedium)
    else {
        ShopSectionHeader("Products", supporting = "${cart.lines.size} quoted ${if (cart.lines.size == 1) "line" else "lines"}")
        ShopGroup(spacing = ShopSpacing.small) {
            cart.lines.forEach { line ->
                val product = shop.products.single { it.id == line.productId }
                val quantityError = error?.field == ProductField.CartQuantity && error.definitionId == product.id
                val quantityView = remember(product.id) { BringIntoViewRequester() }
                LaunchedEffect(error) { if (quantityError) quantityView.bringIntoView() }
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(product.title, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(product.sku, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("On hand: ${indianNumber(product.quantity)} pieces", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Other carts: ${shop.otherCartDemand(cart.id, product.id)} pieces", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Quoted unit price: ₹${indianNumber(line.quotedPriceRupees)}", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(draft.quantities[product.id].orEmpty(), { edit(null, product.id, it) },
                            label = { Text("Pieces") }, singleLine = true,
                            isError = quantityError, supportingText = if (quantityError) { { Text("Enter a positive whole-piece quantity.") } } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(quantityView)
                                .semantics { contentDescription = "Pieces · ${product.title}" })
                        Text("Saved line total: ₹${indianNumber(line.totalRupees)}", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { remove(product.id) }, enabled = !busy && !draft.dirty,
                            modifier = Modifier.semantics { contentDescription = "Remove ${product.title}" }) { Text("Remove line") }
                    }
                }
            }
        }
    }
    ShopGroup {
        Text("Saved cart total: ₹${indianNumber(cart.totalRupees)}", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
    }
    ShopGroup(spacing = ShopSpacing.small) {
        ShopListRow("Add products", supporting = "Search or scan another product", icon = R.drawable.ic_add,
            enabled = !busy && !draft.dirty, onClick = add)
    }
    ShopPrimaryButton("Checkout", enabled = !busy && cart.lines.isNotEmpty(), onClick = checkout)
    TextButton(onClick = pause, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Save and return to carts") }
    TextButton(onClick = { confirmDiscard = true }, enabled = !busy && !draft.dirty,
        modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Discard cart") }
    if (confirmDiscard) AlertDialog(onDismissRequest = { if (!busy) confirmDiscard = false },
        title = { Text("Discard ${cart.name}?") }, text = { Text("This removes the unfinished bill. Stock stays unchanged.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; discard() }, enabled = !busy) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep cart") } })
}

@Composable
internal fun CartProductPicker(shop: ShopSnapshot, cart: Cart, busy: Boolean, error: ShopError?, loadPhoto: PhotoLoader,
    onChange: () -> Unit, add: (String, String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var limit by rememberSaveable(query) { mutableIntStateOf(30) }
    val search = remember(shop) { CatalogSearch(shop) }
    val results = remember(search, query) { search.find(ProductFilter(query = query)) }
    val product = shop.products.find { it.id == selected }
    val selectedView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected != null) selectedView.bringIntoView() }
    if (product != null) Column(Modifier.bringIntoViewRequester(selectedView)) { ShopSection("Selected product") {
        Text(product.title, style = MaterialTheme.typography.titleLarge, maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(product.sku + " · " + shop.categories.single { it.id == product.categoryId }.name,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StockHealthIndicator(product)
        Text("On hand: ${indianNumber(product.quantity)} pieces")
        Text("Other carts: ${shop.otherCartDemand(cart.id, product.id)} pieces")
        val line = cart.lines.find { it.productId == product.id }
        Text("${if (line == null) "Unit price" else "Retained quote"}: ₹${indianNumber(line?.quotedPriceRupees ?: product.priceRupees)}",
            style = MaterialTheme.typography.titleMedium)
        if (line != null) Text("Already in this cart: ${indianNumber(line.quantity)} pieces",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(quantity, { quantity = it; onChange() }, label = { Text("Pieces to add") }, enabled = !busy,
            isError = error?.field == ProductField.CartQuantity,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            modifier = Modifier.fillMaxWidth())
        if (error != null) FormError(error.message)
        ShopPrimaryButton("Add to cart", enabled = !busy, onClick = { add(product.id, quantity) })
    } }
    ShopSearchField(query, { query = it; selected = null; onChange() }, "Search products for cart", enabled = !busy)
    if (results.isEmpty()) Text("No matching products", style = MaterialTheme.typography.bodyMedium)
    else {
        ShopSectionHeader("Products", supporting = "${results.size} matching")
        ShopGroup(spacing = ShopSpacing.small) {
            results.take(limit).forEach { item ->
                StockProductRow(shop, item, loadPhoto, enabled = !busy) { selected = item.id; quantity = "1"; onChange() }
            }
        }
    }
    if (results.size > limit) OutlinedButton(onClick = { limit += 30 }, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Show more products") }
}
