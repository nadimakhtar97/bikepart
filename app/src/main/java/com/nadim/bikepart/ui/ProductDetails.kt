package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.CorrectionKind
import com.nadim.bikepart.shop.Product
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.shop.appliesTo
import com.nadim.bikepart.shop.indianNumber
import com.nadim.bikepart.shop.missingRequired
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun ProductDetails(shop: ShopSnapshot, product: Product, onEdit: () -> Unit, onManage: () -> Unit, loadPhoto: PhotoLoader,
    busy: Boolean, onCopy: () -> Unit, onTemplate: () -> Unit, onCorrection: (CorrectionKind) -> Unit, onArchive: () -> Unit,
    archiveError: String?, onReceive: () -> Unit, onLabels: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // At large system text the photo would squeeze the title into a narrow column.
    val stackHeader = LocalDensity.current.fontScale >= 1.5f
    val summary: @Composable ColumnScope.() -> Unit = {
        Text(product.title, style = MaterialTheme.typography.titleLarge)
        Text(shop.categories.single { it.id == product.categoryId }.name, style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.pieces, indianNumber(product.quantity)), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.selling_price, indianNumber(product.priceRupees)), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        if (product.archived) Text(stringResource(R.string.product_archived_label), style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        else StockHealthIndicator(product)
    }
    Surface(shape = MaterialTheme.shapes.medium, color = colors.primary.copy(alpha = .08f).compositeOver(colors.surface)) {
        if (stackHeader) Column(Modifier
            .fillMaxWidth()
            .padding(ShopSpacing.content),
            verticalArrangement = Arrangement.spacedBy(ShopSpacing.medium)) {
            ProductPhoto(product.photo, loadPhoto, Modifier.size(96.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = summary)
        } else Row(Modifier
            .fillMaxWidth()
            .padding(ShopSpacing.content), horizontalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
            ProductPhoto(product.photo, loadPhoto, Modifier.size(104.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = summary)
        }
    }
    if (!product.archived) ShopPrimaryButton(stringResource(R.string.product_edit_details), enabled = !busy, onClick = onEdit)
    if (shop.missingRequired(product.id).isNotEmpty()) Text(stringResource(R.string.product_catalog_incomplete), style = MaterialTheme.typography.bodyMedium)
    if (!product.archived) {
        ShopSectionHeader(stringResource(R.string.product_correct_stock))
        ShopListGroup {
            CorrectionKind.entries.forEach { kind ->
                ShopListRow("Record ${kind.label.lowercase()}",
                    supporting = when (kind) {
                        CorrectionKind.PhysicalCount -> "Enter the total pieces counted"
                        CorrectionKind.Damage -> "Remove damaged pieces from stock"
                        CorrectionKind.Loss -> "Remove lost pieces from stock"
                    },
                    icon = R.drawable.ic_inventory, enabled = !busy, onClick = { onCorrection(kind) })
            }
        }
    }
    ShopSection(stringResource(R.string.product_details_section)) {
        Text(product.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodyLarge)
        shop.attributes.filter { it.appliesTo(product.categoryId, product.id) && (!it.retired || it.id in product.attributes) }.forEach { definition ->
            val value = product.attributes[definition.id]
            val display = if (value == null && definition.required && !definition.retired) "Required · missing" else attributeDisplay(shop, value)
            Text(definition.name + (if (definition.retired) " (retired)" else "") + ": " + display + (if (value != null && definition.unit.isNotBlank()) " " + definition.unit else ""), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(color = colors.outlineVariant)
        Text(stringResource(R.string.sku, product.sku), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.opening_stock_pieces, indianNumber(shop.openings[product.id]?.quantity ?: 0L)), style = MaterialTheme.typography.bodyMedium)
    }
    if (product.historicalAttributes.isNotEmpty()) ShopSection(stringResource(R.string.product_historical_attributes)) {
        product.historicalAttributes.forEach { (_, definitionId, value) ->
            val definition = shop.attributes.single { it.id == definitionId }
            Text(definition.name + ": " + attributeDisplay(shop, value) + " " + definition.unit, style = MaterialTheme.typography.bodyMedium)
        }
    }
    CorrectionHistory(shop.corrections.filter { it.productId == product.id })
    FindingSummary(shop, product)
    if (product.archived) ShopListRow(stringResource(R.string.product_preview_labels), supporting = "Package or shared-bin label",
        icon = R.drawable.ic_template, enabled = !busy, onClick = onLabels)
    if (!product.archived) {
        ShopSectionHeader(stringResource(R.string.product_actions))
        ShopListGroup {
            ShopListRow(stringResource(R.string.product_receive_stock), supporting = "Add received pieces and purchase cost",
                icon = R.drawable.ic_inventory, enabled = !busy, onClick = onReceive)
            ShopListRow(stringResource(R.string.product_preview_labels), supporting = "Package or shared-bin label",
                icon = R.drawable.ic_template, enabled = !busy, onClick = onLabels)
            ShopListRow(stringResource(R.string.scanner_copy_catalog), supporting = "Start a new product with these details",
                icon = R.drawable.ic_add, enabled = !busy, onClick = onCopy)
            ShopListRow(stringResource(R.string.product_create_template), supporting = "Reuse these details later",
                icon = R.drawable.ic_template, enabled = !busy, onClick = onTemplate)
            ShopListRow(
                stringResource(R.string.product_manage_fields),
                supporting = stringResource(R.string.product_manage_fields_supporting),
                icon = R.drawable.ic_settings, enabled = !busy, onClick = onManage)
        }
        ShopSection(stringResource(R.string.product_archive_section)) {
            Text(stringResource(R.string.product_archive_desc), style = MaterialTheme.typography.bodyMedium)
            if (archiveError != null) FormError(archiveError)
            ShopSecondaryButton(onClick = onArchive, enabled = !busy, modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.product_archive_btn)) }
        }
    }
}
