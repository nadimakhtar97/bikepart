package com.nadim.bikepart

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CategoryMigrationTest {
    @Test fun versionTwoUpgradePreservesTypedValuesAndSupportsEvolutionAfterReopen() = runBlocking { verifyTypedUpgrade(2) }

    @Test fun versionThreeUpgradePreservesTypedCatalogAndSupportsFindingDetails() = runBlocking { verifyTypedUpgrade(3) }

    @Test fun versionFourUpgradePreservesTypedCatalogAndSupportsPhotos() = runBlocking { verifyTypedUpgrade(4) }

    @Test fun versionFiveUpgradePreservesTypedCatalogAndSupportsTemplates() = runBlocking { verifyTypedUpgrade(5) }

    @Test fun versionSixUpgradePreservesTypedCatalogAndSupportsCorrections() = runBlocking { verifyTypedUpgrade(6) }

    @Test fun versionSevenUpgradePreservesCorrectionsAndSupportsThresholds() = runBlocking { verifyTypedUpgrade(7) }

    @Test fun versionEightUpgradePreservesCatalogAndSupportsCarts() = runBlocking { verifyTypedUpgrade(8) }

    private suspend fun verifyTypedUpgrade(version: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "upgrade-typed-${java.util.UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        try {
            val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("com.nadim.bikepart.shop.ShopDatabase/$version.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
                db.execSQL("INSERT INTO categories VALUES ('c', 'Bolts', 'bolts', 0)")
                db.execSQL("INSERT INTO products (id, sku, title, categoryId, description, priceRupees) VALUES ('p', 'BP-stable', 'Bolt', 'c', 'Original', 80)")
                if (version >= 8) db.execSQL("UPDATE products SET lowStockThreshold = 7 WHERE id = 'p'")
                db.execSQL("INSERT INTO balances VALUES ('p', 12)")
                db.execSQL("INSERT INTO opening_stock VALUES ('p', 12, 123456)")
                db.execSQL("INSERT INTO shop_state VALUES (1, 'stable-dataset', 7, 'Dark', 1)")
                db.execSQL("INSERT INTO category_attributes (id, categoryId, name, normalizedName, type, required, unit${if (version >= 3) ", ownerKey" else ""}) VALUES ('d', 'c', 'Diameter', 'diameter', 'Decimal', 0, 'mm'${if (version >= 3) ", 'category:c'" else ""})")
                db.execSQL("INSERT INTO category_attributes (id, categoryId, name, normalizedName, type, required, unit${if (version >= 3) ", ownerKey" else ""}) VALUES ('f', 'c', 'Finish', 'finish', 'MultipleChoice', 0, ''${if (version >= 3) ", 'category:c'" else ""})")
                db.execSQL("INSERT INTO attribute_options (id, definitionId, name, normalizedName${if (version >= 3) ", retired" else ""}) VALUES ('o', 'f', 'Zinc', 'zinc'${if (version >= 3) ", 0" else ""})")
                db.execSQL("INSERT INTO attribute_values (productId, definitionId, textValue, decimalValue, choiceIds) VALUES ('p', 'd', NULL, '008.00', NULL)")
                db.execSQL("INSERT INTO attribute_values (productId, definitionId, textValue, decimalValue, choiceIds) VALUES ('p', 'f', NULL, NULL, '[\"o\"]')")
                if (version >= 5) db.execSQL("INSERT INTO product_photos VALUES ('p', '${"a".repeat(64)}', 3)")
                if (version >= 6) db.execSQL("INSERT INTO product_templates VALUES ('t', 'c', ?, 0)", arrayOf(CatalogDraft("Template bolt", "c", "Original template").encode()))
                if (version >= 7) db.execSQL("INSERT INTO stock_corrections VALUES ('legacy-count', 'p', 'PhysicalCount', 12, 'Legacy recount', 12, 12, NULL, 123456, 7)")
                db.version = version
            }
            val values = mapOf("d" to AttributeValue.Decimal("008.00"), "f" to AttributeValue.Choices(setOf("o")))
            Shop.open(context, name).use { shop ->
                val before = shop.snapshot()
                assertEquals("stable-dataset", before.datasetId)
                assertEquals(7L, before.revision)
                assertEquals(Appearance.Dark, before.appearance)
                assertEquals(Product("p", "BP-stable", "Bolt", "c", "Original", 80, 12, values, photo = if (version >= 5) PhotoReference("a".repeat(64), 3) else null, lowStockThreshold = if (version >= 8) 7 else null), before.products.single())
                assertEquals(listOf(OpeningStock(12, 123456)), shop.openingHistory("p"))
                assertTrue(before.makes.isEmpty())
                assertTrue(before.models.isEmpty())
                assertTrue(before.locations.isEmpty())
                if (version >= 6) assertEquals(listOf(ProductTemplate("t", CatalogDraft("Template bolt", "c", "Original template"), false)), before.templates)
                val make = shop.createMake("Workshop make")
                val model = shop.createModel(make, "Workshop model")
                val location = shop.createLocation("Shelf")
                shop.editProduct("p", "Bolt", "c", "Original", modelIds = setOf(model), locationIds = setOf(location))
                shop.retireOption("o")
                shop.replaceAttribute("d", "New diameter", AttributeType.Text)
                val target = shop.createCategory("Other")
                shop.editProduct("p", "Bolt", target, "Original")
            }
            Shop.open(context, name).use { shop ->
                val after = shop.snapshot()
                assertEquals(15L, after.revision)
                assertEquals(setOf(after.models.single().id), after.products.single().modelIds)
                assertEquals(setOf(after.locations.single().id), after.products.single().locationIds)
                assertEquals(values, after.products.single().historicalAttributes.associate { it.definitionId to it.value })
                assertTrue(after.options.single().retired)
                assertTrue(after.attributes.single { it.id == "d" }.retired)
                assertEquals(12L, after.products.single().quantity)
                assertEquals(80L, after.products.single().priceRupees)
                assertEquals(listOf(OpeningStock(12, 123456)), shop.openingHistory("p"))
                if (version >= 6) assertEquals("Original template", after.templates.single().catalog.description)
            }
            Shop.open(context, name).use { shop ->
                val before = shop.snapshot()
                val id = before.products.single().id
                assertEquals(if (version >= 7) 1 else 0, shop.correctionHistory(id).size)
                shop.correctStock("migrated-count", id, CorrectionKind.PhysicalCount, "10", "Recount after upgrade")
                assertEquals(10L, shop.snapshot().products.single().quantity)
                assertEquals(before.revision + 1, shop.snapshot().revision)
                assertEquals(-2L, shop.correctionHistory(id).last().movement)
                assertEquals(12L, shop.openingHistory(id).single().quantity)
            }
            Shop.open(context, name).use { shop ->
                assertEquals(10L, shop.snapshot().products.single().quantity)
                assertEquals("Recount after upgrade", shop.correctionHistory(shop.snapshot().products.single().id).last().reason)
            }
            Shop.open(context, name).use { shop ->
                assertEquals(if (version >= 8) 7L else null, shop.snapshot().products.single().lowStockThreshold)
                shop.setLowStockThreshold("p", "10")
            }
            Shop.open(context, name).use { shop ->
                assertEquals(10L, shop.snapshot().products.single().lowStockThreshold)
                assertEquals(StockHealth.LowStock, shop.snapshot().products.single().stockHealth)
            }
            Shop.open(context, name).use { shop ->
                val before = shop.snapshot()
                assertTrue(before.carts.isEmpty())
                val cart = shop.createCart()
                shop.saveCart(cart, 0, "Upgrade customer", mapOf("p" to "2"))
                assertEquals(before.revision + 2, shop.snapshot().revision)
            }
            Shop.open(context, name).use { shop ->
                assertEquals("Upgrade customer", shop.snapshot().carts.single().customer)
                assertEquals(160L, shop.snapshot().carts.single().totalRupees)
                assertEquals(10L, shop.snapshot().products.single().quantity)
            }
            SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
                db.rawQuery("PRAGMA integrity_check", null).use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun versionOneUpgradePreservesCatalogIdentityStockHistorySettingsAndRevision() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "upgrade-${java.util.UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        try {
            val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("com.nadim.bikepart.shop.ShopDatabase/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
                db.execSQL("INSERT INTO categories VALUES ('category-1', 'Bolts', 'bolts', 0)")
                db.execSQL("INSERT INTO products VALUES ('product-1', 'BP-stable', 'M8 bolt', 'category-1', 'Original description', 80)")
                db.execSQL("INSERT INTO balances VALUES ('product-1', 12)")
                db.execSQL("INSERT INTO opening_stock VALUES ('product-1', 12, 123456)")
                db.execSQL("INSERT INTO shop_state VALUES (1, 'stable-dataset', 7, 'Dark')")
                db.version = 1
            }
            Shop.open(context, name).use { shop ->
                val snapshot = shop.snapshot()
                assertEquals("stable-dataset", snapshot.datasetId)
                assertEquals(7L, snapshot.revision)
                assertEquals(Appearance.Dark, snapshot.appearance)
                assertEquals(listOf(Category("category-1", "Bolts")), snapshot.categories)
                assertEquals(Product("product-1", "BP-stable", "M8 bolt", "category-1", "Original description", 80, 12), snapshot.products.single())
                assertEquals(listOf(OpeningStock(12, 123456)), shop.openingHistory("product-1"))
                assertTrue(snapshot.attributes.isEmpty())
                val field = shop.createAttribute("category-1", "Diameter", AttributeType.Decimal)
                shop.editProduct("product-1", "M8 bolt", "category-1", "Original description", mapOf(field to AttributeValue.Decimal("8.00")))
            }
            Shop.open(context, name).use { shop ->
                assertEquals(9L, shop.snapshot().revision)
                assertEquals(AttributeValue.Decimal("8.00"), shop.snapshot().products.single().attributes.values.single())
                assertEquals(12L, shop.openingHistory("product-1").single().quantity)
            }
            Shop.open(context, name).use { shop ->
                val before = shop.snapshot()
                val id = before.products.single().id
                assertTrue(shop.correctionHistory(id).isEmpty())
                shop.correctStock("migrated-count", id, CorrectionKind.PhysicalCount, "10", "Recount after upgrade")
                assertEquals(10L, shop.snapshot().products.single().quantity)
                assertEquals(before.revision + 1, shop.snapshot().revision)
                assertEquals(-2L, shop.correctionHistory(id).last().movement)
                assertEquals(12L, shop.openingHistory(id).single().quantity)
            }
            Shop.open(context, name).use { shop ->
                assertEquals(10L, shop.snapshot().products.single().quantity)
                assertEquals("Recount after upgrade", shop.correctionHistory(shop.snapshot().products.single().id).last().reason)
            }
            Shop.open(context, name).use { shop ->
                assertNull(shop.snapshot().products.single().lowStockThreshold)
                shop.setLowStockThreshold("product-1", "10")
            }
            Shop.open(context, name).use { shop ->
                assertEquals(10L, shop.snapshot().products.single().lowStockThreshold)
                assertEquals(StockHealth.LowStock, shop.snapshot().products.single().stockHealth)
            }
            SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
                db.rawQuery("PRAGMA integrity_check", null).use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            }
        } finally { context.deleteDatabase(name) }
    }
}
