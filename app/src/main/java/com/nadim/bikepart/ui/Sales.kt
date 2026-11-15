package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun CashCheckout(shop: ShopSnapshot, cart: Cart, busy: Boolean, error: ShopError?,
    onChange: () -> Unit, edit: () -> Unit, complete: (String) -> Unit) {
    var cash by rememberSaveable(cart.id) { mutableStateOf("") }
    val errorView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null) errorView.bringIntoView() }
    ShopPageHeader(cart.name, "Review the quoted lines, then record the cash received.")
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
    ShopGroup("Cash payment") {
        OutlinedTextField(cash, { cash = it; onChange() }, enabled = !busy,
            label = { Text(stringResource(R.string.cash_received)) }, singleLine = true,
            isError = error?.field == ProductField.CashReceived, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth())
        ShopSecondaryButton(onClick = { cash = cart.totalRupees.toString(); onChange() }, enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.exact_amount)) }
        val tender = cash.trim().takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull()
        Text(if (tender != null && tender >= cart.totalRupees) "Change: ₹${indianNumber(Math.subtractExact(tender, cart.totalRupees))}" else "Enter enough cash to calculate change.",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium)
        if (error != null) Column(Modifier.bringIntoViewRequester(errorView)) { FormError(error.message) }
    }
    ShopPrimaryButton(if (busy) "Saving sale…" else "Complete cash sale", enabled = !busy, onClick = { complete(cash) })
    TextButton(onClick = edit, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.edit_cart)) }
    Text(stringResource(R.string.the_whole_sale_is_saved_on_this), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SalesHistory(shop: ShopSnapshot, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("all") }
    var startDay by rememberSaveable { mutableStateOf<String?>(null) }
    var endDay by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingDates by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val from = when (period) {
        "today" -> today
        "week" -> today.with(java.time.DayOfWeek.MONDAY)
        "month" -> today.withDayOfMonth(1)
        "custom" -> startDay?.let(LocalDate::parse)
        else -> null
    }
    val until = if (period == "custom") endDay?.let(LocalDate::parse) else if (period == "all") null else today
    val days = remember(shop.sales, zone, from, until, query) { salesLog(shop.sales, zone, from, until, query) }
    ShopSearchField(query, { query = it }, stringResource(R.string.search_receipts))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        listOf("all" to R.string.all_time, "today" to R.string.today,
            "week" to R.string.this_week, "month" to R.string.this_month).forEach { (id, label) ->
            FilterChip(selected = period == id, onClick = { period = id },
                leadingIcon = if (period == id) ({ Icon(painterResource(R.drawable.ic_check), contentDescription = null) }) else null,
                label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget))
        }
        FilterChip(selected = period == "custom", onClick = { choosingDates = true },
            leadingIcon = { Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null) },
            label = { Text(stringResource(R.string.date_range)) }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget))
    }
    val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    if (period == "custom" && from != null && until != null) Text(
        from.format(dayFormat) + " – " + until.format(dayFormat), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(pluralStringResource(R.plurals.completed_sales, days.sumOf { it.sales.size }, days.sumOf { it.sales.size }),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    if (shop.sales.isEmpty()) ShopEmptyState(stringResource(R.string.no_completed_cash_sales_yet),
        "Completed sales and their receipts appear here after cash checkout.", R.drawable.ic_receipt)
    else if (days.isEmpty()) {
        ShopEmptyState(stringResource(R.string.no_matching_sales), stringResource(R.string.sales_filter_hint), R.drawable.ic_receipt)
        TextButton(onClick = { query = ""; period = "all" }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) {
            Text(stringResource(R.string.clear_sales_filters))
        }
    }
    days.forEach { day ->
        val heading = when (day.date) {
            today -> stringResource(R.string.today)
            today.minusDays(1) -> stringResource(R.string.yesterday)
            else -> day.date.format(dayFormat)
        }
        ShopSectionHeader(heading, supporting = pluralStringResource(R.plurals.sales_day_summary,
            day.sales.size, day.sales.size, groupedRupees(day.totalRupees)))
        ShopListGroup {
            day.sales.forEach { sale ->
                val time = Instant.ofEpochMilli(sale.recordedAt).atZone(zone)
                    .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                ShopListRow(stringResource(R.string.cart)+sale.cartNumber,
                    supporting = listOfNotNull(sale.customer.ifBlank { null }, time,
                        pluralStringResource(R.plurals.product_lines, sale.lines.size, sale.lines.size), sale.id).joinToString(" · "),
                    meta = "₹"+indianNumber(sale.totalRupees),
                    actionLabel = "Open receipt · Cart "+sale.cartNumber, onClick = { open(sale.cartId) })
            }
        }
    }
    if (choosingDates) {
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDay?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            initialSelectedEndDateMillis = endDay?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            initialDisplayMode = DisplayMode.Input)
        Dialog(onDismissRequest = { choosingDates = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(ShopSpacing.content)) {
                    DateRangePicker(picker, modifier = Modifier.weight(1f).fillMaxWidth(), showModeToggle = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                        TextButton(onClick = { choosingDates = false }) { Text(stringResource(R.string.cancel)) }
                        TextButton(enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null,
                            onClick = {
                                startDay = Instant.ofEpochMilli(requireNotNull(picker.selectedStartDateMillis)).atZone(ZoneOffset.UTC).toLocalDate().toString()
                                endDay = Instant.ofEpochMilli(requireNotNull(picker.selectedEndDateMillis)).atZone(ZoneOffset.UTC).toLocalDate().toString()
                                period = "custom"; choosingDates = false
                            }) { Text(stringResource(R.string.apply_dates)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SaleReceipt(sale: Sale, history: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = .08f).compositeOver(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sale_saved_on_this_phone), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.cash_sale_cart, sale.cartNumber), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(saleDate(sale.recordedAt), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.receipt, sale.id), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sale.customer.isNotEmpty()) Text(sale.customer, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.total, indianNumber(sale.totalRupees)), style = MaterialTheme.typography.headlineSmall)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.cash_received_2, indianNumber(sale.cashRupees)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.change, indianNumber(sale.changeRupees)), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.this_receipt_remains_available_offline_in_sales), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    ShopSectionHeader(stringResource(R.string.products), supporting = "${sale.lines.size} ${if (sale.lines.size == 1) "line" else "lines"}")
    ShopGroup(spacing = ShopSpacing.small) {
        sale.lines.forEach { line ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line.title, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(line.sku, line.category).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (line.fitment.isNotBlank()) Text(line.fitment, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (line.description.isNotEmpty()) Text(line.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.pieces_2, indianNumber(line.quantity), indianNumber(line.quotedPriceRupees), indianNumber(line.totalRupees)),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    TextButton(onClick = history, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.more_sales_history)) }
}

@Composable
internal fun SaleStockHistory(shop: ShopSnapshot, productId: String) {
    val sales = shop.sales.filter { sale -> sale.lines.any { it.productId == productId } }
    if (sales.isEmpty()) return
    ShopSection(stringResource(R.string.sale_stock_history)) {
        sales.forEach { sale ->
            val line = sale.lines.single { it.productId == productId }
            Text(stringResource(R.string.cash_sale_cart_2, sale.cartNumber, saleDate(sale.recordedAt)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.removed_pieces_pieces, indianNumber(line.quantity), indianNumber(line.previousBalance), indianNumber(line.resultingBalance)))
        }
    }
}

private fun saleDate(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
