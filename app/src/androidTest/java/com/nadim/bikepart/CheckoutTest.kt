package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CheckoutTest {
    @Test fun invalidTenderStaleVersionAndEveryShortagePreserveTheWholeCart() = runBlocking {
        withShop { shop, context, name ->
            val category = shop.snapshot().categories.first().id
            val first = shop.createProduct("Bolt", category, "", "2", "80")
            val second = shop.createProduct("Nut", category, "", "3", "5")
            val cart = shop.createCart()
            rejects { shop.checkout(cart, 0, "0") }
            shop.saveCart(cart, 0, "Ravi", mapOf(first to "2", second to "3"))
            // Simulate an older writer leaving a shortage after the cart was saved.
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { db ->
                db.execSQL("UPDATE balances SET quantity = CASE WHEN productId = ? THEN 1 ELSE 0 END", arrayOf(first))
            }
            val before = shop.snapshot()
            for (cash in listOf("", "-1", "1.5", "₹175", "١٧٥", "9223372036854775808", "174")) {
                rejects { shop.checkout(cart, 1, cash) }
                assertEquals(before, shop.snapshot())
            }
            rejects { shop.checkout(cart, 0, "175") }
            try { shop.checkout(cart, 1, "175"); fail("Expected shortage") }
            catch (error: CheckoutShortage) {
                assertEquals(setOf(StockShortage(first, "Bolt", 2, 1), StockShortage(second, "Nut", 3, 0)), error.shortages.toSet())
            }
            assertEquals(before, shop.snapshot())
            // Staff explicitly fixes both lines. Checkout must not silently trim either.
            shop.saveCart(cart, 1, "Ravi", mapOf(first to "1"))
            val sale = shop.checkout(cart, 2, "80")
            assertEquals(0L, sale.changeRupees)
            assertEquals(0L, shop.snapshot().products.single { it.id == first }.quantity)
        }
    }

    @Test fun beforeCommitFailuresRollbackAndCompetingCartsSellTheLastPieceOnlyOnce() = runBlocking {
        for (failure in listOf("line", "balance", "completion", "revision", "overflow")) withShop { shop, context, name ->
            val product = shop.createProduct("Last bolt", shop.snapshot().categories.first().id, "", "1", "80")
            val first = shop.createCart()
            val second = shop.createCart()
            shop.saveCart(first, 0, "First", mapOf(product to "1"))
            shop.saveCart(second, 0, "Second", mapOf(product to "1"))
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { db ->
                val trigger = when (failure) {
                    "line" -> "BEFORE INSERT ON sale_lines"
                    "balance" -> "BEFORE UPDATE ON balances"
                    "completion" -> "BEFORE DELETE ON carts"
                    else -> "BEFORE INSERT ON shop_state"
                }
                if (failure == "overflow") db.execSQL("UPDATE shop_state SET revision = 9223372036854775807")
                else db.execSQL("CREATE TRIGGER interrupt_checkout $trigger BEGIN SELECT RAISE(ABORT, 'interruption'); END")
            }
            val before = shop.snapshot()
            try { shop.checkout(first, 1, "80"); fail("Expected interrupted checkout") } catch (_: Exception) { }
            Shop.open(context, name).use { reopened -> assertEquals(before, reopened.snapshot()); assertNull(reopened.receipt(first)) }
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { db ->
                if (failure == "overflow") db.execSQL("UPDATE shop_state SET revision = 5")
                else db.execSQL("DROP TRIGGER interrupt_checkout")
            }
            Shop.open(context, name).use { other ->
                val outcomes = kotlinx.coroutines.coroutineScope {
                    listOf(async { runCatching { shop.checkout(first, 1, "80") } },
                        async { runCatching { other.checkout(second, 1, "80") } }).map { it.await() }
                }
                assertEquals(1, outcomes.count { it.isSuccess })
                assertTrue(outcomes.single { it.isFailure }.exceptionOrNull() is StaleCart)
                val after = shop.snapshot()
                assertEquals(1, after.sales.size)
                assertTrue(after.carts.single().lines.isEmpty())
                assertEquals(mapOf(product to 0L), after.carts.single().stockReductions)
                assertEquals(0L, after.products.single().quantity)
                assertEquals(0L, after.openings.getValue(product).quantity + after.sales.flatMap { it.lines }.sumOf { it.movement })
            }
        }
    }

    @Test fun maximumTenderAndZeroPricedGoodsUseCheckedWholeRupees() = runBlocking {
        withShop { shop, _, _ ->
            val product = shop.createProduct("Free washer", shop.snapshot().categories.first().id, "", "1", "0")
            val cart = shop.createCart()
            shop.saveCart(cart, 0, "", mapOf(product to "1"))
            val sale = shop.checkout(cart, 1, Long.MAX_VALUE.toString())
            assertEquals(0L, sale.totalRupees)
            assertEquals(Long.MAX_VALUE, sale.changeRupees)
        }
    }

    @Test fun concurrentRetriesReturnOneSaleAndItsMovementReconcilesWithCorrections() = runBlocking {
        withShop { shop, context, name ->
            val category = shop.snapshot().categories.first().id
            val product = shop.createProduct("Bolt", category, "", "10", "80")
            shop.correctStock("damage-before-sale", product, CorrectionKind.Damage, "2", "Damaged pieces")
            val cart = shop.createCart()
            shop.saveCart(cart, 0, "Same customer", mapOf(product to "3"))
            // Newly required descriptive fields must not stop a sale of existing stock.
            shop.createAttribute(category, "Required later", AttributeType.Text, required = true)
            val revision = shop.snapshot().revision
            Shop.open(context, name).use { other ->
                val sales = kotlinx.coroutines.coroutineScope {
                    listOf(async { shop.checkout(cart, 1, "250") }, async { other.checkout(cart, 1, "250") }).map { it.await() }
                }
                assertEquals(sales.first(), sales.last())
                assertEquals(revision + 1, shop.snapshot().revision)
                assertEquals(1, shop.snapshot().sales.size)
                assertEquals(5L, shop.snapshot().products.single().quantity)
            }
            shop.correctStock("count-after-sale", product, CorrectionKind.PhysicalCount, "4", "Count after sale")
            Shop.open(context, name).use { reopened ->
                val snapshot = reopened.snapshot()
                assertEquals(4L, snapshot.products.single().quantity)
                assertEquals(4L, snapshot.openings.getValue(product).quantity + snapshot.corrections.sumOf { it.movement ?: 0 } + snapshot.sales.flatMap { it.lines }.sumOf { it.movement })
            }
        }
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Expected rejected checkout") } catch (_: IllegalArgumentException) { }
    }

    @Test fun cashSaleCommitsOnceAndRecoversItsHistoricalReceipt() = runBlocking {
        withShop { shop, context, name ->
            val category = shop.snapshot().categories.first().id
            val make = shop.createMake("Honda")
            val model = shop.createModel(make, "Shine")
            val product = shop.createProduct("Brake pad", category, "Original description", "3", "80", modelIds = setOf(model))
            val cart = shop.createCart()
            shop.saveCart(cart, 0, "Ravi", mapOf(product to "2"))
            shop.setSellingPrice(product, "95")
            val before = shop.snapshot().revision
            val sale = shop.checkout(cart, 2, "200")
            assertEquals(190L, sale.totalRupees)
            assertEquals(200L, sale.cashRupees)
            assertEquals(10L, sale.changeRupees)
            assertEquals("Ravi", sale.customer)
            assertEquals("Brake pad", sale.lines.single().title)
            assertEquals("Brake pads", sale.lines.single().category)
            assertEquals("Honda · Shine", sale.lines.single().fitment)
            assertEquals("Original description", sale.lines.single().description)
            assertEquals(-2L, sale.lines.single().movement)
            assertEquals(3L, sale.lines.single().previousBalance)
            assertEquals(1L, sale.lines.single().resultingBalance)
            assertEquals(before + 1, shop.snapshot().revision)
            assertTrue(shop.snapshot().carts.isEmpty())
            shop.editProduct(product, "Changed", shop.createCategory("New category"), "New description", modelIds = emptySet())
            val committed = shop.snapshot()
            // Simulate losing the response after commit: close/reopen, retry stale inputs.
            Shop.open(context, name).use { reopened ->
                assertEquals(sale, reopened.checkout(cart, 0, "invalid"))
                assertEquals(sale, reopened.receipt(cart))
                assertEquals(listOf(sale), reopened.snapshot().sales)
                assertEquals(committed, reopened.snapshot())
                assertEquals(1L, reopened.snapshot().products.single().quantity)
            }
        }
    }

    private suspend fun withShop(block: suspend (Shop, Context, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "checkout-${UUID.randomUUID()}.db"
        try { Shop.open(context, name).use { block(it, context, name) } } finally { context.deleteDatabase(name) }
    }
}
