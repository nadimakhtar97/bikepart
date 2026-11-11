package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class StockCorrectionsTest {
    @Test fun physicalCountUsesCurrentBalanceAndRetrySurvivesReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "corrections-${UUID.randomUUID()}.db"
        var shop = Shop.open(context, name)
        try {
            val id = shop.createProduct("Bolt", shop.snapshot().categories.first().id, "", "12", "80")
            val staleObservation = shop.snapshot().products.single().quantity
            shop.correctStock("damage", id, CorrectionKind.Damage, "2", "Cracked")
            shop.correctStock("count", id, CorrectionKind.PhysicalCount, staleObservation.toString(), "Counted shelf")
            assertEquals(12L, shop.snapshot().products.single().quantity)
            val history = shop.correctionHistory(id)
            assertEquals(listOf(-2L, 2L), history.map { it.movement })
            assertEquals(3L, shop.snapshot().revision)
            shop.close()
            shop = Shop.open(context, name)
            assertEquals(history, shop.correctionHistory(id))
            shop.correctStock("count", id, CorrectionKind.PhysicalCount, "12", "Counted shelf")
            assertEquals(3L, shop.snapshot().revision)
            assertEquals(history, shop.correctionHistory(id))
        } finally { shop.close(); context.deleteDatabase(name) }
    }
    @Test fun unchangedCountsLossAndInvalidInputsKeepTheLedgerReconciled() = runBlocking {
        withShop { shop, _, _ ->
            val id = shop.createProduct("Nut", shop.snapshot().categories.first().id, "", "12", "5")
            shop.correctStock("same", id, CorrectionKind.PhysicalCount, "12", "Shelf checked")
            assertNull(shop.correctionHistory(id).single().movement)
            assertEquals(2L, shop.snapshot().revision)
            shop.correctStock("lost", id, CorrectionKind.Loss, "3", "Missing from bin")
            assertEquals(9L, shop.snapshot().products.single().quantity)
            assertEquals(9L, shop.openingHistory(id).sumOf { it.quantity } + shop.correctionHistory(id).sumOf { it.movement ?: 0L })
            val before = shop.snapshot()
            for (invalid in listOf("-1", "1.5", "", "9223372036854775808", "1,000", "+2")) {
                rejects { shop.correctStock(UUID.randomUUID().toString(), id, CorrectionKind.PhysicalCount, invalid, "Checked") }
            }
            for (kind in listOf(CorrectionKind.Damage, CorrectionKind.Loss)) for (quantity in listOf("0", "10")) {
                rejects { shop.correctStock(UUID.randomUUID().toString(), id, kind, quantity, "Checked") }
            }
            rejects { shop.correctStock("blank", id, CorrectionKind.PhysicalCount, "9", "   ") }
            rejects { shop.correctStock("lost", id, CorrectionKind.Loss, "2", "Missing from bin") }
            rejects { shop.correctStock("lost", "missing", CorrectionKind.Loss, "3", "Missing from bin") }
            assertEquals(before, shop.snapshot())
            shop.correctStock("empty", id, CorrectionKind.PhysicalCount, "0", "All bins empty")
            shop.correctStock("maximum", id, CorrectionKind.PhysicalCount, Long.MAX_VALUE.toString(), "Full recount")
            assertEquals(Long.MAX_VALUE, shop.snapshot().products.single().quantity)
        }
    }

    @Test fun concurrentCountsAndDuplicateRetriesAreSerializedAcrossShopConnections() = runBlocking {
        withShop { shop, context, name ->
            val id = shop.createProduct("Bolt", shop.snapshot().categories.first().id, "", "12", "80")
            Shop.open(context, name).use { other ->
                kotlinx.coroutines.coroutineScope {
                    val first = async(kotlinx.coroutines.Dispatchers.IO) { shop.correctStock("first", id, CorrectionKind.PhysicalCount, "7", "First count") }
                    val second = async(kotlinx.coroutines.Dispatchers.IO) { other.correctStock("second", id, CorrectionKind.PhysicalCount, "5", "Second count") }
                    first.await(); second.await()
                }
                val product = shop.snapshot().products.single()
                assertTrue(product.quantity == 5L || product.quantity == 7L)
                assertEquals(product.quantity, 12L + shop.correctionHistory(id).sumOf { it.movement ?: 0L })
                kotlinx.coroutines.coroutineScope {
                    val first = async(kotlinx.coroutines.Dispatchers.IO) { shop.correctStock("retry", id, CorrectionKind.Damage, "1", "Damaged") }
                    val second = async(kotlinx.coroutines.Dispatchers.IO) { other.correctStock("retry", id, CorrectionKind.Damage, "1", "Damaged") }
                    first.await(); second.await()
                }
                assertEquals(product.quantity - 1, shop.snapshot().products.single().quantity)
                assertEquals(3, shop.correctionHistory(id).size)
                assertEquals(4L, shop.snapshot().revision)
            }
        }
    }

    @Test fun storageInterruptionAndRevisionOverflowLeaveNoPartialCorrectionAfterReopen() = runBlocking {
        for (fault in listOf("audit", "balance", "revision", "overflow")) withShop { shop, context, name ->
            val id = shop.createProduct("Pad", shop.snapshot().categories.first().id, "", "12", "80")
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                when (fault) {
                    "audit" -> it.execSQL("CREATE TRIGGER fail_write BEFORE INSERT ON stock_corrections BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                    "balance" -> it.execSQL("CREATE TRIGGER fail_write BEFORE UPDATE ON balances BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                    "revision" -> it.execSQL("CREATE TRIGGER fail_write BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                    else -> it.execSQL("UPDATE shop_state SET revision = 9223372036854775807")
                }
            }
            val before = shop.snapshot()
            try {
                shop.correctStock("interrupted", id, CorrectionKind.Loss, "2", "Missing")
                fail("Correction should roll back")
            } catch (_: android.database.sqlite.SQLiteException) { } catch (_: ArithmeticException) { }
            assertEquals(before, shop.snapshot())
            Shop.open(context, name).use { assertEquals(before, it.snapshot()); assertTrue(it.correctionHistory(id).isEmpty()) }
            if (fault != "overflow") {
                android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { it.execSQL("DROP TRIGGER fail_write") }
                shop.correctStock("interrupted", id, CorrectionKind.Loss, "2", "Missing")
                assertEquals(10L, shop.snapshot().products.single().quantity)
                assertEquals(1, shop.correctionHistory(id).size)
            }
        }
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Correction should be rejected") } catch (_: IllegalArgumentException) { }
    }

    private suspend fun withShop(action: suspend (Shop, Context, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "corrections-${UUID.randomUUID()}.db"
        try { Shop.open(context, name).use { shop -> action(shop, context, name) } }
        finally { context.deleteDatabase(name) }
    }

}
