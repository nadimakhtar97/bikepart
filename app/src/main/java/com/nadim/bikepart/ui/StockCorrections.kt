package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    ShopSection(kind.label) {
        Text(product.title, style = MaterialTheme.typography.titleMedium)
        Text("Current stock: ${indianNumber(product.quantity)} pieces", style = MaterialTheme.typography.bodyMedium)
        Text(if (kind == CorrectionKind.PhysicalCount) "Enter the total pieces counted. The difference is calculated against stock when saved." else "Enter the pieces to remove for ${kind.label.lowercase()}.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(quantity, { quantity = it; onChange() }, enabled = !busy,
            label = { Text(if (kind == CorrectionKind.PhysicalCount) "Counted pieces" else "Pieces to remove") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            isError = error?.field == ProductField.CorrectionQuantity, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(reason, { reason = it; onChange() }, enabled = !busy, label = { Text("Reason") },
            isError = error?.field == ProductField.CorrectionReason, modifier = Modifier.fillMaxWidth())
        if (error != null) FormError(error.message)
        ShopPrimaryButton("Save correction", enabled = !busy, onClick = { save(quantity, reason) })
    }
}

@Composable
internal fun CorrectionHistory(corrections: List<StockCorrection>) {
    if (corrections.isEmpty()) return
    ShopSection("Stock correction history") {
        corrections.asReversed().forEach { event ->
            Text(event.kind.label + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(event.recordedAt)), style = MaterialTheme.typography.titleMedium)
            Text(event.reason, style = MaterialTheme.typography.bodyMedium)
            Text("${indianNumber(event.previousBalance)} → ${indianNumber(event.resultingBalance)} pieces", style = MaterialTheme.typography.bodyMedium)
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
