package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.Cart
import com.nadim.bikepart.shop.ProductField
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.shop.indianNumber
import com.nadim.bikepart.ui.theme.ShopSpacing


@Composable
internal fun CashCheckout(shop: ShopSnapshot, cart: Cart, busy: Boolean, error: ShopError?,
    onChange: () -> Unit, edit: () -> Unit, complete: (String) -> Unit) {
    var cash by rememberSaveable(cart.id) { mutableStateOf("") }
    val errorView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null) errorView.bringIntoView() }
    ShopPageHeader(cart.name, "Review the quoted lines, then record the cash received.")
    ShopGroup(spacing = ShopSpacing.small) {
        if (cart.customer.isNotEmpty()) Text(cart.customer, style = MaterialTheme.typography.titleMedium)
        ShopListGroup(showDividers = true) {
            cart.lines.forEachIndexed { index, line ->
                val product = shop.products.single { it.id == line.productId }
                ShopListRow(
                    product.title,
                    supporting = stringResource(R.string.pieces, indianNumber(line.quantity)),
                    meta = stringResource(
                        R.string.pieces_2,
                        indianNumber(line.quantity),
                        indianNumber(line.quotedPriceRupees),
                        indianNumber(line.totalRupees)
                    ),
                    dividerAbove = index > 0,
                    minHeight = 72.dp
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.cart_total, indianNumber(cart.totalRupees)), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    ShopGroup("Cash payment") {
        ShopTextField(
            value = cash,
            onValueChange = { cash = it; onChange() },
            label = stringResource(R.string.cash_received),
            enabled = !busy,
            error = error?.takeIf { it.field == ProductField.CashReceived }?.message,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ShopSecondaryButton(onClick = { cash = cart.totalRupees.toString(); onChange() }, enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.exact_amount)) }
        val tender = cash.trim().takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull()
        Text(if (tender != null && tender >= cart.totalRupees) "Change: ₹${indianNumber(Math.subtractExact(tender, cart.totalRupees))}" else "Enter enough cash to calculate change.",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium)
        if (error != null && error.field != ProductField.CashReceived) Column(
            Modifier.bringIntoViewRequester(
                errorView
            )
        ) { FormError(error.message) }
    }
    ShopPrimaryButton(if (busy) "Saving sale…" else "Complete cash sale", enabled = !busy, onClick = { complete(cash) })
    TextButton(onClick = edit, enabled = !busy, modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.edit_cart)) }
    Text(stringResource(R.string.the_whole_sale_is_saved_on_this), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
