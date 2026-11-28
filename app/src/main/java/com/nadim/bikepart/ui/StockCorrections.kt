package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
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
        Text(if (kind == CorrectionKind.PhysicalCount) stringResource(R.string.correction_physical_count_note) else stringResource(R.string.correction_remove_note, stringResource(kind.label).lowercase()), style = MaterialTheme.typography.bodyMedium)
        ShopTextField(
            quantity,
            { quantity = it; onChange() },
            label = if (kind == CorrectionKind.PhysicalCount) stringResource(R.string.correction_counted_pieces) else stringResource(R.string.correction_pieces_to_remove),
            enabled = !busy,
            error = error?.takeIf { it.field == ProductField.CorrectionQuantity }?.message,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        ShopTextField(
            reason,
            { reason = it; onChange() },
            stringResource(R.string.correction_reason),
            enabled = !busy,
            error = error?.takeIf { it.field == ProductField.CorrectionReason }?.message,
            singleLine = false,
        )
        ShopPrimaryButton(stringResource(R.string.correction_save), enabled = !busy, onClick = { save(quantity, reason) })
    }
}

@Composable
internal fun CorrectionHistory(corrections: List<StockCorrection>) {
    if (corrections.isEmpty()) return
    ShopSection(stringResource(R.string.correction_history)) {
        corrections.asReversed().forEach { event ->
            Text(stringResource(R.string.formatted_text_2, stringResource(event.kind.label), DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(event.recordedAt))), style = MaterialTheme.typography.titleMedium)
            Text(event.reason, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.arrival_stock_change, indianNumber(event.previousBalance), indianNumber(event.resultingBalance)), style = MaterialTheme.typography.bodyMedium)
            val movement = event.movement
            Text(when {
                movement == null -> stringResource(R.string.correction_no_stock_change, indianNumber(event.quantity))
                movement < 0 -> stringResource(R.string.correction_removed_pieces, indianNumber(-movement))
                else -> stringResource(R.string.correction_added_pieces, indianNumber(movement))
            }, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
        }
    }
}
