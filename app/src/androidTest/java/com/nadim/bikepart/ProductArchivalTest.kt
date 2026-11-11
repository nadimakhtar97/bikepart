package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ProductArchivalTest {
    @Test fun competingArchiveAndCartOrStockWritesCannotCommitAnInvalidArchivedProduct() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "archive-race-${UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop -> Shop.open(context, name).use { other ->
                for (cartRace in listOf(true, false)) {
                    val product = shop.createProduct("Race $cartRace", shop.snapshot().categories.first().id, "", "0", "5")
                    val cart = shop.createCart()
                    val results = coroutineScope {
                        val archive = async(Dispatchers.IO) { runCatching { shop.archiveProduct(product) } }
                        val write = async(Dispatchers.IO) { runCatching {
                            if (cartRace) other.addCartProduct(cart, 0, product, "1")
                            else other.correctStock("race-count", product, CorrectionKind.PhysicalCount, "1", "Found one")
                        } }
                        listOf(archive.await().isSuccess, write.await().isSuccess)
                    }
                    assertEquals(1, results.count { it })
                    val state = shop.snapshot()
                    val saved = state.products.single { it.id == product }
                    if (saved.archived) {
                        assertEquals(0L, saved.quantity)
                        assertTrue(state.carts.flatMap { it.lines }.none { it.productId == product })
                    } else if (cartRace) assertTrue(state.carts.flatMap { it.lines }.any { it.productId == product })
                    else assertEquals(1L, saved.quantity)
                }
            } }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun revisionFailureRollsBackArchivalAndReopenRetainsTheActiveProduct() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "archive-rollback-${UUID.randomUUID()}.db"
        try {
            val product = Shop.open(context, name).use { shop ->
                shop.createProduct("Rollback bolt", shop.snapshot().categories.first().id, "", "0", "80")
            }
            // Inject a storage failure at the transaction boundary, after the product update.
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                it.execSQL("CREATE TRIGGER fail_archive_revision BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interrupted revision'); END")
            }
            val before = Shop.open(context, name).use { shop ->
                val snapshot = shop.snapshot()
                try { shop.archiveProduct(product); fail("Expected storage failure") }
                catch (_: android.database.sqlite.SQLiteException) { }
                assertEquals(snapshot, shop.snapshot())
                snapshot
            }
            Shop.open(context, name).use { assertEquals(before, it.snapshot()) }
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                it.execSQL("DROP TRIGGER fail_archive_revision")
                it.execSQL("UPDATE shop_state SET revision = 9223372036854775807")
            }
            Shop.open(context, name).use { shop ->
                val snapshot = shop.snapshot()
                try { shop.archiveProduct(product); fail("Expected revision overflow") }
                catch (_: ArithmeticException) { }
                assertEquals(snapshot, shop.snapshot())
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun archivalRechecksChangedStockAndUnfinishedCartReferencesWithoutPartialWrites() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "archive-guards-${UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val product = shop.createProduct("Bolt", shop.snapshot().categories.first().id, "", "0", "80")
                Shop.open(context, name).use { stale ->
                    shop.correctStock("count", product, CorrectionKind.PhysicalCount, "1", "Found one")
                    var before = shop.snapshot()
                    rejects("stock") { stale.archiveProduct(product) }
                    assertEquals(before, shop.snapshot())
                    shop.correctStock("lost", product, CorrectionKind.Loss, "1", "Lost one")
                    val cart = shop.createCart()
                    shop.addCartProduct(cart, 0, product, "1")
                    before = shop.snapshot()
                    rejects("unfinished cart") { stale.archiveProduct(product) }
                    assertEquals(before, shop.snapshot())
                    shop.saveCart(cart, 1, "", emptyMap())
                    stale.archiveProduct(product)
                    assertTrue(shop.snapshot().products.single().archived)
                    assertEquals(0L, shop.snapshot().products.single().quantity)
                    assertEquals(before.corrections, shop.snapshot().corrections)
                }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun staleCartAndStockWritesCannotReactivateAnArchivedProduct() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "archive-stale-${UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val product = shop.createProduct("Unused nut", category, "", "0", "5")
                val cart = shop.createCart()
                Shop.open(context, name).use { stale ->
                    shop.archiveProduct(product)
                    val before = shop.snapshot()
                    rejects("archived") { stale.addCartProduct(cart, 0, product, "1") }
                    rejects("archived") { stale.saveCart(cart, 0, "Stale", mapOf(product to "1")) }
                    rejects("archived") { stale.correctStock("late-count", product, CorrectionKind.PhysicalCount, "2", "Late count") }
                    rejects("archived") { stale.editProduct(product, "Changed", category, "") }
                    rejects("archived") { stale.setSellingPrice(product, "6") }
                    rejects("archived") { stale.setLowStockThreshold(product, "2") }
                    rejects("archived") { stale.createCustomAttribute(product, "Late field", AttributeType.Text) }
                    assertEquals(before, shop.snapshot())
                }
            }
        } finally { context.deleteDatabase(name) }
    }

    private suspend fun rejects(message: String, block: suspend () -> Unit) {
        try { block(); fail("Expected rejection containing $message") }
        catch (error: IllegalArgumentException) { assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(message)) }
    }

    @Test fun zeroStockArchivalPreservesIdentityHistoryAndReceiptAfterReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "archive-${UUID.randomUUID()}.db"
        try {
            val before = Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val finish = shop.createAttribute(category, "Finish", AttributeType.Text)
                val make = shop.createMake("Honda")
                val model = shop.createModel(make, "Shine")
                val location = shop.createLocation("Shelf A")
                val product = shop.createProduct("Bolt", category, "M8 zinc", "3", "80",
                    attributes = mapOf(finish to AttributeValue.Text("Zinc")), modelIds = setOf(model), locationIds = setOf(location))
                shop.retireAttribute(finish)
                shop.correctStock("damage", product, CorrectionKind.Damage, "1", "Damaged thread")
                val cart = shop.createCart()
                shop.addCartProduct(cart, 0, product, "2")
                shop.checkout(cart, 1, "160")
                val snapshot = shop.snapshot()
                shop.archiveProduct(product)
                val archived = shop.snapshot()
                assertEquals(snapshot.copy(products = snapshot.products.map { it.copy(archived = true) }, revision = snapshot.revision + 1), archived)
                assertTrue(CatalogSearch(archived).find().isEmpty())
                assertEquals(snapshot.sales.single(), shop.checkout(cart, 0, "invalid retry"))
                shop.archiveProduct(product)
                assertEquals(archived, shop.snapshot())
                archived
            }
            Shop.open(context, name).use { assertEquals(before, it.snapshot()) }
        } finally { context.deleteDatabase(name) }
    }
}
