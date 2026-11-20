package com.nadim.bikepart.ui

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
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.Product
import com.nadim.bikepart.shop.ProductField
import com.nadim.bikepart.shop.StockArrival
import com.nadim.bikepart.shop.indianNumber

@Composable
internal fun StockArrivalForm(product: Product, busy: Boolean, error: ShopError?, onChange: () -> Unit, save: (String, String) -> Unit) {
    var pieces by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    ShopSection(stringResource(R.string.arrival_details)) {
        Text(product.title, style = MaterialTheme.typography.titleMedium)
        Text(product.sku)
        Text(stringResource(R.string.current_stock_pieces, indianNumber(product.quantity)))
        Text(stringResource(R.string.arrival_save_note))
        ShopTextField(
            pieces,
            { pieces = it; onChange() },
            stringResource(R.string.arrival_received_pieces),
            enabled = !busy,
            error = error?.takeIf { it.field == ProductField.ArrivalQuantity }?.message,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(cost, { cost = it; onChange() }, enabled = !busy, label = { Text(stringResource(R.string.arrival_purchase_cost)) },
            isError = error?.field == ProductField.PurchaseCost, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (error != null && error.field !in setOf(
                ProductField.ArrivalQuantity,
                ProductField.PurchaseCost
            )
        ) FormError(error.message)
        ShopPrimaryButton(stringResource(R.string.arrival_save), enabled = !busy) { save(pieces, cost) }
    }
}

@Composable
internal fun ArrivalHistory(arrivals: List<StockArrival>) {
    if (arrivals.isEmpty()) return
    ShopSectionHeader(stringResource(R.string.arrival_history))
    ShopListGroup(showDividers = true) {
        arrivals.asReversed().forEachIndexed { index, arrival ->
            ShopListRow(
                title = java.text.DateFormat.getDateTimeInstance()
                    .format(java.util.Date(arrival.recordedAt)),
                supporting = stringResource(
                    R.string.arrival_received_summary,
                    indianNumber(arrival.quantity),
                    indianNumber(arrival.unitCostRupees)
                ),
                status = {
                    Text(
                        stringResource(
                            R.string.arrival_stock_change,
                            indianNumber(arrival.previousBalance),
                            indianNumber(arrival.resultingBalance)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                dividerAbove = index > 0,
            )
        }
    }
}
