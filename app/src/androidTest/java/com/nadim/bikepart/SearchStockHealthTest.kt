package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SearchStockHealthTest {
    @Test fun liveStockCorrectionsUpdateAlertsAndUnassignedFilters() = runBlocking {
        withShop { shop, _, _ ->
            val id = shop.createProduct("Unassigned pad", shop.snapshot().categories.first().id, "", "5", "80")
            shop.setLowStockThreshold(id, "5")
            assertEquals(listOf(id), CatalogSearch(shop.snapshot()).find(ProductFilter(health = StockHealth.LowStock, unknownFitment = true, noLocation = true)).map { it.id })
            shop.correctStock("count", id, CorrectionKind.PhysicalCount, "6", "Found another piece")
            assertTrue(CatalogSearch(shop.snapshot()).find(ProductFilter(health = StockHealth.LowStock)).isEmpty())
            shop.correctStock("loss", id, CorrectionKind.Loss, "6", "Missing after recount")
            val found = CatalogSearch(shop.snapshot()).find(ProductFilter(health = StockHealth.OutOfStock, unknownFitment = true, noLocation = true)).single()
            assertEquals(id, found.id); assertEquals(0L, found.quantity)
        }
    }

    @Test fun thresholdsAreOptionalExactDurableAndNeverChangeBalances() = runBlocking {
        withShop { shop, context, name ->
            val category = shop.snapshot().categories.first().id
            val id = shop.createProduct("Pad", category, "", "5", "80")
            val zero = shop.createProduct("Empty pad", category, "", "0", "80")
            assertEquals(StockHealth.OutOfStock, shop.snapshot().products.single { it.id == zero }.stockHealth)
            val before = shop.snapshot()
            shop.setLowStockThreshold(id, " 5 ")
            var snapshot = shop.snapshot()
            assertEquals(before.revision + 1, snapshot.revision)
            assertEquals(StockHealth.LowStock, snapshot.products.single { it.id == id }.stockHealth)
            shop.setLowStockThreshold(id, "005")
            assertEquals(snapshot, shop.snapshot())
            for (invalid in listOf("-1", "1.5", "1e3", "1,000", "9223372036854775808")) {
                try { shop.setLowStockThreshold(id, invalid); fail("Invalid threshold accepted: $invalid") } catch (e: ShopValidation) {
                    assertEquals(ProductField.LowStockThreshold, e.field)
                }
                assertEquals(snapshot, shop.snapshot())
            }
            Shop.open(context, name).use { assertEquals(snapshot, it.snapshot()) }
            shop.setLowStockThreshold(id, "4")
            assertEquals(StockHealth.InStock, shop.snapshot().products.single { it.id == id }.stockHealth)
            shop.setLowStockThreshold(id, "0")
            assertEquals(0L, shop.snapshot().products.single { it.id == id }.lowStockThreshold)
            shop.setLowStockThreshold(id, "")
            snapshot = shop.snapshot()
            assertNull(snapshot.products.single { it.id == id }.lowStockThreshold)
            assertEquals(before.products, snapshot.products)
            assertEquals(before.openings, snapshot.openings)
            Shop.open(context, name).use { assertEquals(snapshot, it.snapshot()) }
        }
    }

    @Test fun thresholdRevisionInterruptionRollsBackAfterReopen() = runBlocking {
        withShop { shop, context, name ->
            val id = shop.createProduct("Pad", shop.snapshot().categories.first().id, "", "5", "80")
            val before = shop.snapshot()
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                it.execSQL("CREATE TRIGGER fail_revision BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interruption'); END")
            }
            try { shop.setLowStockThreshold(id, "5"); fail("Should roll back") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(before, shop.snapshot())
            Shop.open(context, name).use { assertEquals(before, it.snapshot()) }
        }
    }

    @Test fun descriptiveSearchCombinesCatalogFitmentLocationAndExactDecimals() = runBlocking {
        withShop { shop, context, name ->
            val category = shop.snapshot().categories.first().id
            val diameter = shop.createAttribute(category, "Diameter", AttributeType.Decimal, unit = "mm")
            val finish = shop.createAttribute(category, "Finish", AttributeType.SingleChoice)
            val zinc = shop.createOption(finish, "Zinc plated")
            val make = shop.createMake("Hero")
            val model = shop.createModel(make, "Splendor")
            val location = shop.createLocation("Rear shelf")
            val id = shop.createProduct("Workshop bolt", category, "Flanged stainless", "8", "90",
                attributes = mapOf(diameter to AttributeValue.Decimal("008.00"), finish to AttributeValue.Choices(setOf(zinc))),
                modelIds = setOf(model), locationIds = setOf(location))
            shop.createProduct("Other bolt", category, "", "0", "50", attributes = mapOf(diameter to AttributeValue.Decimal("18.0")))
            val precise = shop.createProduct("Straße precision bolt", category, "", "1", "50", attributes = mapOf(diameter to AttributeValue.Decimal("9007199254740993.000000000001")))
            val snapshot = shop.snapshot()
            val search = CatalogSearch(snapshot)
            for (query in listOf("stainless", "WORKSHOP zinc", "hero splendor", "rear shelf", "diameter 8", "8.000", snapshot.products.single { it.id == id }.sku)) {
                assertEquals(query, listOf(id), search.find(ProductFilter(query = query)).map { it.id })
            }
            assertEquals(listOf(id), search.find(ProductFilter(categoryId = category, modelId = model, locationId = location,
                health = StockHealth.InStock, decimal = DecimalFilter(diameter, "8.000"))).map { it.id })
            assertTrue(search.find(ProductFilter(query = "stainless", health = StockHealth.OutOfStock)).isEmpty())
            assertTrue(search.find(ProductFilter(decimal = DecimalFilter(diameter, "8.01"))).isEmpty())
            assertEquals(listOf(precise), search.find(ProductFilter(query = "STRASSE 9007199254740993.000000000001")).map { it.id })
            assertTrue(search.find(ProductFilter(decimal = DecimalFilter(diameter, "9007199254740993.000000000002"))).isEmpty())
            assertEquals(8L, search.find(ProductFilter(query = "zinc")).single().quantity)
            assertEquals(snapshot.revision, shop.snapshot().revision)
            Shop.open(context, name).use { assertEquals(listOf(id), CatalogSearch(it.snapshot()).find(ProductFilter(query = "8.000 zinc")).map { product -> product.id }) }
        }
    }

    private suspend fun withShop(action: suspend (Shop, Context, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "search-health-${UUID.randomUUID()}.db"
        try { Shop.open(context, name).use { action(it, context, name) } }
        finally { context.deleteDatabase(name) }
    }
}
