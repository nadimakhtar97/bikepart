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
            SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
                db.rawQuery("PRAGMA integrity_check", null).use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            }
        } finally { context.deleteDatabase(name) }
    }
}
