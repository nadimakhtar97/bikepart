package com.nadim.bikepart.shop

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories", indices = [Index(value = ["normalizedName"], unique = true)])
internal data class CategoryRow(@PrimaryKey val id: String, val name: String, val normalizedName: String, val position: Int)

@Entity(tableName = "products", foreignKeys = [ForeignKey(entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId"), Index(value = ["sku"], unique = true)])
internal data class ProductRow(@PrimaryKey val id: String, val sku: String, val title: String, val categoryId: String, val description: String, val priceRupees: Long)

@Entity(tableName = "balances", foreignKeys = [ForeignKey(entity = ProductRow::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)])
internal data class BalanceRow(@PrimaryKey val productId: String, val quantity: Long)

// Zero opening stock has a balance but no invented movement.
@Entity(tableName = "opening_stock", foreignKeys = [ForeignKey(entity = ProductRow::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)])
internal data class OpeningRow(@PrimaryKey val productId: String, val quantity: Long, val recordedAt: Long)

@Entity(tableName = "shop_state")
internal data class StateRow(@PrimaryKey val id: Int = 1, val datasetId: String, val revision: Long = 0, val appearance: String = "System", @ColumnInfo(defaultValue = "1") val normalizerVersion: Int = LookupNames.VERSION)

@Entity(tableName = "category_attributes", foreignKeys = [ForeignKey(entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["categoryId", "normalizedName"], unique = true)])
internal data class AttributeRow(@PrimaryKey val id: String, val categoryId: String, val name: String, val normalizedName: String, val type: String, val required: Boolean, val unit: String)

@Entity(tableName = "attribute_options", foreignKeys = [ForeignKey(entity = AttributeRow::class, parentColumns = ["id"], childColumns = ["definitionId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["definitionId", "normalizedName"], unique = true)])
internal data class OptionRow(@PrimaryKey val id: String, val definitionId: String, val name: String, val normalizedName: String)

@Entity(tableName = "attribute_values", primaryKeys = ["productId", "definitionId"], foreignKeys = [ForeignKey(entity = ProductRow::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = AttributeRow::class, parentColumns = ["id"], childColumns = ["definitionId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("definitionId")])
internal data class AttributeValueRow(val productId: String, val definitionId: String, val textValue: String?, val decimalValue: String?, val choiceIds: String?)

@Dao
internal interface ShopDao {
    @Query("SELECT * FROM category_attributes ORDER BY name, id") suspend fun attributes(): List<AttributeRow>
    @Query("SELECT * FROM attribute_options ORDER BY name, id") suspend fun options(): List<OptionRow>
    @Query("SELECT * FROM attribute_values") suspend fun values(): List<AttributeValueRow>
    @Insert suspend fun insertAttribute(row: AttributeRow)
    @Insert suspend fun insertOption(row: OptionRow)
    @Insert suspend fun insertValues(rows: List<AttributeValueRow>)
    @Query("DELETE FROM attribute_values WHERE productId = :id") suspend fun deleteValues(id: String)
    @Query("SELECT * FROM categories ORDER BY position, name") suspend fun categories(): List<CategoryRow>
    @Query("SELECT * FROM products ORDER BY title, id") suspend fun products(): List<ProductRow>
    @Query("SELECT * FROM balances") suspend fun balances(): List<BalanceRow>
    @Query("SELECT * FROM opening_stock WHERE productId = :id") suspend fun openings(id: String): List<OpeningRow>
    @Query("SELECT * FROM shop_state WHERE id = 1") suspend fun state(): StateRow?
    @Query("SELECT revision FROM shop_state WHERE id = 1") fun revisions(): Flow<Long>
    @Insert suspend fun insertCategory(row: CategoryRow)
    @Update suspend fun updateCategory(row: CategoryRow)
    @Insert suspend fun insertProduct(row: ProductRow)
    @Update suspend fun updateProduct(row: ProductRow)
    @Insert suspend fun insertBalance(row: BalanceRow)
    @Insert suspend fun insertOpening(row: OpeningRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putState(row: StateRow)
}

@Database(entities = [CategoryRow::class, ProductRow::class, BalanceRow::class, OpeningRow::class, StateRow::class, AttributeRow::class, OptionRow::class, AttributeValueRow::class], version = 2, exportSchema = true)
internal abstract class ShopDatabase : RoomDatabase() { abstract fun dao(): ShopDao }
