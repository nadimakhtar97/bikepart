package com.nadim.bikepart.shop

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.map

data class Category(val id: String, val name: String)
data class Product(val id: String, val sku: String, val title: String, val categoryId: String, val description: String, val priceRupees: Long, val quantity: Long, val attributes: Map<String, AttributeValue> = emptyMap(), val historicalAttributes: List<HistoricalAttribute> = emptyList())
data class HistoricalAttribute(val id: String, val definitionId: String, val value: AttributeValue)

data class OpeningStock(val quantity: Long, val recordedAt: Long)
enum class Appearance { System, Light, Dark }
data class ShopSnapshot(val categories: List<Category>, val products: List<Product>, val revision: Long, val appearance: Appearance, val attributes: List<CategoryAttribute> = emptyList(), val options: List<AttributeOption> = emptyList(), val datasetId: String = "")
enum class ProductField(val label: String) { Title("Title"), Category("Category"), OpeningStock("Opening stock"), SellingPrice("Selling price"), Attribute("Category field") }
class ShopValidation(val field: ProductField, message: String, val definitionId: String? = null) : IllegalArgumentException(message)

class Shop private constructor(private val database: ShopDatabase) : AutoCloseable {
    private val dao = database.dao()
    val snapshots = dao.revisions().map { snapshot() }

    private suspend fun initialize() = database.withTransaction {
        require(dao.state()?.normalizerVersion?.let { it == LookupNames.VERSION } != false) { "Unsupported lookup normalizer version" }
        if (dao.state() == null) {
            listOf("Brake pads", "Nuts", "Bolts", "Sealed oil bottles").forEachIndexed { index, name ->
                dao.insertCategory(CategoryRow(UUID.randomUUID().toString(), name, LookupNames.key(name), index))
            }
            seedAttributes()
            dao.putState(StateRow(datasetId = UUID.randomUUID().toString()))
        }
    }

    suspend fun snapshot(): ShopSnapshot = database.withTransaction {
        val state = requireNotNull(dao.state())
        val balances = dao.balances().associateBy { it.productId }
        val values = dao.values().groupBy { it.productId }
        ShopSnapshot(dao.categories().map { Category(it.id, it.name) }, dao.products().map {
            Product(it.id, it.sku, it.title, it.categoryId, it.description, it.priceRupees, requireNotNull(balances[it.id]).quantity, values[it.id].orEmpty().filter { it.historyId.isEmpty() }.associate { value -> value.definitionId to decodeValue(value) }, values[it.id].orEmpty().filter { it.historyId.isNotEmpty() }.map { value -> HistoricalAttribute(value.historyId, value.definitionId, decodeValue(value)) })
        }, state.revision, Appearance.valueOf(state.appearance),
            dao.attributes().map { CategoryAttribute(it.id, it.categoryId, it.name, AttributeType.valueOf(it.type), it.required, it.unit, it.productId, it.retired) },
            dao.options().map { AttributeOption(it.id, it.definitionId, it.name, it.retired) }, state.datasetId)
    }

    suspend fun createProduct(title: String, categoryId: String, description: String, openingPieces: String, priceRupees: String, allowSimilar: Boolean = false, attributes: Map<String, AttributeValue> = emptyMap()): String = database.withTransaction {
        validateProduct(title, categoryId)
        val values = validateAttributes(categoryId, attributes)
        val quantity = parseWhole(openingPieces, ProductField.OpeningStock)
        val price = parseWhole(priceRupees, ProductField.SellingPrice)
        if (!allowSimilar && dao.products().any { LookupNames.key(it.title) == LookupNames.key(title) && it.categoryId == categoryId }) throw SimilarProduct()
        val id = UUID.randomUUID().toString()
        dao.insertProduct(ProductRow(id, "BP-${id.uppercase(Locale.ROOT)}", title.trim(), categoryId, description.trim(), price))
        dao.insertValues(values.map { it.copy(productId = id) })
        dao.insertBalance(BalanceRow(id, quantity))
        if (quantity > 0) dao.insertOpening(OpeningRow(id, quantity, System.currentTimeMillis()))
        advanceRevision()
        id
    }

    suspend fun editProduct(id: String, title: String, categoryId: String, description: String, attributes: Map<String, AttributeValue>? = null, categoryMappings: Map<String, String> = emptyMap()) = database.withTransaction {
        validateProduct(title, categoryId)
        val product = dao.products().single { it.id == id }
        val previous = dao.values().filter { it.productId == id }
        val existing = previous.filter { it.historyId.isEmpty() }.associate { it.definitionId to decodeValue(it) }
        val definitions = dao.attributes()
        val applicable = definitions.filter { it.productId == id || it.productId == null && it.categoryId == categoryId }.map { it.id }.toSet()
        val historical = previous.filter { it.historyId.isNotEmpty() || it.definitionId !in applicable }.map { if (it.historyId.isNotEmpty()) it else it.copy(historyId = UUID.randomUUID().toString()) }
        val input = (attributes ?: existing.filterKeys { it in applicable }).toMutableMap()
        require(categoryMappings.isEmpty() || product.categoryId != categoryId) { "Mapping requires a category change" }
        require(categoryMappings.values.toSet().size == categoryMappings.size) { "Map each target only once" }
        for ((sourceId, targetId) in categoryMappings) {
            val source = definitions.single { it.id == sourceId }
            val target = definitions.single { it.id == targetId }
            require(source.productId == null && source.categoryId == product.categoryId && sourceId in existing) { "Choose a previous category value" }
            require(target.productId == null && target.categoryId == categoryId && !target.retired && source.type == target.type && source.unit == target.unit && !AttributeType.valueOf(source.type).isChoice) { "Map fields with the same text or decimal type and unit; choice fields must be selected explicitly" }
            val mapped = existing.getValue(sourceId)
            require(targetId !in input || input[targetId] == mapped) { "Mapping conflicts with the entered target value" }
            input[targetId] = mapped
        }
        val values = validateAttributes(categoryId, input, id, existing)
        dao.deleteValues(id)
        dao.insertValues(historical + values.map { it.copy(productId = id) })
        dao.updateProduct(product.copy(title = title.trim(), categoryId = categoryId, description = description.trim()))
        advanceRevision()
    }

    suspend fun renameCategory(id: String, name: String) = database.withTransaction {
        require(LookupNames.display(name).isNotEmpty()) { "Enter a category name" }
        val key = LookupNames.key(name)
        require(dao.categories().none { it.id != id && LookupNames.key(it.name) == key }) { "That category already exists" }
        dao.updateCategory(dao.categories().single { it.id == id }.copy(name = LookupNames.display(name), normalizedName = key))
        advanceRevision()
    }

    suspend fun createCategory(name: String): String = database.withTransaction {
        val display = LookupNames.display(name)
        require(display.isNotEmpty()) { "Enter a category name" }
        dao.categories().find { LookupNames.key(it.name) == LookupNames.key(display) }?.id ?: run {
            val id = UUID.randomUUID().toString()
            dao.insertCategory(CategoryRow(id, display, LookupNames.key(display), dao.categories().size))
            advanceRevision()
            id
        }
    }

    suspend fun createAttribute(categoryId: String, name: String, type: AttributeType, required: Boolean = false, unit: String = ""): String = database.withTransaction {
        require(dao.categories().any { it.id == categoryId }) { "Choose a category" }
        val display = LookupNames.display(name)
        require(display.isNotEmpty()) { "Enter a field name" }
        val existing = dao.attributes().find { it.productId == null && it.categoryId == categoryId && it.normalizedName == LookupNames.key(display) }
        if (existing != null) {
            require(!existing.retired && existing.type == type.name && existing.required == required && existing.unit == unit.trim()) { "A field with that name has different settings. Select the existing field." }
            existing.id
        } else {
            val id = UUID.randomUUID().toString()
            dao.insertAttribute(AttributeRow(id, categoryId, display, LookupNames.key(display), type.name, required, unit.trim()))
            advanceRevision()
            id
        }
    }

    suspend fun retireAttribute(id: String) = database.withTransaction {
        val field = dao.attributes().single { it.id == id }
        if (!field.retired) { dao.updateAttribute(field.copy(retired = true)); advanceRevision() }
    }

    suspend fun retireOption(id: String) = database.withTransaction {
        val option = dao.options().single { it.id == id }
        if (!option.retired) { dao.updateOption(option.copy(retired = true)); advanceRevision() }
    }

    suspend fun setAttributeRequired(id: String, required: Boolean) = database.withTransaction {
        val field = dao.attributes().single { it.id == id }
        require(!field.retired) { "That field is retired" }
        if (field.required != required) { dao.updateAttribute(field.copy(required = required)); advanceRevision() }
    }

    suspend fun replaceAttribute(id: String, name: String, type: AttributeType, required: Boolean = false, unit: String = ""): String = database.withTransaction {
        val field = dao.attributes().single { it.id == id }
        require(!field.retired && field.type != type.name) { "Choose a new field type for replacement" }
        val display = LookupNames.display(name)
        require(display.isNotEmpty()) { "Enter a replacement name" }
        require(dao.attributes().none { it.ownerKey == field.ownerKey && it.normalizedName == LookupNames.key(display) }) { "Choose a distinct replacement name; the original meaning is retained" }
        val replacement = UUID.randomUUID().toString()
        dao.insertAttribute(field.copy(id = replacement, name = display, normalizedName = LookupNames.key(display), type = type.name, required = required, unit = unit.trim()))
        dao.updateAttribute(field.copy(retired = true))
        advanceRevision()
        replacement
    }

    suspend fun createCustomAttribute(productId: String, name: String, type: AttributeType, required: Boolean = false, unit: String = ""): String = database.withTransaction {
        val product = dao.products().single { it.id == productId }
        val display = LookupNames.display(name)
        require(display.isNotEmpty()) { "Enter a field name" }
        val existing = dao.attributes().find { it.productId == productId && it.normalizedName == LookupNames.key(display) }
        if (existing != null) {
            require(!existing.retired && existing.type == type.name && existing.required == required && existing.unit == unit.trim()) { "A custom field with that name has different settings or is retired" }
            existing.id
        } else {
            val id = UUID.randomUUID().toString()
            dao.insertAttribute(AttributeRow(id, product.categoryId, display, LookupNames.key(display), type.name, required, unit.trim(), productId, "product:$productId"))
            advanceRevision()
            id
        }
    }

    suspend fun promoteCustomAttribute(definitionId: String, name: String) = database.withTransaction {
        val field = dao.attributes().single { it.id == definitionId }
        require(field.productId != null && !field.retired) { "Choose an active product custom field" }
        val categoryId = dao.products().single { it.id == field.productId }.categoryId
        val display = LookupNames.display(name)
        require(display.isNotEmpty()) { "Enter a category field name" }
        require(dao.attributes().none { it.productId == null && it.categoryId == categoryId && it.normalizedName == LookupNames.key(display) }) { "That category field already exists. Choose a distinct name and meaning; fields are never merged." }
        dao.updateAttribute(field.copy(categoryId = categoryId, productId = null, ownerKey = "category:$categoryId", name = display, normalizedName = LookupNames.key(display)))
        advanceRevision()
    }

    suspend fun createOption(definitionId: String, name: String): String = database.withTransaction {
        require(dao.attributes().single { it.id == definitionId }.let { !it.retired && AttributeType.valueOf(it.type).isChoice }) { "Choose a choice field" }
        val display = LookupNames.display(name)
        require(display.isNotEmpty()) { "Enter an option name" }
        dao.options().find { it.definitionId == definitionId && it.normalizedName == LookupNames.key(display) }?.also { require(!it.retired) { "That option is retired; choose another name" } }?.id ?: run {
            val id = UUID.randomUUID().toString()
            dao.insertOption(OptionRow(id, definitionId, display, LookupNames.key(display)))
            advanceRevision()
            id
        }
    }

    private suspend fun seedAttributes() {
        dao.categories().forEach { category ->
            suspend fun field(name: String, type: AttributeType, unit: String = ""): String {
                val id = UUID.randomUUID().toString()
                dao.insertAttribute(AttributeRow(id, category.id, name, LookupNames.key(name), type.name, false, unit))
                return id
            }
            when (category.position) {
                0 -> {
                    val id = field("Position", AttributeType.SingleChoice)
                    listOf("Front", "Rear").forEach { dao.insertOption(OptionRow(UUID.randomUUID().toString(), id, it, LookupNames.key(it))) }
                }
                1, 2 -> {
                    field("Thread diameter", AttributeType.Decimal, "mm")
                    field("Thread pitch", AttributeType.Decimal, "mm")
                    if (category.position == 2) field("Length", AttributeType.Decimal, "mm")
                }
                3 -> {
                    field("Bottle volume", AttributeType.Decimal, "ml")
                    field("Viscosity grade", AttributeType.Text)
                }
            }
        }
    }

    private suspend fun validateAttributes(categoryId: String, values: Map<String, AttributeValue>, productId: String? = null, existing: Map<String, AttributeValue> = emptyMap()): List<AttributeValueRow> {
        val definitions = dao.attributes().filter { it.productId != null && it.productId == productId || it.productId == null && it.categoryId == categoryId }
        require(values.keys.all { key -> definitions.any { it.id == key } }) { "Choose fields from the selected category" }
        val options = dao.options()
        return definitions.mapNotNull { definition ->
            val value = values[definition.id]
            fun invalid(message: String): Nothing = throw ShopValidation(ProductField.Attribute, definition.name + ": " + message, definition.id)
            if (definition.retired && value != null && value != existing[definition.id]) invalid("Retired field cannot be newly selected or changed")
            if (!definition.retired && definition.required && (value == null || value is AttributeValue.Text && value.text.isBlank() || value is AttributeValue.Choices && value.optionIds.isEmpty())) invalid("Required")
            if (value == null) return@mapNotNull null
            when (value) {
                is AttributeValue.Text -> {
                    if (definition.type != AttributeType.Text.name) invalid("Enter the correct field type")
                    AttributeValueRow("", definition.id, value.text, null, null)
                }
                is AttributeValue.Decimal -> {
                    if (definition.type != AttributeType.Decimal.name) invalid("Enter the correct field type")
                    try { exactDecimal(value.exact) } catch (e: IllegalArgumentException) { invalid(requireNotNull(e.message)) }
                    AttributeValueRow("", definition.id, null, value.exact.trim(), null)
                }
                is AttributeValue.Choices -> {
                    if (!AttributeType.valueOf(definition.type).isChoice) invalid("Enter the correct field type")
                    if (definition.type == AttributeType.SingleChoice.name && value.optionIds.size > 1) invalid("Choose one option")
                    if (!value.optionIds.all { id -> options.any { it.id == id && it.definitionId == definition.id && (!it.retired || id in (existing[definition.id] as? AttributeValue.Choices)?.optionIds.orEmpty()) } }) invalid("Choose options from this field")
                    AttributeValueRow("", definition.id, null, null, org.json.JSONArray(value.optionIds.sorted()).toString())
                }
            }
        }
    }

    suspend fun setAppearance(appearance: Appearance) = database.withTransaction {
        val state = requireNotNull(dao.state())
        if (state.appearance != appearance.name) dao.putState(state.copy(appearance = appearance.name, revision = Math.addExact(state.revision, 1)))
    }

    suspend fun openingHistory(id: String): List<OpeningStock> = dao.openings(id).map { OpeningStock(it.quantity, it.recordedAt) }

    private suspend fun validateProduct(title: String, categoryId: String) {
        if (title.isBlank()) throw ShopValidation(ProductField.Title, "Enter a product title")
        if (dao.categories().none { it.id == categoryId }) throw ShopValidation(ProductField.Category, "Choose a category")
    }

    private suspend fun advanceRevision() {
        val state = requireNotNull(dao.state())
        dao.putState(state.copy(revision = Math.addExact(state.revision, 1)))
    }

    override fun close() = database.close()

    companion object {
        suspend fun open(context: Context, name: String = "shop.db"): Shop {
            val database = Room.databaseBuilder(context.applicationContext, ShopDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
            return Shop(database).also { try { it.initialize() } catch (error: Throwable) { database.close(); throw error } }
        }
    }
}

class SimilarProduct : IllegalArgumentException("A similar product already exists. Open it from Stock or save a distinct product.")

private fun parseWhole(value: String, field: ProductField): Long {
    val text = value.trim()
    if (!text.matches(Regex("[0-9]+"))) throw ShopValidation(field, "${field.label} must be a nonnegative whole number")
    return text.toLongOrNull() ?: throw ShopValidation(field, "${field.label} exceeds the supported whole-number limit")
}

fun indianNumber(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    val prefix = digits.dropLast(3)
    val groups = prefix.reversed().chunked(2).joinToString(",").reversed()
    return "$groups,${digits.takeLast(3)}"
}

private fun decodeValue(row: AttributeValueRow): AttributeValue = when {
    row.textValue != null -> AttributeValue.Text(row.textValue)
    row.decimalValue != null -> AttributeValue.Decimal(row.decimalValue)
    else -> {
        val array = org.json.JSONArray(requireNotNull(row.choiceIds))
        AttributeValue.Choices((0 until array.length()).map { array.getString(it) }.toSet())
    }
}
