package com.nadim.bikepart.ui

import com.nadim.bikepart.shop.Sale
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class SaleDay(val date: LocalDate, val sales: List<Sale>) {
    // A day's sum may exceed Long.MAX_VALUE even though each receipt is valid.
    val totalRupees: BigInteger get() = sales.fold(BigInteger.ZERO) { sum, sale -> sum + sale.totalRupees.toBigInteger() }
}

internal fun salesLog(sales: List<Sale>, zone: ZoneId, from: LocalDate? = null, until: LocalDate? = null,
    query: String = "", cartName: (Long) -> String = Long::toString): List<SaleDay> = sales.asSequence()
    .filter { sale ->
        val day = Instant.ofEpochMilli(sale.recordedAt).atZone(zone).toLocalDate()
        (from == null || day >= from) && (until == null || day <= until) &&
            (query.isBlank() || listOf(sale.id, cartName(sale.cartNumber), sale.customer).any { it.contains(query.trim(), ignoreCase = true) })
    }
    .sortedWith(compareByDescending<Sale> { it.recordedAt }.thenBy { it.id })
    .groupBy { Instant.ofEpochMilli(it.recordedAt).atZone(zone).toLocalDate() }
    .map { (day, entries) -> SaleDay(day, entries) }

internal fun groupedRupees(value: BigInteger): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    return digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed() + "," + digits.takeLast(3)
}
