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
