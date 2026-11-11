package com.nadim.bikepart

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FindingDetailsTest {
    @Test fun scopedReuseRetirementAndTransitionsPreserveStockAndMeaning() = runBlocking {
        withShop { shop, _ ->
            assertTrue(shop.snapshot().makes.isEmpty())
            assertTrue(shop.snapshot().models.isEmpty())
            assertTrue(shop.snapshot().locations.isEmpty())
            val make = shop.createMake("  Straße  Works ")
            val otherMake = shop.createMake("Other")
            val model = shop.createModel(make, "Model A")
            val second = shop.createModel(otherMake, "Model A")
            val shelf = shop.createLocation("Shelf\u00a0 A")
            val bin = shop.createLocation("Bin B")
            val revision = shop.snapshot().revision
            assertEquals(make, shop.createMake("STRASSE WORKS"))
            assertEquals(model, shop.createModel(make, "ＭＯＤＥＬ Ａ"))
            assertEquals(shelf, shop.createLocation(" shelf   a "))
            assertNotEquals(model, second)
            assertEquals(revision, shop.snapshot().revision)
            val category = shop.snapshot().categories.first().id
            val id = shop.createProduct("Pad", category, "Original", "12", "80")
            val original = shop.snapshot().products.single()
            assertTrue(original.modelIds.isEmpty())
            shop.editProduct(id, "Pad", category, "Original", modelIds = setOf(model, second), locationIds = setOf(shelf, bin))
            assertEquals(revision + 2, shop.snapshot().revision)
            shop.retireModel(model)
            shop.retireMake(otherMake)
            shop.retireLocation(shelf)
            val retired = shop.snapshot()
            shop.retireModel(model)
            shop.retireMake(otherMake)
            shop.retireLocation(shelf)
            assertEquals(retired, shop.snapshot())
            rejects { shop.createModel(otherMake, "New") }
            rejects { shop.createModel(make, "model a") }
            rejects { shop.createLocation("SHELF A") }
            rejects { shop.createMake("other") }
            rejects { shop.createProduct("Other pad", category, "", "0", "0", modelIds = setOf(model)) }
            rejects { shop.createProduct("Other pad", category, "", "0", "0", modelIds = setOf(second)) }
            rejects { shop.createProduct("Other pad", category, "", "0", "0", locationIds = setOf(shelf)) }
            rejects { shop.createProduct("Other pad", category, "", "0", "0", modelIds = setOf("missing")) }
            assertEquals(retired, shop.snapshot())
            shop.editProduct(id, "Pad retained", category, "Original")
            assertEquals(setOf(model, second), shop.snapshot().products.single().modelIds)
            assertEquals(setOf(shelf, bin), shop.snapshot().products.single().locationIds)
            shop.editProduct(id, "Pad", category, "Original", modelIds = emptySet(), locationIds = setOf(bin))
            assertEquals(original.copy(locationIds = setOf(bin)), shop.snapshot().products.single())
            val before = shop.snapshot()
            rejects { shop.editProduct(id, "Invalid", category, "", modelIds = setOf(model), locationIds = setOf(shelf)) }
            assertEquals(before, shop.snapshot())
            assertEquals(12L, shop.openingHistory(id).single().quantity)
        }
    }

    @Test fun revisionInterruptionRollsBackLookupsAssociationsAndProductWrites() = runBlocking {
        withShop { shop, name ->
            val make = shop.createMake("Make")
            val model = shop.createModel(make, "Model")
            val location = shop.createLocation("Shelf")
            val category = shop.snapshot().categories.first().id
            val id = shop.createProduct("Pad", category, "", "12", "80", modelIds = setOf(model), locationIds = setOf(location))
            val before = shop.snapshot()
            val context = ApplicationProvider.getApplicationContext<Context>()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                it.execSQL("CREATE TRIGGER fail_revision BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interrupted revision'); END")
            }
            for (write in listOf<suspend () -> Unit>(
                { shop.createMake("New") }, { shop.createModel(make, "New") }, { shop.createLocation("New") },
                { shop.retireMake(make) }, { shop.retireModel(model) }, { shop.retireLocation(location) },
                { shop.editProduct(id, "Changed", category, "", modelIds = emptySet(), locationIds = emptySet()) },
                { shop.createProduct("New pad", category, "", "4", "90", modelIds = setOf(model), locationIds = setOf(location)) },
            )) {
                try { write(); fail("Interrupted write must fail") } catch (_: android.database.sqlite.SQLiteException) { }
                assertEquals(before, shop.snapshot())
            }
        }
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Must reject invalid change") } catch (_: IllegalArgumentException) { }
    }

    private suspend fun withShop(action: suspend (Shop, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "finding-${java.util.UUID.randomUUID()}.db"
        try {
            val saved = Shop.open(context, name).use { action(it, name); it.snapshot() }
            Shop.open(context, name).use { assertEquals(saved, it.snapshot()) }
        } finally { context.deleteDatabase(name) }
    }
}
