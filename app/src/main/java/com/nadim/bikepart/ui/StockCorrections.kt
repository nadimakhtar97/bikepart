package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.CorrectionKind
import com.nadim.bikepart.shop.Product
import com.nadim.bikepart.shop.ProductField
import com.nadim.bikepart.shop.StockCorrection
import com.nadim.bikepart.shop.indianNumber
import java.text.DateFormat
import java.util.Date

@Composable
internal fun StockCorrectionForm(product: Product, kind: CorrectionKind, busy: Boolean, error: ShopError?, onChange: () -> Unit, save: (String, String) -> Unit) {
    var quantity by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    // The app bar already names the correction, so the group starts with the product.
    ShopGroup {
        Text(product.title, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.current_stock_pieces, indianNumber(product.quantity)), style = MaterialTheme.typography.bodyMedium)
        Text(if (kind == CorrectionKind.PhysicalCount) "Enter the total pieces counted. The difference is calculated against stock when saved." else "Enter the pieces to remove for ${kind.label.lowercase()}.", style = MaterialTheme.typography.bodyMedium)
        ShopTextField(
            quantity,
            { quantity = it; onChange() },
            label = if (kind == CorrectionKind.PhysicalCount) "Counted pieces" else "Pieces to remove",
            enabled = !busy,
            error = error?.takeIf { it.field == ProductField.CorrectionQuantity }?.message,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(reason, { reason = it; onChange() }, enabled = !busy, label = { Text(stringResource(R.string.correction_reason)) },
            isError = error?.field == ProductField.CorrectionReason, modifier = Modifier.fillMaxWidth())
        if (error != null && error.field != ProductField.CorrectionQuantity) FormError(error.message)
        ShopPrimaryButton(stringResource(R.string.correction_save), enabled = !busy, onClick = { save(quantity, reason) })
    }
}

@Composable
internal fun CorrectionHistory(corrections: List<StockCorrection>) {
    if (corrections.isEmpty()) return
    ShopSectionHeader(stringResource(R.string.correction_history))
    ShopListGroup(showDividers = true) {
        corrections.asReversed().forEachIndexed { index, event ->
            val movement = event.movement
            ShopListRow(
                title = event.kind.label + " · " + DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT
                ).format(Date(event.recordedAt)),
                supporting = event.reason,
                status = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(
                                R.string.arrival_stock_change,
                                indianNumber(event.previousBalance),
                                indianNumber(event.resultingBalance)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            when {
                                movement == null -> "No stock change · counted ${indianNumber(event.quantity)} pieces"
                                movement < 0 -> "Removed ${indianNumber(-movement)} pieces"
                                else -> "Added ${indianNumber(movement)} pieces"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                dividerAbove = index > 0,
            )
        }
    }
}
