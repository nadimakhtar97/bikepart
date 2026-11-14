package com.nadim.bikepart

import com.nadim.bikepart.shop.Sale
import com.nadim.bikepart.ui.groupedRupees
import com.nadim.bikepart.ui.salesLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SalesLogTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun sale(id: String, timestamp: String, amount: Long = 80) = Sale(id, id, 1, "Ravi",
        Instant.parse(timestamp).toEpochMilli(), 1, amount, amount, 0, emptyList())

    @Test fun groupsByLocalDayAndFiltersInclusiveDatesInsteadOfUtcDay() {
        val before = sale("before", "2026-11-13T18:29:59Z")
        val midnight = sale("midnight", "2026-11-13T18:30:00Z")
        val later = sale("later", "2026-11-14T10:00:00Z")
        val days = salesLog(listOf(before, midnight, later), zone)
        assertEquals(listOf(LocalDate.parse("2026-11-14"), LocalDate.parse("2026-11-13")), days.map { it.date })
        assertEquals(listOf(later, midnight), days.first().sales)
        assertEquals(listOf(later, midnight), salesLog(listOf(before, midnight, later), zone,
            LocalDate.parse("2026-11-14"), LocalDate.parse("2026-11-14")).single().sales)
        assertEquals(listOf(midnight), salesLog(listOf(before, midnight, later), zone, query = " MIDNIGHT ").single().sales)
        assertEquals(2, salesLog(listOf(before, midnight), zone, query = "rAvI").sumOf { it.sales.size })
    }

    @Test fun dailyTotalsRetainEveryRupeeWhenSeveralReceiptsExceedLongRange() {
        val days = salesLog(listOf(sale("one", "2026-11-14T10:00:00Z", Long.MAX_VALUE),
            sale("two", "2026-11-14T11:00:00Z", Long.MAX_VALUE)), zone)
        assertEquals("18446744073709551614", days.single().totalRupees.toString())
        assertEquals("1,84,46,74,40,73,70,95,51,614", groupedRupees(days.single().totalRupees))
    }
}
