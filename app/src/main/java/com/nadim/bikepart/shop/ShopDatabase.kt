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

@Entity(tableName = "category_attributes", foreignKeys = [ForeignKey(entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ProductRow::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId"), Index("productId"), Index(value = ["ownerKey", "normalizedName"], unique = true)])
internal data class AttributeRow(@PrimaryKey val id: String, val categoryId: String, val name: String, val normalizedName: String, val type: String, val required: Boolean, val unit: String, val productId: String? = null, @ColumnInfo(defaultValue = "''") val ownerKey: String = "category:$categoryId", @ColumnInfo(defaultValue = "0") val retired: Boolean = false)

@Entity(tableName = "attribute_options", foreignKeys = [ForeignKey(entity = AttributeRow::class, parentColumns = ["id"], childColumns = ["definitionId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["definitionId", "normalizedName"], unique = true)])
internal data class OptionRow(@PrimaryKey val id: String, val definitionId: String, val name: String, val normalizedName: String, @ColumnInfo(defaultValue = "0") val retired: Boolean = false)

@Entity(tableName = "attribute_values", primaryKeys = ["productId", "definitionId", "historyId"], foreignKeys = [ForeignKey(entity = ProductRow::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = AttributeRow::class, parentColumns = ["id"], childColumns = ["definitionId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("definitionId")])
internal data class AttributeValueRow(val productId: String, val definitionId: String, val textValue: String?, val decimalValue: String?, val choiceIds: String?, @ColumnInfo(defaultValue = "''") val historyId: String = "")

@Entity(tableName = "motorcycle_makes", indices = [Index(value = ["normalizedName"], unique = true)])
internal data class MakeRow(@PrimaryKey val id: String, val name: String, val normalizedName: String, val retired: Boolean = false)

@Entity(tableName = "motorcycle_models", foreignKeys = [ForeignKey(entity = MakeRow::class, parentColumns = ["id"], childColumns = ["makeId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["makeId", "normalizedName"], unique = true)])
internal data class ModelRow(@PrimaryKey val id: String, val makeId: String, val name: String, val normalizedName: String, val retired: Boolean = false)

@Entity(tableName = "storage_locations", indices = [Index(value = ["normalizedName"], unique = true)])
internal data class LocationRow(@PrimaryKey val id: String, val name: String, val normalizedName: String, val retired: Boolean = false)

@Entity(tableName = "product_fitments", primaryKeys = ["productId", "modelId"], foreignKeys = [ForeignKey(entity = ProductRow::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ModelRow::class, parentColumns = ["id"], childColumns = ["modelId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("modelId")])
internal data class FitmentRow(val productId: String, val modelId: String)

@Entity(tableName = "product_locations", primaryKeys = ["productId", "locationId"], foreignKeys = [ForeignKey(entity = ProductRow::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = LocationRow::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("locationId")])
internal data class ProductLocationRow(val productId: String, val locationId: String)

@Entity(tableName = "product_photos", foreignKeys = [ForeignKey(entity = ProductRow::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)])
internal data class ProductPhotoRow(@PrimaryKey val productId: String, val sha256: String, val sizeBytes: Long)

@Entity(tableName = "product_templates", foreignKeys = [ForeignKey(entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId")])
internal data class TemplateRow(@PrimaryKey val id: String, val categoryId: String, val catalog: String, val retired: Boolean = false)

// A count with no difference retains an audit row and has no movement.
@Entity(tableName = "stock_corrections", foreignKeys = [ForeignKey(entity = ProductRow::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("productId")])
internal data class CorrectionRow(@PrimaryKey val operationId: String, val productId: String, val kind: String, val quantity: Long, val reason: String, val previousBalance: Long, val resultingBalance: Long, val movement: Long?, val recordedAt: Long, val revision: Long)

@Dao
internal interface ShopDao {
    @Query("SELECT * FROM stock_corrections ORDER BY revision") suspend fun corrections(): List<CorrectionRow>
    @Query("SELECT * FROM stock_corrections WHERE operationId = :id") suspend fun correction(id: String): CorrectionRow?
    @Insert suspend fun insertCorrection(row: CorrectionRow)
    @Update suspend fun updateBalance(row: BalanceRow)
    @Query("SELECT * FROM product_templates ORDER BY id") suspend fun templates(): List<TemplateRow>
    @Insert suspend fun insertTemplate(row: TemplateRow)
    @Update suspend fun updateTemplate(row: TemplateRow)
    @Query("SELECT * FROM product_photos") suspend fun productPhotos(): List<ProductPhotoRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPhoto(row: ProductPhotoRow)
    @Query("DELETE FROM product_photos WHERE productId = :id") suspend fun deletePhoto(id: String)
    @Query("SELECT * FROM motorcycle_makes ORDER BY name, id") suspend fun makes(): List<MakeRow>
    @Query("SELECT * FROM motorcycle_models ORDER BY name, id") suspend fun models(): List<ModelRow>
    @Query("SELECT * FROM storage_locations ORDER BY name, id") suspend fun locations(): List<LocationRow>
    @Query("SELECT * FROM product_fitments ORDER BY productId, modelId") suspend fun fitments(): List<FitmentRow>
    @Query("SELECT * FROM product_locations ORDER BY productId, locationId") suspend fun productLocations(): List<ProductLocationRow>
    @Insert suspend fun insertMake(row: MakeRow)
    @Insert suspend fun insertModel(row: ModelRow)
    @Insert suspend fun insertLocation(row: LocationRow)
    @Update suspend fun updateMake(row: MakeRow)
    @Update suspend fun updateModel(row: ModelRow)
    @Update suspend fun updateLocation(row: LocationRow)
    @Insert suspend fun insertFitments(rows: List<FitmentRow>)
    @Insert suspend fun insertLocations(rows: List<ProductLocationRow>)
    @Query("DELETE FROM product_fitments WHERE productId = :id") suspend fun deleteFitments(id: String)
    @Query("DELETE FROM product_locations WHERE productId = :id") suspend fun deleteLocations(id: String)
    @Query("SELECT * FROM category_attributes ORDER BY name, id") suspend fun attributes(): List<AttributeRow>
    @Query("SELECT * FROM attribute_options ORDER BY name, id") suspend fun options(): List<OptionRow>
    @Query("SELECT * FROM attribute_values ORDER BY productId, definitionId, historyId") suspend fun values(): List<AttributeValueRow>
    @Update suspend fun updateAttribute(row: AttributeRow)
    @Update suspend fun updateOption(row: OptionRow)
    @Insert suspend fun insertAttribute(row: AttributeRow)
    @Insert suspend fun insertOption(row: OptionRow)
    @Insert suspend fun insertValues(rows: List<AttributeValueRow>)
    @Query("DELETE FROM attribute_values WHERE productId = :id") suspend fun deleteValues(id: String)
    @Query("SELECT * FROM categories ORDER BY position, name") suspend fun categories(): List<CategoryRow>
    @Query("SELECT * FROM products ORDER BY title, id") suspend fun products(): List<ProductRow>
    @Query("SELECT * FROM balances") suspend fun balances(): List<BalanceRow>
    @Query("SELECT * FROM opening_stock") suspend fun allOpenings(): List<OpeningRow>
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

@Database(entities = [CategoryRow::class, ProductRow::class, BalanceRow::class, OpeningRow::class, StateRow::class, AttributeRow::class, OptionRow::class, AttributeValueRow::class, MakeRow::class, ModelRow::class, LocationRow::class, FitmentRow::class, ProductLocationRow::class, ProductPhotoRow::class, TemplateRow::class, CorrectionRow::class], version = 7, exportSchema = true)
internal abstract class ShopDatabase : RoomDatabase() { abstract fun dao(): ShopDao }
