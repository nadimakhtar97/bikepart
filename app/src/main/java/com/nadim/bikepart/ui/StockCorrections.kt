package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
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
    ShopSection(stringResource(R.string.correction_history)) {
        corrections.asReversed().forEach { event ->
            Text(event.kind.label + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(event.recordedAt)), style = MaterialTheme.typography.titleMedium)
            Text(event.reason, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.arrival_stock_change, indianNumber(event.previousBalance), indianNumber(event.resultingBalance)), style = MaterialTheme.typography.bodyMedium)
            val movement = event.movement
            Text(when {
                movement == null -> "No stock change · counted ${indianNumber(event.quantity)} pieces"
                movement < 0 -> "Removed ${indianNumber(-movement)} pieces"
                else -> "Added ${indianNumber(movement)} pieces"
            }, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
        }
    }
}
