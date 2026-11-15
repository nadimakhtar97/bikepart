package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.*
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*

enum class ScanPurpose(val label: String) { Sale("Sale"), Receiving("Receive stock"), Catalog("Create product") }

@Composable
internal fun ContextualScanner(shop: ShopSnapshot, purpose: ScanPurpose, busy: Boolean, error: ShopError?,
    camera: ScanCameraContent, manual: () -> Unit, onChange: () -> Unit, add: (String, String) -> Unit,
    receive: (String) -> Unit, open: (String) -> Unit, copy: (CatalogDraft) -> Unit, cart: Cart? = null,
    link: (String, BarcodeTarget, Long?, () -> Unit) -> Unit) {
    var raw by rememberSaveable { mutableStateOf<String?>(null) }
    var transportError by rememberSaveable { mutableStateOf<String?>(null) }
    // Opening any contextual scan route starts the camera; cancel/pause still requires an explicit resume.
    var running by rememberSaveable { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var started by rememberSaveable { mutableStateOf(true) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var linking by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { running = false; attempt++ } }
    val resolution = raw?.let { runCatching { shop.resolveScan(it) } }
    if (linking && raw != null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { BarcodeLinker(shop, requireNotNull(raw), busy, error, cancel = { linking = false; onChange() }, onChange = onChange,
            save = { target, revision -> link(requireNotNull(raw), target, revision) { linking = false } }) }
        return
    }
    Column(Modifier.fillMaxSize()) {
    if (running) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(attempt) {
                val currentAttempt = attempt
                camera({ value ->
                    if (running && attempt == currentAttempt) { running = false; raw = value; transportError = null; quantity = ""; onChange() }
                }, { message ->
                    if (running && attempt == currentAttempt) { running = false; transportError = message; raw = null }
                })
            }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            ShopPrimaryButton("Manual search", enabled = !busy) { running = false; attempt++; manual() }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ShopSecondaryButton(onClick = { attempt++; manual() }, enabled = !busy) { Text("Manual search") }
        if (started) Text("Recognition paused", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        transportError?.let { FormError(it) }
        resolution?.exceptionOrNull()?.message?.let { FormError(it) }
        raw?.takeIf { it.isNotEmpty() && !it.startsWith("bikepart:", ignoreCase = true) }?.let { code ->
            ShopSecondaryButton(onClick = { linking = true; onChange() }, enabled = !busy,
                modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget)) { Text("Link barcode") }
            for (assignment in shop.barcodeHistory.filter { it.namespace == "external" && it.code == code }) {
                Text("Barcode history · revision ${assignment.revision}: ${assignment.previousTarget?.let { shop.barcodeTargetLabel(it) } ?: "Unassigned"} → ${shop.barcodeTargetLabel(assignment.target)}")
            }
        }
        when (val target = resolution?.getOrNull()) {
            is ScanTarget.StockedProduct -> ShopSection("Scanned product") {
                Text(target.product.title, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(target.product.sku)
                Text("On hand: ${indianNumber(target.product.quantity)} pieces")
                when (purpose) {
                    ScanPurpose.Sale -> {
                        val line = cart?.lines?.find { it.productId == target.product.id }
                        Text("Unit price: ₹${indianNumber(line?.quotedPriceRupees ?: target.product.priceRupees)}")
                        OutlinedTextField(quantity, { quantity = it; onChange() }, label = { Text("Pieces to add") },
                            enabled = !busy, isError = error?.field == ProductField.CartQuantity,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (error != null) FormError(error.message)
                        ShopPrimaryButton("Add to cart", enabled = !busy) { add(target.product.id, quantity) }
                    }
                    ScanPurpose.Receiving -> ShopPrimaryButton("Enter quantity and cost", enabled = !busy) { attempt++; receive(target.product.id) }
                    ScanPurpose.Catalog -> {
                        ShopPrimaryButton("Open existing", enabled = !busy) { attempt++; open(target.product.id) }
                        ShopSecondaryButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = { attempt++; copy(shop.copyCatalog(target.product.id)) }, enabled = !busy) { Text("Copy catalog details") }
                    }
                }
            }
            is ScanTarget.Template -> if (purpose == ScanPurpose.Catalog) ShopSection("Scanned template") {
                Text(target.template.catalog.title)
                ShopPrimaryButton("Use template details", enabled = !busy) { attempt++; copy(shop.prefillTemplate(target.template.id)) }
            } else FormError("Template codes only prefill new products. They cannot be sold or received.")
            null -> Unit
        }
        ShopSecondaryButton(modifier = Modifier.heightIn(min = com.nadim.bikepart.ui.theme.ShopSpacing.touchTarget), onClick = { attempt++; raw = null; transportError = null; quantity = ""; started = true; running = true; onChange() }, enabled = !busy) {
            Text(if (started) "Resume scanning" else "Start camera")
        }
        }
    }
    }
}
