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
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


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
            ShopChoiceChip(
                label = stringResource(label),
                selected = period == id,
                onClick = { period = id },
                leading = if (period == id) ({
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null
                    )
                }) else null
            )
        }
        ShopChoiceChip(
            label = stringResource(R.string.date_range),
            selected = period == "custom",
            onClick = { choosingDates = true },
            leading = {
                Icon(
                    painterResource(R.drawable.ic_calendar_month),
                    contentDescription = null
                )
            })
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
            Surface(Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier
                    .fillMaxSize()
                    .padding(ShopSpacing.content)) {
                    DateRangePicker(picker, modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(), showModeToggle = true)
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
