package com.nadim.bikepart.shop

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
