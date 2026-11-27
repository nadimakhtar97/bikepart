package com.nadim.bikepart.shop

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.map

data class Category(val id: String, val name: String)
data class Product(val id: String, val sku: String, val title: String, val categoryId: String, val description: String, val priceRupees: Long, val quantity: Long, val attributes: Map<String, AttributeValue> = emptyMap(), val historicalAttributes: List<HistoricalAttribute> = emptyList(), val modelIds: Set<String> = emptySet(), val locationIds: Set<String> = emptySet(), val photo: PhotoReference? = null, val lowStockThreshold: Long? = null, val archived: Boolean = false)
data class HistoricalAttribute(val id: String, val definitionId: String, val value: AttributeValue)

data class OpeningStock(val quantity: Long, val recordedAt: Long)
enum class Appearance { System, Light, Dark }
data class ShopSnapshot(val categories: List<Category>, val products: List<Product>, val revision: Long, val appearance: Appearance, val attributes: List<CategoryAttribute> = emptyList(), val options: List<AttributeOption> = emptyList(), val datasetId: String = "", val makes: List<MotorcycleMake> = emptyList(), val models: List<MotorcycleModel> = emptyList(), val locations: List<StorageLocation> = emptyList(), val templates: List<ProductTemplate> = emptyList(), val corrections: List<StockCorrection> = emptyList(), val openings: Map<String, OpeningStock> = emptyMap(), val carts: List<Cart> = emptyList(), val sales: List<Sale> = emptyList(), val arrivals: List<StockArrival> = emptyList(), val barcodeAliases: List<BarcodeAlias> = emptyList(), val barcodeHistory: List<BarcodeAssignment> = emptyList(), val labelPreferences: LabelPreferences = LabelPreferences())
enum class ProductField(val label: String) { Title("Title"), Category("Category"), OpeningStock("Opening stock"), SellingPrice("Selling price"), Attribute("Category field"), DefinitionName("Field name"), DefinitionType("Field type"), Fitment("Fitment"), Location("Location"), Photo("Photo"), CorrectionQuantity("Correction quantity"), CorrectionReason("Reason"), LowStockThreshold("Low-stock threshold"), CartQuantity("Cart quantity"), CashReceived("Cash received"), ArrivalQuantity("Arrival quantity"), PurchaseCost("Purchase unit cost"), LabelCopies("Label copies") }
class ShopValidation(val field: ProductField, message: String, val definitionId: String? = null) : IllegalArgumentException(message)

class Shop private constructor(private val database: ShopDatabase, private val photos: ProductPhotos, private val backupDirectory: java.io.File, private val driveDirectory: java.io.File, private val context: Context, private val storage: ShopStorage? = null, private val generation: String? = null) : AutoCloseable {
    val driveBackup = DriveBackup(this, driveDirectory)
    private val dao = database.dao()
    val snapshots = kotlinx.coroutines.flow.merge(dao.revisions().map { Unit },
        storage?.changes?.map { Unit } ?: kotlinx.coroutines.flow.emptyFlow()).map { snapshot() }

    internal fun checkActive() { storage?.checkActive(generation) }
    internal fun restoreOrigin(): RestoreOrigin? = storage?.origin()
    private suspend fun <T> activeTransaction(block: suspend () -> T): T {
        suspend fun run(): T { checkActive(); return database.withTransaction { block() } }
        return storage?.exclusive { run() } ?: run()
    }

    /** Replacement is explicit; callers reopen after success (or a lost activation response). */
    suspend fun activateRestore(staged: ValidatedRestore, confirmed: Boolean,
        boundary: suspend (RestoreBoundary) -> Unit = {}): LocalCapture {
        require(confirmed) { "Confirm replacement. Local changes absent from this restore point will be replaced." }
        return driveBackup.exclusiveActivation {
            requireNotNull(storage) { "Cannot activate an isolated preview." }.activate(this, staged, generation, boundary)
        }
    }

    suspend fun validateLocalRestore(file: java.io.File): ValidatedRestore =
        RestoreValidation.prepare(context, file, java.io.File(driveDirectory, "restore"))

    suspend fun restoreRecoveryCopy(): LocalCapture? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { storage?.recoveryCopy() }

    /** The callback reports a pinned committed revision; ordinary writes may continue during staging. */
    suspend fun captureLocalBackup(directory: java.io.File = backupDirectory, onCaptured: suspend (Long) -> Unit = {}): LocalCapture {
        checkActive()
        return captureBackup(database, photos, directory, onCaptured)
    }

    /** Incomplete attempts are never candidates. They are retried under a new identity. */
    suspend fun latestLocalBackup(): LocalCapture? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        backupDirectory.listFiles().orEmpty().filter { it.extension == "zip" }.mapNotNull { file ->
            try { LocalBackup.inspect(file) } catch (_: Exception) { null }
        }.maxWithOrNull(compareBy<LocalCapture> { it.revision }.thenBy { it.capturedAt })
    }

    /** Compare the confirmed assignment revision inside the same transaction as audit and ownership. */
    suspend fun linkBarcode(code: String, target: BarcodeTarget, namespace: String = "external",
        expectedAssignmentRevision: Long? = null) = activeTransaction {
        requireExternalBarcode(namespace, code)
        when (target.kind) {
            BarcodeTargetKind.Product -> requireActiveProduct(target.id)
            BarcodeTargetKind.Template -> require(dao.templates().any { it.id == target.id && !it.retired }) { "Choose an active template." }
        }
        val previous = dao.barcodeAliases().find { it.namespace == namespace && it.code == code }
        if (previous?.toAlias()?.target == target) return@activeTransaction
        if (previous != null && expectedAssignmentRevision != previous.revision) throw BarcodeConflict(previous.toAlias())
        require(previous != null || expectedAssignmentRevision == null) { "Barcode ownership changed. Scan again." }
        val revision = Math.addExact(requireNotNull(dao.state()).revision, 1)
        dao.putBarcodeAlias(BarcodeAliasRow(namespace, code, target.kind.name, target.id, revision))
        dao.insertBarcodeAssignment(BarcodeAssignmentRow(revision, namespace, code, previous?.kind, previous?.targetId,
            target.kind.name, target.id, System.currentTimeMillis()))
        advanceRevision()
    }

    private suspend fun initialize() = activeTransaction {
        require(dao.state()?.normalizerVersion?.let { it == LookupNames.VERSION } != false) { "Unsupported lookup normalizer version" }
        if (dao.state() == null) {
            listOf("Brake pads", "Nuts", "Bolts", "Sealed oil bottles").forEachIndexed { index, name ->
                dao.insertCategory(CategoryRow(UUID.randomUUID().toString(), name, LookupNames.key(name), index))
            }
            seedAttributes()
            dao.putState(StateRow(datasetId = UUID.randomUUID().toString()))
        }
    }

    suspend fun snapshot(): ShopSnapshot = activeTransaction {
        val state = requireNotNull(dao.state())
        val photoRows = dao.productPhotos().associateBy { it.productId }
        val balances = dao.balances().associateBy { it.productId }
        val values = dao.values().groupBy { it.productId }
        val fitments = dao.fitments().groupBy { it.productId }
        val locations = dao.productLocations().groupBy { it.productId }
        val cartLines = dao.cartLines().groupBy { it.cartId }
        val saleLines = dao.saleLines().groupBy { it.saleId }
        ShopSnapshot(dao.categories().map { Category(it.id, it.name) }, dao.products().map {
            Product(it.id, it.sku, it.title, it.categoryId, it.description, it.priceRupees, requireNotNull(balances[it.id]).quantity, values[it.id].orEmpty().filter { it.historyId.isEmpty() }.associate { value -> value.definitionId to decodeValue(value) }, values[it.id].orEmpty().filter { it.historyId.isNotEmpty() }.map { value -> HistoricalAttribute(value.historyId, value.definitionId, decodeValue(value)) }, fitments[it.id].orEmpty().map { row -> row.modelId }.toSet(), locations[it.id].orEmpty().map { row -> row.locationId }.toSet(), photoRows[it.id]?.let { row -> PhotoReference(row.sha256, row.sizeBytes) }, it.lowStockThreshold, it.archived)
        }, state.revision, Appearance.valueOf(state.appearance),
            dao.attributes().map { CategoryAttribute(it.id, it.categoryId, it.name, AttributeType.valueOf(it.type), it.required, it.unit, it.productId, it.retired) },
            dao.options().map { AttributeOption(it.id, it.definitionId, it.name, it.retired) }, state.datasetId,
            dao.makes().map { MotorcycleMake(it.id, it.name, it.retired) },
            dao.models().map { MotorcycleModel(it.id, it.makeId, it.name, it.retired) },
            dao.locations().map { StorageLocation(it.id, it.name, it.retired) },
            dao.templates().map { ProductTemplate(it.id, decodeCatalog(it.catalog), it.retired) }, dao.corrections().map { it.toCorrection() }, dao.allOpenings().associate { it.productId to OpeningStock(it.quantity, it.recordedAt) },
            dao.carts().map { cart -> Cart(cart.id, cart.number, cart.customer, cart.editVersion, cartLines[cart.id].orEmpty().map { CartLine(it.productId, it.quantity, it.quotedPriceRupees) }) },
            dao.sales().map { it.toSale(saleLines[it.id].orEmpty()) }, dao.arrivals().map { it.toArrival() }, dao.barcodeAliases().map { it.toAlias() }, dao.barcodeAssignments().map { it.toAssignment() }, LabelPreferences(LabelMedia.valueOf(state.labelMedia), state.labelCopies))
    }

    /** Add a visibly labeled demo batch through the normal stock and checkout paths. */
    internal suspend fun addDummyData(): Unit = activeTransaction {
        val batch = "Demo ${UUID.randomUUID().toString().take(8)}"
        val category = createCategory("$batch parts")
        val make = createMake("$batch Motorcycles")
        val models = setOf(createModel(make, "Road 125"))
        val locations = setOf(createLocation("$batch shelf"))
        val description = "Dummy data for development."

        val pads = createProduct(
            "$batch brake pads", category, description, "18", "650",
            modelIds = models, locationIds = locations,
        )
        val cable = createProduct(
            "$batch clutch cable", category, description, "3", "180",
            modelIds = models, locationIds = locations,
        )
        createProduct("$batch spark plug", category, description, "0", "120")
        val archived = createProduct("$batch discontinued lever", category, description, "0", "90")
        archiveProduct(archived)
        setLowStockThreshold(cable, "5")

        saveTemplate(CatalogDraft("$batch brake pad template", category, description, modelIds = models))

        val cart = createCart()
        saveCart(cart, 0, "$batch pending customer", mapOf(cable to "1"))

        val completedCart = createCart()
        val version = saveCart(completedCart, 0, "$batch cash customer", mapOf(pads to "2"))
        checkout(completedCart, version, "1500")
    }

    suspend fun createProduct(title: String, categoryId: String, description: String, openingPieces: String, priceRupees: String, allowSimilar: Boolean = false, attributes: Map<String, AttributeValue> = emptyMap(), modelIds: Set<String> = emptySet(), locationIds: Set<String> = emptySet(), photo: PhotoChange = PhotoChange.Keep): String {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        return activeTransaction {
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
        activeTransaction {
            validateProduct(title, categoryId)
            val product = requireActiveProduct(id)
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

    /** Recheck live stock and cart references in the same transaction as the archive write. */
    suspend fun archiveProduct(id: String) = activeTransaction {
        val product = dao.products().single { it.id == id }
        if (product.archived) return@activeTransaction
        require(dao.balances().single { it.productId == id }.quantity == 0L) {
            "Cannot archive while stock remains. Stock must be zero."
        }
        require(dao.cartLines().none { it.productId == id }) {
            "Cannot archive while an unfinished cart contains this product. Remove its line or discard the cart first."
        }
        dao.updateProduct(product.copy(archived = true))
        advanceRevision()
    }

    suspend fun setLowStockThreshold(id: String, pieces: String) = activeTransaction {
        val threshold = pieces.takeIf { it.isNotBlank() }?.let { parseWhole(it, ProductField.LowStockThreshold) }
        val product = requireActiveProduct(id)
        if (product.lowStockThreshold != threshold) {
            dao.updateProduct(product.copy(lowStockThreshold = threshold))
            advanceRevision()
        }
    }

    suspend fun saveTemplate(catalog: CatalogDraft, id: String? = null, photo: PhotoChange = PhotoChange.Keep): String {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        return activeTransaction {
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

    suspend fun retireTemplate(id: String) = activeTransaction {
        val row = dao.templates().single { it.id == id }
        if (!row.retired) { dao.updateTemplate(row.copy(retired = true)); advanceRevision() }
    }

    suspend fun createFromCatalog(catalog: CatalogDraft, openingPieces: String, priceRupees: String,
        allowSimilar: Boolean = false, locationIds: Set<String> = emptySet(), photo: PhotoChange = PhotoChange.Keep): String {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        return activeTransaction {
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
        draft.requireDistinctCustomIdentities()
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
        require(dao.attributes().none { existing -> definitions.any { it.id == existing.id } }) { "Custom fields must be independently owned" }
        validateAttributes(draft.categoryId, draft.attributes, "catalog-draft", previous?.attributes.orEmpty(), allowIncomplete, definitions, options)
        val snapshot = snapshot()
        if (draft.modelIds.any { id -> snapshot.models.none { it.id == id && ((!it.retired && snapshot.makes.any { make -> make.id == it.makeId && !make.retired }) || id in previous?.modelIds.orEmpty()) } })
            throw ShopValidation(ProductField.Fitment, "Choose active motorcycle models")
        draft.photo?.let { reference ->
            require(reference == previous?.photo || dao.productPhotos().any { it.sha256 == reference.sha256 && it.sizeBytes == reference.sizeBytes } || dao.templates().any { decodeCatalog(it.catalog).photo == reference } || photos.read(reference) != null) { "Photo unavailable. Choose it again or remove it." }
        }
    }

    suspend fun createMake(name: String): String = activeTransaction {
        val display = lookupDisplay(name)
        dao.makes().find { it.normalizedName == LookupNames.key(display) }?.also {
            require(!it.retired) { "That make is retired; choose another name" }
        }?.id ?: UUID.randomUUID().toString().also {
            dao.insertMake(MakeRow(it, display, LookupNames.key(display))); advanceRevision()
        }
    }

    suspend fun createModel(makeId: String, name: String): String = activeTransaction {
        require(dao.makes().any { it.id == makeId && !it.retired }) { "Choose an active make" }
        val display = lookupDisplay(name)
        dao.models().find { it.makeId == makeId && it.normalizedName == LookupNames.key(display) }?.also {
            require(!it.retired) { "That model is retired; choose another name" }
        }?.id ?: UUID.randomUUID().toString().also {
            dao.insertModel(ModelRow(it, makeId, display, LookupNames.key(display))); advanceRevision()
        }
    }

    suspend fun createLocation(name: String): String = activeTransaction {
        val display = lookupDisplay(name)
        dao.locations().find { it.normalizedName == LookupNames.key(display) }?.also {
            require(!it.retired) { "That location is retired; choose another name" }
        }?.id ?: UUID.randomUUID().toString().also {
            dao.insertLocation(LocationRow(it, display, LookupNames.key(display))); advanceRevision()
        }
    }

    suspend fun retireMake(id: String) = activeTransaction {
        val row = dao.makes().single { it.id == id }
        if (!row.retired) { dao.updateMake(row.copy(retired = true)); advanceRevision() }
    }

    suspend fun retireModel(id: String) = activeTransaction {
        val row = dao.models().single { it.id == id }
        if (!row.retired) { dao.updateModel(row.copy(retired = true)); advanceRevision() }
    }

    suspend fun retireLocation(id: String) = activeTransaction {
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

    suspend fun renameCategory(id: String, name: String) = activeTransaction {
        require(LookupNames.display(name).isNotEmpty()) { "Enter a category name" }
        val key = LookupNames.key(name)
        require(dao.categories().none { it.id != id && LookupNames.key(it.name) == key }) { "That category already exists" }
        dao.updateCategory(dao.categories().single { it.id == id }.copy(name = LookupNames.display(name), normalizedName = key))
        advanceRevision()
    }

    suspend fun createCategory(name: String): String = activeTransaction {
        val display = LookupNames.display(name)
        require(display.isNotEmpty()) { "Enter a category name" }
        dao.categories().find { LookupNames.key(it.name) == LookupNames.key(display) }?.id ?: run {
            val id = UUID.randomUUID().toString()
            dao.insertCategory(CategoryRow(id, display, LookupNames.key(display), dao.categories().size))
            advanceRevision()
            id
        }
    }

    suspend fun createAttribute(categoryId: String, name: String, type: AttributeType, required: Boolean = false, unit: String = ""): String = activeTransaction {
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

    suspend fun retireAttribute(id: String) = activeTransaction {
        val field = dao.attributes().single { it.id == id }
        if (!field.retired) { dao.updateAttribute(field.copy(retired = true)); advanceRevision() }
    }

    suspend fun retireOption(id: String) = activeTransaction {
        val option = dao.options().single { it.id == id }
        if (!option.retired) { dao.updateOption(option.copy(retired = true)); advanceRevision() }
    }

    suspend fun setAttributeRequired(id: String, required: Boolean) = activeTransaction {
        val field = dao.attributes().single { it.id == id }
        require(!field.retired) { "That field is retired" }
        if (field.required != required) { dao.updateAttribute(field.copy(required = required)); advanceRevision() }
    }

    suspend fun replaceAttribute(id: String, name: String, type: AttributeType, required: Boolean = false, unit: String = ""): String = activeTransaction {
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

    suspend fun createCustomAttribute(productId: String, name: String, type: AttributeType, required: Boolean = false, unit: String = ""): String = activeTransaction {
        val product = requireActiveProduct(productId)
        createDefinition(product.categoryId, productId, name, type, required, unit)
    }

    suspend fun promoteCustomAttribute(definitionId: String, name: String) = activeTransaction {
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

    suspend fun createOption(definitionId: String, name: String): String = activeTransaction {
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

    suspend fun setLabelPreferences(media: LabelMedia, copies: String) = activeTransaction {
        val count = parseLabelCopies(copies)
        val state = requireNotNull(dao.state())
        if (state.labelMedia != media.name || state.labelCopies != count) {
            dao.putState(state.copy(labelMedia = media.name, labelCopies = count, revision = Math.addExact(state.revision, 1)))
        }
    }

    suspend fun setAppearance(appearance: Appearance) = activeTransaction {
        val state = requireNotNull(dao.state())
        if (state.appearance != appearance.name) dao.putState(state.copy(appearance = appearance.name, revision = Math.addExact(state.revision, 1)))
    }

    /** Retry with the same operation identity and inputs; reuse for a different command is rejected. */
    suspend fun correctStock(operationId: String, productId: String, kind: CorrectionKind, quantity: String, reason: String) = activeTransaction {
        require(operationId.isNotBlank()) { "A correction operation identity is required" }
        val amount = parseWhole(quantity, ProductField.CorrectionQuantity)
        if (kind != CorrectionKind.PhysicalCount && amount == 0L) throw ShopValidation(ProductField.CorrectionQuantity, "Enter a positive whole quantity to remove")
        val explanation = reason.trim()
        if (explanation.isEmpty()) throw ShopValidation(ProductField.CorrectionReason, "Explain why this stock correction is needed")
        val previous = dao.correction(operationId)
        if (previous != null) {
            require(previous.productId == productId && previous.kind == kind.name && previous.quantity == amount && previous.reason == explanation) { "This operation identity already belongs to a different correction" }
            return@activeTransaction
        }
        requireActiveProduct(productId)
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

    suspend fun receiveStock(operationId: String, productId: String, pieces: String, unitCostRupees: String) = activeTransaction {
        require(operationId.isNotBlank()) { "An arrival operation identity is required" }
        val quantity = parseWhole(pieces, ProductField.ArrivalQuantity)
        if (quantity == 0L) throw ShopValidation(ProductField.ArrivalQuantity, "Enter a positive whole-piece quantity")
        val cost = parseWhole(unitCostRupees, ProductField.PurchaseCost)
        val previous = dao.arrival(operationId)
        if (previous != null) {
            require(previous.productId == productId && previous.quantity == quantity && previous.unitCostRupees == cost) { "This operation identity already belongs to a different arrival" }
            return@activeTransaction
        }
        requireActiveProduct(productId)
        val balance = dao.balances().single { it.productId == productId }
        val result = try {
            Math.multiplyExact(quantity, cost)
            Math.addExact(balance.quantity, quantity)
        } catch (_: ArithmeticException) { throw ShopValidation(ProductField.ArrivalQuantity, "Arrival exceeds the supported quantity or cost limit") }
        dao.insertArrival(ArrivalRow(operationId, productId, quantity, cost, balance.quantity, result, System.currentTimeMillis(), Math.addExact(requireNotNull(dao.state()).revision, 1)))
        dao.updateBalance(balance.copy(quantity = result))
        advanceRevision()
    }

    suspend fun receipt(cartId: String): Sale? = activeTransaction {
        dao.saleForCart(cartId)?.let { sale -> sale.toSale(dao.saleLines().filter { it.saleId == sale.id }) }
    }

    /** The cart is the retry identity. A committed sale wins even over stale/invalid retry inputs. */
    suspend fun checkout(cartId: String, expectedVersion: Long, cashReceived: String): Sale = activeTransaction {
        receipt(cartId)?.let { return@activeTransaction it }
        val cart = requireCurrentCart(cartId, expectedVersion)
        val lines = dao.cartLines().filter { it.cartId == cartId }
        require(lines.isNotEmpty()) { "Add products before checkout" }
        val total = lines.fold(0L) { sum, line -> Math.addExact(sum, Math.multiplyExact(line.quantity, line.quotedPriceRupees)) }
        val cash = parseWhole(cashReceived, ProductField.CashReceived)
        if (cash < total) throw ShopValidation(ProductField.CashReceived, "Cash received must cover the whole cart total")
        val products = dao.products().associateBy { it.id }
        val balances = dao.balances().associateBy { it.productId }
        val shortages = lines.mapNotNull { line ->
            val balance = balances.getValue(line.productId).quantity
            if (line.quantity > balance) StockShortage(line.productId, products.getValue(line.productId).title, line.quantity, balance) else null
        }
        if (shortages.isNotEmpty()) throw CheckoutShortage(shortages)
        val categories = dao.categories().associateBy { it.id }
        val makes = dao.makes().associateBy { it.id }
        val models = dao.models().associateBy { it.id }
        val fitments = dao.fitments().groupBy { it.productId }
        val sale = SaleRow(UUID.randomUUID().toString(), cartId, cart.number, cart.customer, System.currentTimeMillis(),
            Math.addExact(requireNotNull(dao.state()).revision, 1), total, cash, Math.subtractExact(cash, total))
        val snapshots = lines.map { line ->
            val product = products.getValue(line.productId)
            val before = balances.getValue(product.id).quantity
            val fitment = fitments[product.id].orEmpty().map { models.getValue(it.modelId) }
                .map { "${makes.getValue(it.makeId).name} · ${it.name}" }.sorted().joinToString("; ").ifEmpty { "Unknown fitment" }
            SaleLineRow(sale.id, product.id, product.sku, product.title, categories.getValue(product.categoryId).name,
                product.description, fitment, line.quantity, line.quotedPriceRupees, before,
                Math.subtractExact(before, line.quantity), Math.negateExact(line.quantity))
        }
        dao.insertSale(sale)
        dao.insertSaleLines(snapshots)
        snapshots.forEach { dao.updateBalance(BalanceRow(it.productId, it.resultingBalance)) }
        // Removing the open cart is its completion; the unique sale retains its identity forever.
        dao.deleteCart(cartId)
        advanceRevision()
        sale.toSale(snapshots)
    }

    suspend fun createCart(): String = activeTransaction {
        val number = Math.addExact(dao.cartSequence()?.lastNumber ?: 0L, 1L)
        val id = UUID.randomUUID().toString()
        dao.putCartSequence(CartSequenceRow(lastNumber = number))
        dao.insertCart(CartRow(id, number, "", 0))
        advanceRevision()
        id
    }

    /** Compare versions within the transaction; only new lines acquire current prices. */
    suspend fun saveCart(id: String, expectedVersion: Long, customer: String, quantities: Map<String, String>): Long = activeTransaction {
        val cart = requireCurrentCart(id, expectedVersion)
        val old = dao.cartLines().filter { it.cartId == id }.associateBy { it.productId }
        val products = dao.products().associateBy { it.id }
        val lines = quantities.map { (productId, input) ->
            val product = requireNotNull(products[productId]) { "Choose an existing stocked product" }
            require(!product.archived) { "This product is archived. Choose an active product." }
            val quantity = parseCartQuantity(input, productId)
            CartLineRow(id, productId, quantity, old[productId]?.quotedPriceRupees ?: product.priceRupees)
        }
        try {
            lines.fold(0L) { total, line -> Math.addExact(total, Math.multiplyExact(line.quantity, line.quotedPriceRupees)) }
        } catch (_: ArithmeticException) {
            throw ShopValidation(ProductField.CartQuantity, "Cart total exceeds the supported whole-rupee limit")
        }
        val label = customer.trim()
        val changed = cart.customer != label || old.values.toSet() != lines.toSet()
        val version = if (changed) Math.addExact(cart.editVersion, 1) else cart.editVersion
        if (changed) {
            dao.deleteCartLines(id)
            dao.insertCartLines(lines)
            dao.updateCart(cart.copy(customer = label, editVersion = version))
            advanceRevision()
        }
        version
    }

    suspend fun addCartProduct(id: String, expectedVersion: Long, productId: String, pieces: String) = activeTransaction {
        val cart = requireCurrentCart(id, expectedVersion)
        val amount = parseCartQuantity(pieces, productId)
        val lines = dao.cartLines().filter { it.cartId == id }
        val quantities = lines.associate { it.productId to it.quantity.toString() }.toMutableMap()
        val quantity = try { Math.addExact(lines.find { it.productId == productId }?.quantity ?: 0L, amount) }
            catch (_: ArithmeticException) { throw ShopValidation(ProductField.CartQuantity, "Cart quantity exceeds the supported whole-number limit", productId) }
        quantities[productId] = quantity.toString()
        saveCart(id, expectedVersion, cart.customer, quantities)
    }

    suspend fun discardCart(id: String, expectedVersion: Long) = activeTransaction {
        requireCurrentCart(id, expectedVersion)
        dao.deleteCart(id)
        advanceRevision()
    }

    private suspend fun requireCurrentCart(id: String, version: Long): CartRow {
        val cart = dao.cart(id) ?: throw StaleCart()
        if (cart.editVersion != version) throw StaleCart()
        return cart
    }

    suspend fun setSellingPrice(id: String, rupees: String) = activeTransaction {
        val price = parseWhole(rupees, ProductField.SellingPrice)
        val product = requireActiveProduct(id)
        if (product.priceRupees != price) {
            val lines = dao.cartLines()
            val affected = lines.filter { it.productId == id }.groupBy { it.cartId }
            try {
                affected.forEach { (cartId, _) ->
                    lines.asSequence().filter { it.cartId == cartId }.fold(0L) { total, line ->
                        val linePrice = if (line.productId == id) price else line.quotedPriceRupees
                        Math.addExact(total, Math.multiplyExact(line.quantity, linePrice))
                    }
                }
            } catch (_: ArithmeticException) {
                throw ShopValidation(ProductField.SellingPrice, "Selling price makes an unfinished cart total exceed the supported whole-rupee limit")
            }
            dao.updateProduct(product.copy(priceRupees = price))
            if (affected.isNotEmpty()) {
                dao.repriceCartLines(id, price)
                val carts = dao.carts().associateBy { it.id }
                affected.keys.forEach { cartId ->
                    val cart = requireNotNull(carts[cartId])
                    dao.updateCart(cart.copy(editVersion = Math.addExact(cart.editVersion, 1)))
                }
            }
            advanceRevision()
        }
    }

    suspend fun correctionHistory(productId: String): List<StockCorrection> = dao.corrections().filter { it.productId == productId }.map { it.toCorrection() }

    suspend fun openingHistory(id: String): List<OpeningStock> = dao.openings(id).map { OpeningStock(it.quantity, it.recordedAt) }

    private suspend fun requireActiveProduct(id: String): ProductRow {
        val product = requireNotNull(dao.products().find { it.id == id }) { "Choose an existing stocked product" }
        require(!product.archived) { "This product is archived. Choose an active product." }
        return product
    }

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
            val storage = ShopStorage(context.applicationContext, name)
            return storage.exclusive {
                val generation = storage.active
                val database = openDatabase(context, storage.database(generation))
                Shop(database, ProductPhotos(storage.photos(generation)), storage.backups(generation), storage.drive(generation),
                    context.applicationContext, storage, generation).also {
                    try { it.initialize() } catch (error: Throwable) { database.close(); throw error }
                }
            }
        }
        internal fun openDatabase(context: Context, name: String): ShopDatabase =
            Room.databaseBuilder(context.applicationContext, ShopDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14).build()

        internal fun staged(context: Context, database: ShopDatabase, directory: java.io.File) =
            Shop(database, ProductPhotos(java.io.File(directory, "blobs")), directory, directory, context)
    }
}

class SimilarProduct : IllegalArgumentException("A similar product already exists. Open it from Stock or save a distinct product.")

private fun parseCartQuantity(value: String, productId: String): Long {
    val quantity = try { parseWhole(value, ProductField.CartQuantity) } catch (_: ShopValidation) { null }
    if (quantity == null || quantity == 0L) throw ShopValidation(ProductField.CartQuantity, "Cart quantity must be a positive whole number within the supported limit", productId)
    return quantity
}

internal fun parseWhole(value: String, field: ProductField): Long {
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
