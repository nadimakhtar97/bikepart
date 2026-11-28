package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun BarcodeLinker(shop: ShopSnapshot, code: String, busy: Boolean,
    cancel: () -> Unit, onChange: () -> Unit, save: (BarcodeTarget, Long?) -> Unit) {
    var query by rememberSaveable(code) { mutableStateOf("") }
    var selectedId by rememberSaveable(code) { mutableStateOf<String?>(null) }
    var selectedKind by rememberSaveable(code) { mutableStateOf(BarcodeTargetKind.Product.name) }
    var confirmedRevision by rememberSaveable(code) { mutableStateOf<Long?>(null) }
    var limit by rememberSaveable(code, query) { mutableIntStateOf(30) }
    val selected = selectedId?.let { BarcodeTarget(BarcodeTargetKind.valueOf(selectedKind), it) }
    val owner = shop.barcodeAliases.find { it.namespace == "external" && it.code == code }
    val conflict = owner != null && selected != null && owner.target != selected
    ShopSection(stringResource(R.string.barcode_link_section)) {
        Text(stringResource(R.string.exact_barcode, code))
        Text(stringResource(R.string.barcode_link_note))
        owner?.let { Text(stringResource(R.string.current_target, shop.barcodeTargetLabel(it.target))) }
        ShopSearchField(
            query, { query = it; selectedId = null; onChange() },
            stringResource(R.string.barcode_find_target), enabled = !busy
        )
        val products = shop.products.filter { !it.archived && (it.title.contains(query, true) || it.sku.contains(query, true)) }
        val templates = shop.templates.filter { !it.retired && it.catalog.title.contains(query, true) }
        val targets = products.map { BarcodeTarget(BarcodeTargetKind.Product, it.id) to stringResource(R.string.product, it.title) } +
            templates.map { BarcodeTarget(BarcodeTargetKind.Template, it.id) to stringResource(R.string.template, it.catalog.title) }
        for ((target, label) in targets.take(limit)) {
            ShopSecondaryButton(onClick = { selectedId = target.id; selectedKind = target.kind.name; confirmedRevision = owner?.revision; onChange() },
                enabled = !busy, shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (target == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (target == selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ShopSpacing.touchTarget)
                    .semantics { this.selected = target == selected }) {
                Column {
                    Text(label)
                    if (target.kind == BarcodeTargetKind.Product) {
                        val product = products.single { it.id == target.id }
                        Text(shop.categories.single { it.id == product.categoryId }.name)
                        Text(stringResource(R.string.pieces_3, indianNumber(product.quantity), indianNumber(product.priceRupees)))
                        StockHealthIndicator(product)
                        Text(product.sku)
                    } else {
                        val template = templates.single { it.id == target.id }
                        Text(shop.categories.single { it.id == template.catalog.categoryId }.name)
                    }
                }
            }
        }
        if (targets.size > limit) ShopSecondaryButton(onClick = { limit += 30 }, enabled = !busy) { Text(stringResource(R.string.barcode_show_more)) }
        if (targets.isEmpty()) Text(stringResource(R.string.barcode_no_targets))
        selected?.let { target ->
            Text(stringResource(R.string.selected_target, shop.barcodeTargetLabel(target)))
            if (conflict) Text(stringResource(R.string.conflict_this_barcode_already_belongs_to, shop.barcodeTargetLabel(requireNotNull(owner).target), shop.barcodeTargetLabel(target)))
            ShopPrimaryButton(if (conflict) stringResource(R.string.barcode_confirm_reassignment) else stringResource(R.string.barcode_save_link), enabled = !busy) {
                save(target, confirmedRevision)
            }
        }
        ShopSecondaryButton(onClick = cancel, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.barcode_cancel_link)) }
    }
}

@Composable
internal fun ShopSnapshot.barcodeTargetLabel(target: BarcodeTarget): String = when (target.kind) {
    BarcodeTargetKind.Product -> products.find { it.id == target.id }?.let {
        stringResource(R.string.formatted_text_2, it.title, it.sku)
    } ?: target.id
    BarcodeTargetKind.Template -> templates.find { it.id == target.id }?.let {
        stringResource(R.string.template, it.catalog.title)
    } ?: target.id
}
