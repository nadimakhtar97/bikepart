package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing
import java.text.DateFormat
import java.util.Date
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


@Composable
internal fun CashCheckout(shop: ShopSnapshot, cart: Cart, busy: Boolean, error: ShopError?,
    onChange: () -> Unit, edit: () -> Unit, complete: (String) -> Unit) {
    var cash by rememberSaveable(cart.id) { mutableStateOf("") }
    ShopPageHeader(stringResource(R.string.cart_3, cart.number), stringResource(R.string.review_the_quoted_lines_then_record))
    ShopGroup(spacing = ShopSpacing.small) {
        if (cart.customer.isNotEmpty()) Text(cart.customer, style = MaterialTheme.typography.titleMedium)
        cart.lines.forEach { line ->
            val product = shop.products.single { it.id == line.productId }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(product.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(stringResource(R.string.pieces_2, indianNumber(line.quantity), indianNumber(line.quotedPriceRupees), indianNumber(line.totalRupees)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.cart_total, indianNumber(cart.totalRupees)), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    ShopGroup(stringResource(R.string.cash_payment)) {
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
        Text(if (tender != null && tender >= cart.totalRupees) stringResource(R.string.change, indianNumber(Math.subtractExact(tender, cart.totalRupees))) else stringResource(R.string.enter_enough_cash_to_calculate_change),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium)

    }
    ShopPrimaryButton(if (busy) stringResource(R.string.saving_sale) else stringResource(R.string.complete_cash_sale), enabled = !busy, onClick = { complete(cash) })
    TextButton(onClick = edit, enabled = !busy, modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.edit_cart)) }
    Text(stringResource(R.string.the_whole_sale_is_saved_on_this), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
