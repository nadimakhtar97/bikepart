package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun BarcodeLinker(shop: ShopSnapshot, code: String, busy: Boolean, error: ShopError?,
    cancel: () -> Unit, onChange: () -> Unit, save: (BarcodeTarget, Long?) -> Unit) {
    var query by rememberSaveable(code) { mutableStateOf("") }
    var selectedId by rememberSaveable(code) { mutableStateOf<String?>(null) }
    var selectedKind by rememberSaveable(code) { mutableStateOf(BarcodeTargetKind.Product.name) }
    var confirmedRevision by rememberSaveable(code) { mutableStateOf<Long?>(null) }
    var limit by rememberSaveable(code, query) { mutableIntStateOf(30) }
    val selected = selectedId?.let { BarcodeTarget(BarcodeTargetKind.valueOf(selectedKind), it) }
    val owner = shop.barcodeAliases.find { it.namespace == "external" && it.code == code }
    val conflict = owner != null && selected != null && owner.target != selected
    ShopSection("Link external barcode") {
        Text("Exact barcode: $code")
        Text("Linking changes how future scans resolve. It does not change stock or prices.")
        owner?.let { Text("Current target: ${shop.barcodeTargetLabel(it.target)}") }
        OutlinedTextField(query, { query = it; selectedId = null; onChange() }, label = { Text("Find barcode target") },
            enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
        val products = shop.products.filter { !it.archived && (it.title.contains(query, true) || it.sku.contains(query, true)) }
        val templates = shop.templates.filter { !it.retired && it.catalog.title.contains(query, true) }
        val targets = products.map { BarcodeTarget(BarcodeTargetKind.Product, it.id) to "Product: ${it.title}" } +
            templates.map { BarcodeTarget(BarcodeTargetKind.Template, it.id) to "Template: ${it.catalog.title}" }
        for ((target, label) in targets.take(limit)) {
            ShopSecondaryButton(onClick = { selectedId = target.id; selectedKind = target.kind.name; confirmedRevision = owner?.revision; onChange() },
                enabled = !busy, shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (target == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (target == selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget).semantics { this.selected = target == selected }) {
                Column {
                    Text(label)
                    if (target.kind == BarcodeTargetKind.Product) {
                        val product = products.single { it.id == target.id }
                        Text(shop.categories.single { it.id == product.categoryId }.name)
                        Text("${indianNumber(product.quantity)} pieces · ₹${indianNumber(product.priceRupees)}")
                        StockHealthIndicator(product)
                        Text(product.sku)
                    } else {
                        val template = templates.single { it.id == target.id }
                        Text(shop.categories.single { it.id == template.catalog.categoryId }.name)
                    }
                }
            }
        }
        if (targets.size > limit) ShopSecondaryButton(onClick = { limit += 30 }, enabled = !busy) { Text("Show more barcode targets") }
        if (targets.isEmpty()) Text("No active targets found. Use manual search to create a product or template first.")
        selected?.let { target ->
            Text("Selected target: ${shop.barcodeTargetLabel(target)}")
            if (conflict) FormError("Conflict: this barcode already belongs to ${shop.barcodeTargetLabel(requireNotNull(owner).target)}. Confirm moving it to ${shop.barcodeTargetLabel(target)}.")
            if (error != null) FormError(error.message)
            ShopPrimaryButton(if (conflict) "Confirm reassignment" else "Save barcode link", enabled = !busy) {
                save(target, confirmedRevision)
            }
        }
        ShopSecondaryButton(onClick = cancel, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Cancel barcode link") }
    }
}
