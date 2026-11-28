package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CartsTest {
    @Test fun staleEditsInvalidQuantitiesAndOverflowLeaveSavedCartIntact() = runBlocking {
        withShop { shop, context, name ->
            val product = shop.createProduct("Bolt", shop.snapshot().categories.first().id, "", Long.MAX_VALUE.toString(), "80")
            val cart = shop.createCart()
            shop.saveCart(cart, 0, "Ravi", mapOf(product to "2"))
            Shop.open(context, name).use { other ->
                other.saveCart(cart, 1, "Meena", mapOf(product to "3"))
                val before = shop.snapshot()
                rejects { shop.saveCart(cart, 1, "Stale customer", mapOf(product to "4")) }
                rejects { shop.discardCart(cart, 1) }
                assertEquals(before, shop.snapshot())
            }
            val before = shop.snapshot()
            for (input in listOf("", "-1", "1.5", "+1", "1,000", "9223372036854775808", "１２")) {
                rejects { shop.saveCart(cart, 2, "Invalid", mapOf(product to input)) }
                rejects { shop.addCartProduct(cart, 2, product, input) }
            }
            rejects { shop.addCartProduct(cart, 2, product, "0") }
            rejects { shop.saveCart(cart, 2, "Overflow", mapOf(product to Long.MAX_VALUE.toString())) }
            assertEquals(before, shop.snapshot())
            val expensive = shop.createProduct("Expensive", shop.snapshot().categories.first().id, "", "1", Long.MAX_VALUE.toString())
            rejects { shop.saveCart(cart, 2, "Sum overflow", mapOf(product to "1", expensive to "1")) }
            shop.setSellingPrice(product, "0")
            shop.saveCart(cart, 3, "", emptyMap())
            shop.saveCart(cart, 4, "", mapOf(product to Long.MAX_VALUE.toString()))
            val maximum = shop.snapshot()
            assertEquals(0L, maximum.carts.single().totalRupees)
            rejects { shop.addCartProduct(cart, 5, product, "1") }
            assertEquals(maximum, shop.snapshot())
            val other = shop.createCart()
            shop.saveCart(other, 0, "", mapOf(product to "1"))
            assertEquals("9223372036854775808", shop.snapshot().otherCartDemand("another-customer", product))
            assertEquals(Long.MAX_VALUE, shop.snapshot().products.single { it.id == product }.quantity)
            shop.discardCart(cart, 5)
            rejects { shop.saveCart(cart, 4, "", emptyMap()) }
        }
    }

    @Test fun concurrentEditsCannotOverwriteEachOther() = runBlocking {
        withShop { shop, context, name ->
            val id = shop.createCart()
            Shop.open(context, name).use { other ->
                val results = kotlinx.coroutines.coroutineScope {
                    listOf(async(kotlinx.coroutines.Dispatchers.IO) { runCatching { shop.saveCart(id, 0, "First", emptyMap()) } },
                        async(kotlinx.coroutines.Dispatchers.IO) { runCatching { other.saveCart(id, 0, "Second", emptyMap()) } }).map { it.await() }
                }
                assertEquals(1, results.count { it.isSuccess })
                assertTrue(results.single { it.isFailure }.exceptionOrNull() is StaleCart)
                assertEquals(1L, shop.snapshot().carts.single().editVersion)
                assertEquals(2L, shop.snapshot().revision)
            }
        }
    }

    @Test fun interruptedWritesAndRevisionOverflowRollbackWholeCartChanges() = runBlocking {
        for (operation in listOf("create", "edit", "discard")) for (failure in listOf("revision", "overflow")) withShop { shop, context, name ->
            val product = shop.createProduct("Nut", shop.snapshot().categories.first().id, "", "12", "5")
            val cart = shop.createCart()
            shop.saveCart(cart, 0, "Original", mapOf(product to "2"))
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { db ->
                if (failure == "revision") db.execSQL("CREATE TRIGGER fail_cart BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                else db.execSQL("UPDATE shop_state SET revision = 9223372036854775807")
            }
            val before = shop.snapshot()
            try {
                when (operation) {
                    "create" -> shop.createCart()
                    "edit" -> shop.saveCart(cart, 1, "Changed", mapOf(product to "3"))
                    else -> shop.discardCart(cart, 1)
                }
                fail("Expected interrupted commit")
            } catch (_: Exception) { }
            assertEquals(before, shop.snapshot())
            Shop.open(context, name).use { reopened -> assertEquals(before, reopened.snapshot()) }
        }
    }

    @Test fun stockCorrectionsReducePausedCartsAndKeepNoticesAcrossReopen() = runBlocking {
        withShop { shop, context, name ->
            val product = shop.createProduct("Bolt", shop.snapshot().categories.first().id, "", "5", "80")
            val first = shop.createCart()
            val second = shop.createCart()
            shop.saveCart(first, 0, "First", mapOf(product to "4"))
            shop.addCartProduct(second, 0, product, "5")
            val before = shop.snapshot()
            rejects { shop.addCartProduct(first, 1, product, "2") }
            rejects { shop.saveCart(first, 1, "", mapOf(product to "6")) }
            assertEquals(before, shop.snapshot())

            shop.correctStock("count", product, CorrectionKind.PhysicalCount, "2", "Counted two")
            rejects { shop.saveCart(first, 1, "Stale edit", mapOf(product to "4")) }
            Shop.open(context, name).use { reopened ->
                val reduced = reopened.snapshot()
                assertEquals(listOf(2L, 2L), reduced.carts.map { it.lines.single().quantity })
                assertTrue(reduced.carts.all { it.stockReductions == mapOf(product to 2L) })
                reopened.dismissCartStockNotice(first, 2)
                reopened.saveCart(first, 2, "First", mapOf(product to "0"))
                assertTrue(reopened.snapshot().carts.single { it.id == first }.lines.isEmpty())
                reopened.correctStock("loss", product, CorrectionKind.Loss, "2", "Lost both pieces")
                val paused = reopened.snapshot().carts.single { it.id == second }
                assertTrue(paused.lines.isEmpty())
                assertEquals(mapOf(product to 0L), paused.stockReductions)
            }
        }
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Expected rejected cart edit") } catch (_: IllegalArgumentException) { }
    }

    private suspend fun withShop(block: suspend (Shop, Context, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "carts-${UUID.randomUUID()}.db"
        try { Shop.open(context, name).use { block(it, context, name) } } finally { context.deleteDatabase(name) }
    }

    @Test fun priceChangesRepriceEveryUnfinishedCartAndReopenWithoutReservingStock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "carts-${UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val product = shop.createProduct("Bolt", shop.snapshot().categories.first().id, "", "3", "80")
                val first = shop.createCart()
                val second = shop.createCart()
                shop.saveCart(first, 0, "Ravi", mapOf(product to "2"))
                shop.saveCart(second, 0, "", mapOf(product to "1"))
                shop.setSellingPrice(product, "95")
                shop.saveCart(first, 2, "Ravi", mapOf(product to "3"))
                val snapshot = shop.snapshot()
                val cart = snapshot.carts.single { it.id == first }
                assertEquals("Cart 1", cart.name)
                assertEquals(285L, cart.totalRupees)
                assertEquals(95L, cart.lines.single().quotedPriceRupees)
                assertEquals("1", snapshot.otherCartDemand(first, product))
                assertEquals(3L, snapshot.products.single().quantity)
            }
            Shop.open(context, name).use { shop ->
                val snapshot = shop.snapshot()
                val first = snapshot.carts.single { it.number == 1L }
                val product = snapshot.products.single().id
                assertEquals("Ravi", first.customer)
                assertEquals(95L, first.lines.single().quotedPriceRupees)
                shop.saveCart(first.id, first.editVersion, "Ravi", emptyMap())
                shop.saveCart(first.id, first.editVersion + 1, "Ravi", mapOf(product to "2"))
                assertEquals(190L, shop.snapshot().carts.single { it.id == first.id }.totalRupees)
                shop.discardCart(first.id, first.editVersion + 2)
                assertEquals(3L, shop.snapshot().products.single().quantity)
                assertEquals("Cart 3", shop.createCart().let { id -> shop.snapshot().carts.single { it.id == id }.name })
            }
        } finally { context.deleteDatabase(name) }
    }
}
