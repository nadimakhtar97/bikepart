package com.nadim.bikepart.shop

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.map

data class Category(val id: String, val name: String)
data class Product(val id: String, val sku: String, val title: String, val categoryId: String, val description: String, val priceRupees: Long, val quantity: Long, val attributes: Map<String, AttributeValue> = emptyMap(), val historicalAttributes: List<HistoricalAttribute> = emptyList(), val modelIds: Set<String> = emptySet(), val locationIds: Set<String> = emptySet(), val photo: PhotoReference? = null, val lowStockThreshold: Long? = null)
data class HistoricalAttribute(val id: String, val definitionId: String, val value: AttributeValue)

data class OpeningStock(val quantity: Long, val recordedAt: Long)
enum class Appearance { System, Light, Dark }
data class ShopSnapshot(val categories: List<Category>, val products: List<Product>, val revision: Long, val appearance: Appearance, val attributes: List<CategoryAttribute> = emptyList(), val options: List<AttributeOption> = emptyList(), val datasetId: String = "", val makes: List<MotorcycleMake> = emptyList(), val models: List<MotorcycleModel> = emptyList(), val locations: List<StorageLocation> = emptyList(), val templates: List<ProductTemplate> = emptyList(), val corrections: List<StockCorrection> = emptyList(), val openings: Map<String, OpeningStock> = emptyMap())
enum class ProductField(val label: String) { Title("Title"), Category("Category"), OpeningStock("Opening stock"), SellingPrice("Selling price"), Attribute("Category field"), DefinitionName("Field name"), DefinitionType("Field type"), Fitment("Fitment"), Location("Location"), Photo("Photo"), CorrectionQuantity("Correction quantity"), CorrectionReason("Reason"), LowStockThreshold("Low-stock threshold") }
class ShopValidation(val field: ProductField, message: String, val definitionId: String? = null) : IllegalArgumentException(message)

class Shop private constructor(private val database: ShopDatabase, private val photos: ProductPhotos) : AutoCloseable {
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
        val photoRows = dao.productPhotos().associateBy { it.productId }
        val balances = dao.balances().associateBy { it.productId }
        val values = dao.values().groupBy { it.productId }
        val fitments = dao.fitments().groupBy { it.productId }
        val locations = dao.productLocations().groupBy { it.productId }
        ShopSnapshot(dao.categories().map { Category(it.id, it.name) }, dao.products().map {
            Product(it.id, it.sku, it.title, it.categoryId, it.description, it.priceRupees, requireNotNull(balances[it.id]).quantity, values[it.id].orEmpty().filter { it.historyId.isEmpty() }.associate { value -> value.definitionId to decodeValue(value) }, values[it.id].orEmpty().filter { it.historyId.isNotEmpty() }.map { value -> HistoricalAttribute(value.historyId, value.definitionId, decodeValue(value)) }, fitments[it.id].orEmpty().map { row -> row.modelId }.toSet(), locations[it.id].orEmpty().map { row -> row.locationId }.toSet(), photoRows[it.id]?.let { row -> PhotoReference(row.sha256, row.sizeBytes) }, it.lowStockThreshold)
        }, state.revision, Appearance.valueOf(state.appearance),
            dao.attributes().map { CategoryAttribute(it.id, it.categoryId, it.name, AttributeType.valueOf(it.type), it.required, it.unit, it.productId, it.retired) },
            dao.options().map { AttributeOption(it.id, it.definitionId, it.name, it.retired) }, state.datasetId,
            dao.makes().map { MotorcycleMake(it.id, it.name, it.retired) },
            dao.models().map { MotorcycleModel(it.id, it.makeId, it.name, it.retired) },
            dao.locations().map { StorageLocation(it.id, it.name, it.retired) },
            dao.templates().map { ProductTemplate(it.id, decodeCatalog(it.catalog), it.retired) }, dao.corrections().map { it.toCorrection() }, dao.allOpenings().associate { it.productId to OpeningStock(it.quantity, it.recordedAt) })
    }

    suspend fun createProduct(title: String, categoryId: String, description: String, openingPieces: String, priceRupees: String, allowSimilar: Boolean = false, attributes: Map<String, AttributeValue> = emptyMap(), modelIds: Set<String> = emptySet(), locationIds: Set<String> = emptySet(), photo: PhotoChange = PhotoChange.Keep): String {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        return database.withTransaction {
            validateProduct(title, categoryId)
            val values = validateAttributes(categoryId, attributes)
            val quantity = parseWhole(openingPieces, ProductField.OpeningStock)
            val price = parseWhole(priceRupees, ProductField.SellingPrice)
            if (!allowSimilar && dao.products().any { LookupNames.key(it.title) == LookupNames.key(title) && it.categoryId == categoryId }) throw SimilarProduct()
            val id = UUID.randomUUID().toString()
            dao.insertProduct(ProductRow(id, "BP-${id.uppercase(Locale.ROOT)}", title.trim(), categoryId, description.trim(), price))
            dao.insertValues(values.map { it.copy(productId = id) })
            saveFindingDetails(id, modelIds, locationIds)
            if (imported != null) dao.putPhoto(ProductPhotoRow(id, imported.sha256, imported.sizeBytes))
            dao.insertBalance(BalanceRow(id, quantity))
            if (quantity > 0) dao.insertOpening(OpeningRow(id, quantity, System.currentTimeMillis()))
            advanceRevision()
            id
        }
    }

    suspend fun editProduct(id: String, title: String, categoryId: String, description: String, attributes: Map<String, AttributeValue>? = null, categoryMappings: Map<String, String> = emptyMap(), modelIds: Set<String>? = null, locationIds: Set<String>? = null, photo: PhotoChange = PhotoChange.Keep) {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        database.withTransaction {
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
            saveFindingDetails(id, modelIds ?: dao.fitments().filter { it.productId == id }.map { it.modelId }.toSet(), locationIds ?: dao.productLocations().filter { it.productId == id }.map { it.locationId }.toSet())
            dao.deleteValues(id)
            dao.insertValues(historical + values.map { it.copy(productId = id) })
            when (photo) {
                PhotoChange.Keep -> Unit
                PhotoChange.Remove -> dao.deletePhoto(id)
                is PhotoChange.Import -> dao.putPhoto(ProductPhotoRow(id, requireNotNull(imported).sha256, imported.sizeBytes))
            }
            dao.updateProduct(product.copy(title = title.trim(), categoryId = categoryId, description = description.trim()))
            advanceRevision()
        }
    }
    suspend fun readPhoto(reference: PhotoReference): ByteArray? = photos.read(reference)

    suspend fun setLowStockThreshold(id: String, pieces: String) = database.withTransaction {
        val threshold = pieces.takeIf { it.isNotBlank() }?.let { parseWhole(it, ProductField.LowStockThreshold) }
        val product = dao.products().single { it.id == id }
        if (product.lowStockThreshold != threshold) {
            dao.updateProduct(product.copy(lowStockThreshold = threshold))
            advanceRevision()
        }
    }

    suspend fun saveTemplate(catalog: CatalogDraft, id: String? = null, photo: PhotoChange = PhotoChange.Keep): String {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        return database.withTransaction {
            val previous = id?.let { key -> dao.templates().single { it.id == key }.also { require(!it.retired) { "That template is retired" } } }
            val old = previous?.let { decodeCatalog(it.catalog) }
            val definitions = dao.attributes()
            val moved = if (old != null && old.categoryId != catalog.categoryId) old.attributes.filterKeys { key ->
                definitions.any { it.id == key && it.productId == null && it.categoryId == old.categoryId }
            }.map { (definition, value) -> HistoricalAttribute(UUID.randomUUID().toString(), definition, value) } else emptyList()
            val draft = catalog.copy(title = catalog.title.trim(), description = catalog.description.trim(), historicalAttributes = old?.historicalAttributes.orEmpty() + moved, photo = when (photo) {
                PhotoChange.Keep -> catalog.photo
                PhotoChange.Remove -> null
                is PhotoChange.Import -> imported
            })
            validateCatalog(draft, old, allowIncomplete = true)
            val key = id ?: UUID.randomUUID().toString()
            val row = TemplateRow(key, draft.categoryId, draft.encode())
            if (previous == null) dao.insertTemplate(row) else dao.updateTemplate(row)
            advanceRevision()
            key
        }
    }

    suspend fun retireTemplate(id: String) = database.withTransaction {
        val row = dao.templates().single { it.id == id }
        if (!row.retired) { dao.updateTemplate(row.copy(retired = true)); advanceRevision() }
    }

    suspend fun createFromCatalog(catalog: CatalogDraft, openingPieces: String, priceRupees: String,
        allowSimilar: Boolean = false, locationIds: Set<String> = emptySet(), photo: PhotoChange = PhotoChange.Keep): String {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        return database.withTransaction {
            val entered = catalog.copy(photo = when (photo) {
                PhotoChange.Keep -> catalog.photo
                PhotoChange.Remove -> null
                is PhotoChange.Import -> imported
            })
            validateCatalog(entered)
            val draft = entered.independent()
            val customIds = draft.customFields.map { it.id }.toSet()
            val id = createProduct(draft.title, draft.categoryId, draft.description, openingPieces, priceRupees,
                allowSimilar, draft.attributes.filterKeys { it !in customIds }, draft.modelIds, locationIds)
            for (field in draft.customFields) {
                dao.insertAttribute(field.row(draft.categoryId, id))
                field.options.forEach { dao.insertOption(OptionRow(it.id, field.id, it.name, LookupNames.key(it.name))) }
            }
            val values = validateAttributes(draft.categoryId, draft.attributes, id)
            dao.deleteValues(id)
            dao.insertValues(values.map { it.copy(productId = id) })
            draft.photo?.let { dao.putPhoto(ProductPhotoRow(id, it.sha256, it.sizeBytes)) }
            // createProduct advanced the revision inside this same enclosing transaction.
            id
        }
    }

    private suspend fun validateCatalog(draft: CatalogDraft, previous: CatalogDraft? = null, allowIncomplete: Boolean = false) {
        validateProduct(draft.title, draft.categoryId)
        require(draft.customFields.map { it.id }.distinct().size == draft.customFields.size) { "Duplicate custom field identity" }
        val repeated = draft.customFields.groupBy { LookupNames.key(it.name) }.values.firstOrNull { it.size > 1 }?.last()
        if (repeated != null) throw ShopValidation(ProductField.DefinitionName, "Custom field names must be distinct", repeated.id)
        val definitions = draft.customFields.map { field ->
            if (LookupNames.display(field.name).isEmpty()) throw ShopValidation(ProductField.DefinitionName, "Enter a custom field name", field.id)
            require(field.options.all { it.definitionId == field.id && !it.retired && LookupNames.key(it.name).isNotEmpty() }) { "Choose valid custom options" }
            require(field.options.map { LookupNames.key(it.name) }.distinct().size == field.options.size) { "Custom option names must be distinct" }
            require(field.type.isChoice || field.options.isEmpty()) { "Only choice fields have options" }
            field.row(draft.categoryId, "catalog-draft")
        }
        val options = draft.customFields.flatMap { it.options }.map { OptionRow(it.id, it.definitionId, it.name, LookupNames.key(it.name)) }
        require((definitions.map { it.id } + options.map { it.id }).distinct().size == definitions.size + options.size) { "Custom identities must be distinct" }
        require(dao.attributes().none { existing -> definitions.any { it.id == existing.id } }) { "Custom fields must be independently owned" }
        validateAttributes(draft.categoryId, draft.attributes, "catalog-draft", previous?.attributes.orEmpty(), allowIncomplete, definitions, options)
        val snapshot = snapshot()
        if (draft.modelIds.any { id -> snapshot.models.none { it.id == id && ((!it.retired && snapshot.makes.any { make -> make.id == it.makeId && !make.retired }) || id in previous?.modelIds.orEmpty()) } })
            throw ShopValidation(ProductField.Fitment, "Choose active motorcycle models")
        draft.photo?.let { reference ->
            require(reference == previous?.photo || dao.productPhotos().any { it.sha256 == reference.sha256 && it.sizeBytes == reference.sizeBytes } || dao.templates().any { decodeCatalog(it.catalog).photo == reference } || photos.read(reference) != null) { "Photo unavailable. Choose it again or remove it." }
        }
    }

    suspend fun createMake(name: String): String = database.withTransaction {
        val display = lookupDisplay(name)
        dao.makes().find { it.normalizedName == LookupNames.key(display) }?.also {
            require(!it.retired) { "That make is retired; choose another name" }
        }?.id ?: UUID.randomUUID().toString().also {
            dao.insertMake(MakeRow(it, display, LookupNames.key(display))); advanceRevision()
        }
    }

    suspend fun createModel(makeId: String, name: String): String = database.withTransaction {
        require(dao.makes().any { it.id == makeId && !it.retired }) { "Choose an active make" }
        val display = lookupDisplay(name)
        dao.models().find { it.makeId == makeId && it.normalizedName == LookupNames.key(display) }?.also {
            require(!it.retired) { "That model is retired; choose another name" }
        }?.id ?: UUID.randomUUID().toString().also {
            dao.insertModel(ModelRow(it, makeId, display, LookupNames.key(display))); advanceRevision()
        }
    }

    suspend fun createLocation(name: String): String = database.withTransaction {
        val display = lookupDisplay(name)
        dao.locations().find { it.normalizedName == LookupNames.key(display) }?.also {
            require(!it.retired) { "That location is retired; choose another name" }
        }?.id ?: UUID.randomUUID().toString().also {
            dao.insertLocation(LocationRow(it, display, LookupNames.key(display))); advanceRevision()
        }
    }

    suspend fun retireMake(id: String) = database.withTransaction {
        val row = dao.makes().single { it.id == id }
        if (!row.retired) { dao.updateMake(row.copy(retired = true)); advanceRevision() }
    }

    suspend fun retireModel(id: String) = database.withTransaction {
        val row = dao.models().single { it.id == id }
        if (!row.retired) { dao.updateModel(row.copy(retired = true)); advanceRevision() }
    }

    suspend fun retireLocation(id: String) = database.withTransaction {
        val row = dao.locations().single { it.id == id }
        if (!row.retired) { dao.updateLocation(row.copy(retired = true)); advanceRevision() }
    }

    private suspend fun saveFindingDetails(id: String, modelIds: Set<String>, locationIds: Set<String>) {
        val previousModels = dao.fitments().filter { it.productId == id }.map { it.modelId }.toSet()
        val previousLocations = dao.productLocations().filter { it.productId == id }.map { it.locationId }.toSet()
        val makes = dao.makes().associateBy { it.id }
        val models = dao.models().associateBy { it.id }
        val locations = dao.locations().associateBy { it.id }
        if (modelIds.any { modelId -> models[modelId]?.let { (!it.retired && makes[it.makeId]?.retired == false) || modelId in previousModels } != true })
            throw ShopValidation(ProductField.Fitment, "Choose active motorcycle models; retired fitment may only be retained")
        if (locationIds.any { locationId -> locations[locationId]?.let { !it.retired || locationId in previousLocations } != true })
            throw ShopValidation(ProductField.Location, "Choose active locations; retired locations may only be retained")
        dao.deleteFitments(id)
        dao.deleteLocations(id)
        dao.insertFitments(modelIds.map { FitmentRow(id, it) })
        dao.insertLocations(locationIds.map { ProductLocationRow(id, it) })
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
        createDefinition(categoryId, null, name, type, required, unit)
    }

    private suspend fun createDefinition(categoryId: String, productId: String?, name: String, type: AttributeType, required: Boolean, unit: String): String {
        val display = LookupNames.display(name)
        if (display.isEmpty()) throw ShopValidation(ProductField.DefinitionName, "Enter a field name")
        val ownerKey = if (productId == null) "category:$categoryId" else "product:$productId"
        val existing = dao.attributes().find { it.ownerKey == ownerKey && it.normalizedName == LookupNames.key(display) }
        if (existing != null) {
            if (existing.retired || existing.type != type.name || existing.required != required || existing.unit != unit.trim())
                throw ShopValidation(ProductField.DefinitionName, "A field with that name has different settings or is retired. Choose a distinct name.")
            return existing.id
        } else {
            val id = UUID.randomUUID().toString()
            dao.insertAttribute(AttributeRow(id, categoryId, display, LookupNames.key(display), type.name, required, unit.trim(), productId, ownerKey))
            advanceRevision()
            return id
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
        if (field.retired || field.type == type.name) throw ShopValidation(ProductField.DefinitionType, "Choose a new field type for replacement", id)
        val display = LookupNames.display(name)
        if (display.isEmpty()) throw ShopValidation(ProductField.DefinitionName, "Enter a replacement name", id)
        if (dao.attributes().any { it.ownerKey == field.ownerKey && it.normalizedName == LookupNames.key(display) })
            throw ShopValidation(ProductField.DefinitionName, "Choose a distinct replacement name; the original meaning is retained", id)
        val replacement = UUID.randomUUID().toString()
        dao.insertAttribute(field.copy(id = replacement, name = display, normalizedName = LookupNames.key(display), type = type.name, required = required, unit = unit.trim()))
        dao.updateAttribute(field.copy(retired = true))
        advanceRevision()
        replacement
    }

    suspend fun createCustomAttribute(productId: String, name: String, type: AttributeType, required: Boolean = false, unit: String = ""): String = database.withTransaction {
        val product = dao.products().single { it.id == productId }
        createDefinition(product.categoryId, productId, name, type, required, unit)
    }

    suspend fun promoteCustomAttribute(definitionId: String, name: String) = database.withTransaction {
        val field = dao.attributes().single { it.id == definitionId }
        require(field.productId != null && !field.retired) { "Choose an active product custom field" }
        val categoryId = dao.products().single { it.id == field.productId }.categoryId
        val display = LookupNames.display(name)
        if (display.isEmpty()) throw ShopValidation(ProductField.DefinitionName, "Enter a category field name", definitionId)
        if (dao.attributes().any { it.productId == null && it.categoryId == categoryId && it.normalizedName == LookupNames.key(display) })
            throw ShopValidation(ProductField.DefinitionName, "That category field already exists. Choose a distinct name and meaning; fields are never merged.", definitionId)
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

    private suspend fun validateAttributes(categoryId: String, values: Map<String, AttributeValue>, productId: String? = null, existing: Map<String, AttributeValue> = emptyMap(), allowIncomplete: Boolean = false, customDefinitions: List<AttributeRow> = emptyList(), customOptions: List<OptionRow> = emptyList()): List<AttributeValueRow> {
        val definitions = dao.attributes().filter { it.productId != null && it.productId == productId || it.productId == null && it.categoryId == categoryId } + customDefinitions
        require(values.keys.all { key -> definitions.any { it.id == key } }) { "Choose fields from the selected category" }
        val options = dao.options() + customOptions
        return definitions.mapNotNull { definition ->
            val value = values[definition.id]
            fun invalid(message: String): Nothing = throw ShopValidation(ProductField.Attribute, definition.name + ": " + message, definition.id)
            if (definition.retired && value != null && value != existing[definition.id]) invalid("Retired field cannot be newly selected or changed")
            if (!allowIncomplete && !definition.retired && definition.required && (value == null || value is AttributeValue.Text && value.text.isBlank() || value is AttributeValue.Choices && value.optionIds.isEmpty())) invalid("Required")
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

    /** Retry with the same operation identity and inputs; reuse for a different command is rejected. */
    suspend fun correctStock(operationId: String, productId: String, kind: CorrectionKind, quantity: String, reason: String) = database.withTransaction {
        require(operationId.isNotBlank()) { "A correction operation identity is required" }
        val amount = parseWhole(quantity, ProductField.CorrectionQuantity)
        if (kind != CorrectionKind.PhysicalCount && amount == 0L) throw ShopValidation(ProductField.CorrectionQuantity, "Enter a positive whole quantity to remove")
        val explanation = reason.trim()
        if (explanation.isEmpty()) throw ShopValidation(ProductField.CorrectionReason, "Explain why this stock correction is needed")
        val previous = dao.correction(operationId)
        if (previous != null) {
            require(previous.productId == productId && previous.kind == kind.name && previous.quantity == amount && previous.reason == explanation) { "This operation identity already belongs to a different correction" }
            return@withTransaction
        }
        val balance = dao.balances().singleOrNull { it.productId == productId }
            ?: throw IllegalArgumentException("Choose an existing stocked product")
        val result = if (kind == CorrectionKind.PhysicalCount) amount else {
            if (amount > balance.quantity) throw ShopValidation(ProductField.CorrectionQuantity, "Cannot remove more than the current stock")
            Math.subtractExact(balance.quantity, amount)
        }
        val difference = Math.subtractExact(result, balance.quantity)
        dao.insertCorrection(CorrectionRow(operationId, productId, kind.name, amount, explanation, balance.quantity, result, difference.takeIf { it != 0L }, System.currentTimeMillis(), Math.addExact(requireNotNull(dao.state()).revision, 1)))
        dao.updateBalance(balance.copy(quantity = result))
        advanceRevision()
    }

    suspend fun correctionHistory(productId: String): List<StockCorrection> = dao.corrections().filter { it.productId == productId }.map { it.toCorrection() }

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
            val database = Room.databaseBuilder(context.applicationContext, ShopDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build()
            val photoDirectory = java.io.File(context.filesDir, "product-photos/" + java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) })
            return Shop(database, ProductPhotos(photoDirectory)).also { try { it.initialize() } catch (error: Throwable) { database.close(); throw error } }
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
