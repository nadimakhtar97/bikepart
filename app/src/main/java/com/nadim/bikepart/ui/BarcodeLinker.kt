package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.BarcodeTarget
import com.nadim.bikepart.shop.BarcodeTargetKind
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.shop.barcodeTargetLabel
import com.nadim.bikepart.shop.indianNumber
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
    ShopSection(stringResource(R.string.barcode_link_section)) {
        Text(stringResource(R.string.exact_barcode, code))
        Text(stringResource(R.string.barcode_link_note))
        owner?.let { Text(stringResource(R.string.current_target, shop.barcodeTargetLabel(it.target))) }
        ShopTextField(
            query, { query = it; selectedId = null; onChange() },
            stringResource(R.string.barcode_find_target), enabled = !busy
        )
        val products = shop.products.filter { !it.archived && (it.title.contains(query, true) || it.sku.contains(query, true)) }
        val templates = shop.templates.filter { !it.retired && it.catalog.title.contains(query, true) }
        val targets = products.map { BarcodeTarget(BarcodeTargetKind.Product, it.id) to "Product: ${it.title}" } +
            templates.map { BarcodeTarget(BarcodeTargetKind.Template, it.id) to "Template: ${it.catalog.title}" }
        if (targets.isNotEmpty()) ShopListGroup(showDividers = true) {
            targets.take(limit).forEachIndexed { index, (target, label) ->
                val supporting: String
                val meta: String?
                val status: (@Composable () -> Unit)?
                val icon: Int
                if (target.kind == BarcodeTargetKind.Product) {
                    val product = products.single { it.id == target.id }
                    supporting = shop.categories.single { it.id == product.categoryId }.name
                    meta = stringResource(
                        R.string.pieces_3,
                        indianNumber(product.quantity),
                        indianNumber(product.priceRupees)
                    )
                    icon = R.drawable.ic_stock
                    status = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            StockHealthIndicator(product)
                            Text(
                                product.sku, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val template = templates.single { it.id == target.id }
                    supporting =
                        shop.categories.single { it.id == template.catalog.categoryId }.name
                    meta = null
                    icon = R.drawable.ic_template
                    status = null
                }
                ShopListRow(
                    title = label,
                    supporting = supporting,
                    meta = meta,
                    icon = icon,
                    status = status,
                    selected = target == selected,
                    role = androidx.compose.ui.semantics.Role.RadioButton,
                    actionLabel = "Select $label",
                    enabled = !busy,
                    onClick = {
                        selectedId = target.id; selectedKind = target.kind.name; confirmedRevision =
                        owner?.revision; onChange()
                    },
                    dividerAbove = index > 0,
                )
            }
        }
        if (targets.size > limit) ShopSecondaryButton(onClick = { limit += 30 }, enabled = !busy) { Text(stringResource(R.string.barcode_show_more)) }
        if (targets.isEmpty()) Text(stringResource(R.string.barcode_no_targets))
        selected?.let { target ->
            Text(stringResource(R.string.selected_target, shop.barcodeTargetLabel(target)))
            if (conflict) FormError("Conflict: this barcode already belongs to ${shop.barcodeTargetLabel(requireNotNull(owner).target)}. Confirm moving it to ${shop.barcodeTargetLabel(target)}.")
            if (error != null) FormError(error.message)
            ShopPrimaryButton(if (conflict) "Confirm reassignment" else "Save barcode link", enabled = !busy) {
                save(target, confirmedRevision)
            }
        }
        ShopSecondaryButton(onClick = cancel, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.barcode_cancel_link)) }
    }
}
