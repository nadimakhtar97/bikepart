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

@Composable
internal fun StockArrivalForm(product: Product, busy: Boolean, error: ShopError?, onChange: () -> Unit, save: (String, String, String?) -> Unit) {
    var pieces by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf(product.buyingPriceRupees.toString()) }
    val initialSellingPrice = rememberSaveable { product.priceRupees.toString() }
    var sellingPrice by rememberSaveable { mutableStateOf(initialSellingPrice) }
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
        ShopTextField(
            cost,
            { cost = it; onChange() },
            stringResource(R.string.form_buying_price_label),
            enabled = !busy,
            error = error?.takeIf { it.field == ProductField.PurchaseCost }?.message,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        ShopTextField(
            sellingPrice,
            { sellingPrice = it; onChange() },
            stringResource(R.string.form_selling_price_label),
            enabled = !busy,
            error = error?.takeIf { it.field == ProductField.SellingPrice }?.message,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        ShopPrimaryButton(stringResource(R.string.arrival_save), enabled = !busy) {
            save(pieces, cost, sellingPrice.takeUnless { it == initialSellingPrice })
        }
    }
}

@Composable
internal fun ArrivalHistory(arrivals: List<StockArrival>) {
    if (arrivals.isEmpty()) return
    ShopSection(stringResource(R.string.arrival_history)) {
        arrivals.asReversed().forEach {
            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.recordedAt)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.arrival_received_summary, indianNumber(it.quantity), indianNumber(it.unitCostRupees)))
            Text(stringResource(R.string.arrival_stock_change, indianNumber(it.previousBalance), indianNumber(it.resultingBalance)))
        }
    }
}
