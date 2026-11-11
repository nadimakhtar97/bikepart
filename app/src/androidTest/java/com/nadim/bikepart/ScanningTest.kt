package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ScanningTest {
    @Test fun interruptedArrivalRollsBackHistoryBalanceAndRevisionTogether() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (fault in listOf("audit", "balance", "revision", "overflow")) {
            val name = "arrival-fault-${UUID.randomUUID()}.db"
            try {
                Shop.open(context, name).use { shop ->
                    val id = shop.createProduct("Bolt", shop.snapshot().categories.first().id, "", "4", "80")
                    android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { db ->
                        when (fault) {
                            "audit" -> db.execSQL("CREATE TRIGGER fail_write BEFORE INSERT ON stock_arrivals BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                            "balance" -> db.execSQL("CREATE TRIGGER fail_write BEFORE UPDATE ON balances BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                            "revision" -> db.execSQL("CREATE TRIGGER fail_write BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                            else -> db.execSQL("UPDATE shop_state SET revision = 9223372036854775807")
                        }
                    }
                    val before = shop.snapshot()
                    try { shop.receiveStock("interrupted", id, "3", "25"); fail("Partial arrival") }
                    catch (_: android.database.sqlite.SQLiteException) { } catch (_: ArithmeticException) { }
                    assertEquals(before, shop.snapshot())
                    Shop.open(context, name).use { assertEquals(before, it.snapshot()) }
                    if (fault != "overflow") {
                        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { it.execSQL("DROP TRIGGER fail_write") }
                        shop.receiveStock("interrupted", id, "3", "25")
                        assertEquals(7L, shop.snapshot().products.single().quantity)
                        assertEquals(1, shop.snapshot().arrivals.size)
                    }
                }
            } finally { context.deleteDatabase(name) }
        }
    }

    @Test fun resolutionIsStockNeutralAndReceivingCommitsOnceAcrossReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "scanning-${UUID.randomUUID()}.db"
        try {
            lateinit var productId: String
            Shop.open(context, name).use { shop ->
                productId = shop.createProduct("Bin bolt", shop.snapshot().categories.first().id, "", "4", "80")
                val before = shop.snapshot()
                val payload = ItemQr.product(before.datasetId, productId)
                assertEquals(productId, (before.resolveScan(payload) as ScanTarget.StockedProduct).product.id)
                assertEquals(before, shop.snapshot())
                for (bad in listOf(payload.replace(before.datasetId, UUID.randomUUID().toString()),
                    ItemQr.product(before.datasetId, UUID.randomUUID().toString()), "bikepart:2:bad")) {
                    try { before.resolveScan(bad); fail("Accepted $bad") } catch (_: IllegalArgumentException) { }
                    assertEquals(before, shop.snapshot())
                }
                for ((pieces, cost) in listOf("0" to "2", "-1" to "2", "1.5" to "2", "2" to "1.5", Long.MAX_VALUE.toString() to "0", "2" to Long.MAX_VALUE.toString())) {
                    try { shop.receiveStock("invalid", productId, pieces, cost); fail("Accepted $pieces/$cost") } catch (_: IllegalArgumentException) { }
                    assertEquals(before, shop.snapshot())
                }
                shop.receiveStock("arrival", productId, "3", "25")
                val after = shop.snapshot()
                assertEquals(7L, after.products.single().quantity)
                assertEquals(before.revision + 1, after.revision)
                assertEquals(25L, after.arrivals.single().unitCostRupees)
            }
            Shop.open(context, name).use { shop ->
                val before = shop.snapshot()
                shop.receiveStock("arrival", productId, "3", "25")
                assertEquals(before, shop.snapshot())
                try { shop.receiveStock("arrival", productId, "4", "25"); fail("Changed retry") } catch (_: IllegalArgumentException) { }
                assertEquals(before, shop.snapshot())
                shop.correctStock("empty", productId, CorrectionKind.PhysicalCount, "0", "Empty bin")
                shop.archiveProduct(productId)
                val archived = shop.snapshot()
                try { archived.resolveScan(ItemQr.product(archived.datasetId, productId)); fail("Archived target") } catch (_: IllegalArgumentException) { }
                try { shop.receiveStock("archived", productId, "1", "0"); fail("Archived arrival") } catch (_: IllegalArgumentException) { }
                assertEquals(archived, shop.snapshot())
            }
        } finally { context.deleteDatabase(name) }
    }
}
