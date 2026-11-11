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
import com.nadim.bikepart.CartDraft
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun CartList(shop: ShopSnapshot, busy: Boolean, onNew: () -> Unit, onOpen: (String) -> Unit) {
    ShopPrimaryButton("New sale", enabled = !busy, onClick = onNew)
    Text("Carts do not reserve stock.", style = MaterialTheme.typography.bodyMedium)
    if (shop.carts.isEmpty()) ShopEmptyState("No unfinished carts", "Start a sale to create an automatically saved customer bill.", R.drawable.ic_carts)
    shop.carts.forEach { cart ->
        ShopSection(cart.name) {
            if (cart.customer.isNotEmpty()) Text(cart.customer, style = MaterialTheme.typography.titleMedium)
            Text("${cart.lines.size} product lines · ₹${indianNumber(cart.totalRupees)}", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { onOpen(cart.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Open ${cart.name}") }
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
    ShopSection(cart.name) {
        // Autosave buffers typing while a commit is pending; structural actions wait.
        OutlinedTextField(draft.customer, { edit(it, null, null) }, label = { Text("Customer label (optional)") },
            modifier = Modifier.fillMaxWidth())
        Text(when { busy -> "Saving cart…"; error != null -> "Changes not saved"; draft.dirty -> "Unsaved changes…"; else -> "Saved on this phone" },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        Text("On-hand stock and demand in other carts are separate. Carts do not reserve stock.", style = MaterialTheme.typography.bodyMedium)
    }
    if (error != null) Column(Modifier.bringIntoViewRequester(errorView)) {
        FormError(error.message)
        OutlinedButton(onClick = retry, enabled = !busy) { Text("Retry saving cart") }
        OutlinedButton(onClick = reload, enabled = !busy) { Text("Reload saved cart") }
    }
    if (cart.lines.isEmpty()) Text("No products in this cart. Add a product to begin.")
    cart.lines.forEach { line ->
        val product = shop.products.single { it.id == line.productId }
        val quantityError = error?.field == ProductField.CartQuantity && error.definitionId == product.id
        val quantityView = remember(product.id) { BringIntoViewRequester() }
        LaunchedEffect(error) { if (quantityError) quantityView.bringIntoView() }
        ShopSection(product.title) {
            Text(product.sku, style = MaterialTheme.typography.bodyMedium)
            Text("On hand: ${indianNumber(product.quantity)} pieces", style = MaterialTheme.typography.bodyMedium)
            Text("Other carts: ${shop.otherCartDemand(cart.id, product.id)} pieces", style = MaterialTheme.typography.bodyMedium)
            Text("Quoted unit price: ₹${indianNumber(line.quotedPriceRupees)}", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(draft.quantities[product.id].orEmpty(), { edit(null, product.id, it) },
                label = { Text("Pieces") }, singleLine = true,
                isError = quantityError, supportingText = if (quantityError) { { Text("Enter a positive whole-piece quantity.") } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().bringIntoViewRequester(quantityView).semantics { contentDescription = "Pieces · ${product.title}" })
            Text("Saved line total: ₹${indianNumber(line.totalRupees)}", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { remove(product.id) }, enabled = !busy && !draft.dirty, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget).semantics { contentDescription = "Remove ${product.title}" }) { Text("Remove line") }
        }
    }
    Text("Saved cart total: ₹${indianNumber(cart.totalRupees)}", style = MaterialTheme.typography.headlineSmall)
    ShopPrimaryButton("Add products", enabled = !busy && !draft.dirty, onClick = add)
    ShopPrimaryButton("Checkout", enabled = !busy && cart.lines.isNotEmpty(), onClick = checkout)
    OutlinedButton(onClick = pause, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Save and return to carts") }
    OutlinedButton(onClick = { confirmDiscard = true }, enabled = !busy && !draft.dirty, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Discard cart") }
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
        Text(product.title, style = MaterialTheme.typography.titleMedium)
        Text(product.sku, style = MaterialTheme.typography.bodyMedium)
        Text(shop.categories.single { it.id == product.categoryId }.name)
        StockHealthIndicator(product)
        Text("On hand: ${indianNumber(product.quantity)} pieces")
        Text("Other carts: ${shop.otherCartDemand(cart.id, product.id)} pieces")
        val line = cart.lines.find { it.productId == product.id }
        Text("${if (line == null) "Unit price" else "Retained quote"}: ₹${indianNumber(line?.quotedPriceRupees ?: product.priceRupees)}")
        if (line != null) Text("Already in this cart: ${indianNumber(line.quantity)} pieces")
        OutlinedTextField(quantity, { quantity = it; onChange() }, label = { Text("Pieces to add") }, enabled = !busy,
            isError = error?.field == ProductField.CartQuantity,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error != null) FormError(error.message)
        ShopPrimaryButton("Add to cart", enabled = !busy, onClick = { add(product.id, quantity) })
    } }
    OutlinedTextField(query, { query = it; selected = null; onChange() }, label = { Text("Search products for cart") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
    if (results.isEmpty()) Text("No matching products")
    results.take(limit).forEach { item ->
        StockProductRow(shop, item, loadPhoto, enabled = !busy) { selected = item.id; quantity = "1"; onChange() }
    }
    if (results.size > limit) OutlinedButton(onClick = { limit += 30 }) { Text("Show more products") }
}
