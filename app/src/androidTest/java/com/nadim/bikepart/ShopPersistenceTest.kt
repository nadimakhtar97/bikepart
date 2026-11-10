package com.nadim.bikepart

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.nadim.bikepart.shop.Shop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShopPersistenceTest {
    @Test fun invalidInputAndOverflowNeverPartiallyCreateAProduct() = runBlocking {
        withShop { shop ->
            val before = shop.snapshot()
            val category = before.categories.first().id
            for (invalid in listOf("", "-1", "1.5", "1,000", "9223372036854775808", "1e3")) {
                assertFails { shop.createProduct("Pad", category, "", "0", invalid) }
                assertFails { shop.createProduct("Pad", category, "", invalid, "80") }
                assertEquals(before, shop.snapshot())
            }
            assertFails { shop.createProduct(" ", category, "", "0", "0") }
            assertFails { shop.createProduct("Pad", "missing", "", "0", "0") }
            assertEquals(before, shop.snapshot())
            val id = shop.createProduct("Pad", category, "", "9223372036854775807", "9223372036854775807")
            assertEquals(Long.MAX_VALUE, shop.snapshot().products.single().quantity)
            assertEquals(Long.MAX_VALUE, shop.openingHistory(id).single().quantity)
        }
    }

    @Test fun zeroStockAndDeliberatelySimilarProductsHaveSeparateIdentities() = runBlocking {
        withShop { shop ->
            val category = shop.snapshot().categories.first().id
            val id = shop.createProduct("Pad", category, "", "0", "0")
            assertTrue(shop.openingHistory(id).isEmpty())
            val before = shop.snapshot()
            assertFails { shop.createProduct(" PAD ", category, "", "0", "0") }
            assertEquals(before, shop.snapshot())
            val distinct = shop.createProduct(" PAD ", category, "", "0", "0", allowSimilar = true)
            assertNotEquals(id, distinct)
            assertEquals(2, shop.snapshot().products.map { it.sku }.distinct().size)
        }
    }

    @Test fun appearanceAndCategoryChangesPersistWithoutReseeding() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "settings-${java.util.UUID.randomUUID()}.db"
        var shop = Shop.open(context, name)
        try {
            shop.renameCategory(shop.snapshot().categories.first().id, "Pads")
            shop.setAppearance(com.nadim.bikepart.shop.Appearance.Dark)
            shop.close()
            shop = Shop.open(context, name)
            assertEquals("Pads", shop.snapshot().categories.first().name)
            assertEquals(4, shop.snapshot().categories.size)
            assertEquals(com.nadim.bikepart.shop.Appearance.Dark, shop.snapshot().appearance)
            assertEquals(2L, shop.snapshot().revision)
        } finally { shop.close(); context.deleteDatabase(name) }
    }

    @Test fun storageFailureAfterProductInsertRollsBackTheWholeWrite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "interruption-${java.util.UUID.randomUUID()}.db"
        val shop = Shop.open(context, name)
        try {
            val before = shop.snapshot()
            // Fault at the SQLite storage boundary; assertions still use the public shop API.
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                it.execSQL("CREATE TRIGGER fail_opening BEFORE INSERT ON opening_stock BEGIN SELECT RAISE(ABORT, 'storage interruption'); END")
            }
            try {
                shop.createProduct("Pad", before.categories.first().id, "", "12", "80")
                fail("Storage interruption must fail the transaction")
            } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(before, shop.snapshot())
        } finally { shop.close(); context.deleteDatabase(name) }
    }

    @Test fun overflowingRevisionRollsBackProductPriceBalanceAndOpeningHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "revision-${java.util.UUID.randomUUID()}.db"
        var shop = Shop.open(context, name)
        try {
            shop.close()
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                it.execSQL("UPDATE shop_state SET revision = 9223372036854775807")
            }
            shop = Shop.open(context, name)
            val before = shop.snapshot()
            try {
                shop.createProduct("Pad", before.categories.first().id, "", "12", "80")
                fail("Revision overflow must reject the whole write")
            } catch (_: ArithmeticException) { }
            shop.close()
            shop = Shop.open(context, name)
            assertEquals(before, shop.snapshot())
        } finally { shop.close(); context.deleteDatabase(name) }
    }

    private suspend fun assertFails(action: suspend () -> Unit) {
        try { action(); fail("Input should be rejected") } catch (_: IllegalArgumentException) { }
    }

    private suspend fun withShop(action: suspend (Shop) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "shop-test-${java.util.UUID.randomUUID()}.db"
        val shop = Shop.open(context, name)
        try { action(shop) } finally { shop.close(); context.deleteDatabase(name) }
    }

    @Test fun firstProductSurvivesReopenWithItsOpeningOrigin() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "shop-test-${java.util.UUID.randomUUID()}.db"
        var shop = Shop.open(context, name)
        try {
            val fresh = shop.snapshot()
            assertEquals(listOf("Brake pads", "Nuts", "Bolts", "Sealed oil bottles"), fresh.categories.map { it.name })
            assertTrue(fresh.products.isEmpty())
            val id = shop.createProduct("M8 nut", fresh.categories[1].id, "Zinc", "12", "80")
            val product = shop.snapshot().products.single()
            assertEquals(12L, product.quantity)
            assertEquals(80L, product.priceRupees)
            shop.editProduct(id, "M8 nut revised", product.categoryId, "Steel")
            shop.close()
            shop = Shop.open(context, name)
            val reopened = shop.snapshot()
            assertEquals(id, reopened.products.single().id)
            assertEquals(product.sku, reopened.products.single().sku)
            assertEquals("Steel", reopened.products.single().description)
            assertEquals(12L, shop.openingHistory(id).single().quantity)
            assertEquals(2L, reopened.revision)
        } finally { shop.close(); context.deleteDatabase(name) }
    }
}
