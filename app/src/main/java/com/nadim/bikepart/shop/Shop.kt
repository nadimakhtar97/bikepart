package com.nadim.bikepart.shop

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.map

data class Category(val id: String, val name: String)
data class Product(val id: String, val sku: String, val title: String, val categoryId: String, val description: String, val priceRupees: Long, val quantity: Long)
data class OpeningStock(val quantity: Long, val recordedAt: Long)
enum class Appearance { System, Light, Dark }
data class ShopSnapshot(val categories: List<Category>, val products: List<Product>, val revision: Long, val appearance: Appearance)

class Shop private constructor(private val database: ShopDatabase) : AutoCloseable {
    private val dao = database.dao()
    val snapshots = dao.revisions().map { snapshot() }

    private suspend fun initialize() = database.withTransaction {
        if (dao.state() == null) {
            listOf("Brake pads", "Nuts", "Bolts", "Sealed oil bottles").forEachIndexed { index, name ->
                dao.insertCategory(CategoryRow(UUID.randomUUID().toString(), name, normalize(name), index))
            }
            dao.putState(StateRow(datasetId = UUID.randomUUID().toString()))
        }
    }

    suspend fun snapshot(): ShopSnapshot = database.withTransaction {
        val state = requireNotNull(dao.state())
        val balances = dao.balances().associateBy { it.productId }
        ShopSnapshot(dao.categories().map { Category(it.id, it.name) }, dao.products().map {
            Product(it.id, it.sku, it.title, it.categoryId, it.description, it.priceRupees, requireNotNull(balances[it.id]).quantity)
        }, state.revision, Appearance.valueOf(state.appearance))
    }

    suspend fun createProduct(title: String, categoryId: String, description: String, openingPieces: String, priceRupees: String, allowSimilar: Boolean = false): String = database.withTransaction {
        require(title.isNotBlank()) { "Enter a product title" }
        require(dao.categories().any { it.id == categoryId }) { "Choose a category" }
        val quantity = parseWhole(openingPieces, "Opening stock")
        val price = parseWhole(priceRupees, "Selling price")
        if (!allowSimilar && dao.products().any { normalize(it.title) == normalize(title) && it.categoryId == categoryId }) throw SimilarProduct()
        val id = UUID.randomUUID().toString()
        dao.insertProduct(ProductRow(id, "BP-${id.uppercase(Locale.ROOT)}", title.trim(), categoryId, description.trim(), price))
        dao.insertBalance(BalanceRow(id, quantity))
        if (quantity > 0) dao.insertOpening(OpeningRow(id, quantity, System.currentTimeMillis()))
        advanceRevision()
        id
    }

    suspend fun editProduct(id: String, title: String, categoryId: String, description: String) = database.withTransaction {
        require(title.isNotBlank()) { "Enter a product title" }
        require(dao.categories().any { it.id == categoryId }) { "Choose a category" }
        val product = dao.products().single { it.id == id }
        dao.updateProduct(product.copy(title = title.trim(), categoryId = categoryId, description = description.trim()))
        advanceRevision()
    }

    suspend fun renameCategory(id: String, name: String) = database.withTransaction {
        require(name.isNotBlank()) { "Enter a category name" }
        val key = normalize(name)
        require(dao.categories().none { it.id != id && it.normalizedName == key }) { "That category already exists" }
        dao.updateCategory(dao.categories().single { it.id == id }.copy(name = name.trim(), normalizedName = key))
        advanceRevision()
    }

    suspend fun setAppearance(appearance: Appearance) = database.withTransaction {
        val state = requireNotNull(dao.state())
        if (state.appearance != appearance.name) dao.putState(state.copy(appearance = appearance.name, revision = Math.addExact(state.revision, 1)))
    }

    suspend fun openingHistory(id: String): List<OpeningStock> = dao.openings(id).map { OpeningStock(it.quantity, it.recordedAt) }

    private suspend fun advanceRevision() {
        val state = requireNotNull(dao.state())
        dao.putState(state.copy(revision = Math.addExact(state.revision, 1)))
    }

    override fun close() = database.close()

    companion object {
        suspend fun open(context: Context, name: String = "shop.db"): Shop {
            val database = Room.databaseBuilder(context.applicationContext, ShopDatabase::class.java, name).build()
            return Shop(database).also { try { it.initialize() } catch (error: Throwable) { database.close(); throw error } }
        }
    }
}

class SimilarProduct : IllegalArgumentException("A similar product already exists. Open it from Stock or save a distinct product.")

internal fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .replace(Regex("[\\s\\p{Z}]+"), " ").trim().lowercase(Locale.ROOT)

fun parseWhole(value: String, label: String): Long {
    val text = value.trim()
    require(text.matches(Regex("[0-9]+"))) { "$label must be a nonnegative whole number" }
    return requireNotNull(text.toLongOrNull()) { "$label exceeds the supported whole-number limit" }
}

fun indianNumber(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    val prefix = digits.dropLast(3)
    val groups = prefix.reversed().chunked(2).joinToString(",").reversed()
    return "$groups,${digits.takeLast(3)}"
}
