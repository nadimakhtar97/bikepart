package com.nadim.bikepart.shop

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Archival preserves every existing product as active, with its ledger untouched. */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
    }
}

/** Additive migration: existing catalog identities, stock, history and revision are untouched. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shop_state ADD COLUMN normalizerVersion INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE TABLE IF NOT EXISTS category_attributes (id TEXT NOT NULL, categoryId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, type TEXT NOT NULL, required INTEGER NOT NULL, unit TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_category_attributes_categoryId_normalizedName ON category_attributes(categoryId, normalizedName)")
        db.execSQL("CREATE TABLE IF NOT EXISTS attribute_options (id TEXT NOT NULL, definitionId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(definitionId) REFERENCES category_attributes(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_attribute_options_definitionId_normalizedName ON attribute_options(definitionId, normalizedName)")
        db.execSQL("CREATE TABLE IF NOT EXISTS attribute_values (productId TEXT NOT NULL, definitionId TEXT NOT NULL, textValue TEXT, decimalValue TEXT, choiceIds TEXT, PRIMARY KEY(productId, definitionId), FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(definitionId) REFERENCES category_attributes(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_attribute_values_definitionId ON attribute_values(definitionId)")
    }
}

/** Ownership and lifecycle are additive; all previous meanings and exact values survive. */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE category_attributes ADD COLUMN productId TEXT REFERENCES products(id) ON DELETE RESTRICT")
        db.execSQL("ALTER TABLE category_attributes ADD COLUMN ownerKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE category_attributes SET ownerKey = 'category:' || categoryId")
        db.execSQL("ALTER TABLE category_attributes ADD COLUMN retired INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DROP INDEX index_category_attributes_categoryId_normalizedName")
        db.execSQL("CREATE INDEX index_category_attributes_categoryId ON category_attributes(categoryId)")
        db.execSQL("CREATE INDEX index_category_attributes_productId ON category_attributes(productId)")
        db.execSQL("CREATE UNIQUE INDEX index_category_attributes_ownerKey_normalizedName ON category_attributes(ownerKey, normalizedName)")
        db.execSQL("ALTER TABLE attribute_options ADD COLUMN retired INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE evolved_values (productId TEXT NOT NULL, definitionId TEXT NOT NULL, textValue TEXT, decimalValue TEXT, choiceIds TEXT, historyId TEXT NOT NULL DEFAULT '', PRIMARY KEY(productId, definitionId, historyId), FOREIGN KEY(productId) REFERENCES products(id) ON DELETE RESTRICT, FOREIGN KEY(definitionId) REFERENCES category_attributes(id) ON DELETE RESTRICT)")
        db.execSQL("INSERT INTO evolved_values (productId, definitionId, textValue, decimalValue, choiceIds) SELECT productId, definitionId, textValue, decimalValue, choiceIds FROM attribute_values")
        db.execSQL("DROP TABLE attribute_values")
        db.execSQL("ALTER TABLE evolved_values RENAME TO attribute_values")
        db.execSQL("CREATE INDEX index_attribute_values_definitionId ON attribute_values(definitionId)")
    }
}

/** Finding associations are additive; an empty fitment set retains unknown compatibility. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE motorcycle_makes (id TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, retired INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE UNIQUE INDEX index_motorcycle_makes_normalizedName ON motorcycle_makes(normalizedName)")
        db.execSQL("CREATE TABLE motorcycle_models (id TEXT NOT NULL, makeId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, retired INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(makeId) REFERENCES motorcycle_makes(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE UNIQUE INDEX index_motorcycle_models_makeId_normalizedName ON motorcycle_models(makeId, normalizedName)")
        db.execSQL("CREATE TABLE storage_locations (id TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, retired INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE UNIQUE INDEX index_storage_locations_normalizedName ON storage_locations(normalizedName)")
        db.execSQL("CREATE TABLE product_fitments (productId TEXT NOT NULL, modelId TEXT NOT NULL, PRIMARY KEY(productId, modelId), FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(modelId) REFERENCES motorcycle_models(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX index_product_fitments_modelId ON product_fitments(modelId)")
        db.execSQL("CREATE TABLE product_locations (productId TEXT NOT NULL, locationId TEXT NOT NULL, PRIMARY KEY(productId, locationId), FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(locationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX index_product_locations_locationId ON product_locations(locationId)")
    }
}

/** Existing products retain their neutral fallback until staff attach a photo. */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE product_photos (productId TEXT NOT NULL, sha256 TEXT NOT NULL, sizeBytes INTEGER NOT NULL, PRIMARY KEY(productId), FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
    }
}

/** Templates never acquire stock, prices, SKUs or locations. Existing rows are untouched. */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS product_templates (id TEXT NOT NULL, categoryId TEXT NOT NULL, catalog TEXT NOT NULL, retired INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_product_templates_categoryId ON product_templates(categoryId)")
    }
}

/** Correction audit and optional signed movement are one row, committed with the balance. */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE stock_corrections (operationId TEXT NOT NULL, productId TEXT NOT NULL, kind TEXT NOT NULL, quantity INTEGER NOT NULL, reason TEXT NOT NULL, previousBalance INTEGER NOT NULL, resultingBalance INTEGER NOT NULL, movement INTEGER, recordedAt INTEGER NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(operationId), FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX index_stock_corrections_productId ON stock_corrections(productId)")
    }
}

/** Existing products have no threshold; zero still means out of stock. */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN lowStockThreshold INTEGER")
    }
}

/** Unfinished quotes are separate from the stock ledger. */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE carts (id TEXT NOT NULL, number INTEGER NOT NULL, customer TEXT NOT NULL, editVersion INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE UNIQUE INDEX index_carts_number ON carts(number)")
        db.execSQL("CREATE TABLE cart_lines (cartId TEXT NOT NULL, productId TEXT NOT NULL, quantity INTEGER NOT NULL, quotedPriceRupees INTEGER NOT NULL, PRIMARY KEY(cartId, productId), FOREIGN KEY(cartId) REFERENCES carts(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX index_cart_lines_productId ON cart_lines(productId)")
        db.execSQL("CREATE TABLE cart_sequence (id INTEGER NOT NULL, lastNumber INTEGER NOT NULL, PRIMARY KEY(id))")
    }
}

/** Existing open carts and their quotes survive; completed carts are identified by sales.cartId. */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE sales (id TEXT NOT NULL, cartId TEXT NOT NULL, cartNumber INTEGER NOT NULL, customer TEXT NOT NULL, recordedAt INTEGER NOT NULL, revision INTEGER NOT NULL, totalRupees INTEGER NOT NULL, cashRupees INTEGER NOT NULL, changeRupees INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE UNIQUE INDEX index_sales_cartId ON sales(cartId)")
        db.execSQL("CREATE TABLE sale_lines (saleId TEXT NOT NULL, productId TEXT NOT NULL, sku TEXT NOT NULL, title TEXT NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, fitment TEXT NOT NULL, quantity INTEGER NOT NULL, quotedPriceRupees INTEGER NOT NULL, previousBalance INTEGER NOT NULL, resultingBalance INTEGER NOT NULL, movement INTEGER NOT NULL, PRIMARY KEY(saleId, productId), FOREIGN KEY(saleId) REFERENCES sales(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX index_sale_lines_productId ON sale_lines(productId)")
    }
}
