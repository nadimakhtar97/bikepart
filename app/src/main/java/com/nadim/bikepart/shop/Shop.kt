package com.nadim.bikepart.shop

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.nadim.bikepart.R
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.map
import org.json.JSONObject

data class Category(val id: String, val name: String)
data class Product(val id: String, val sku: String, val title: String, val categoryId: String, val description: String, val priceRupees: Long, val quantity: Long, val attributes: Map<String, AttributeValue> = emptyMap(), val historicalAttributes: List<HistoricalAttribute> = emptyList(), val modelIds: Set<String> = emptySet(), val locationIds: Set<String> = emptySet(), val photo: PhotoReference? = null, val lowStockThreshold: Long? = null, val archived: Boolean = false, val additionalPhotos: List<PhotoReference> = emptyList(), val buyingPriceRupees: Long = 0)
data class HistoricalAttribute(val id: String, val definitionId: String, val value: AttributeValue)

data class OpeningStock(val quantity: Long, val recordedAt: Long, val buyingPriceRupees: Long = 0)
enum class Appearance(@param:androidx.annotation.StringRes val label: Int) {
    System(R.string.system), Light(R.string.light), Dark(R.string.dark)
}
data class ShopSnapshot(val categories: List<Category>, val products: List<Product>, val revision: Long, val appearance: Appearance, val attributes: List<CategoryAttribute> = emptyList(), val options: List<AttributeOption> = emptyList(), val datasetId: String = "", val makes: List<MotorcycleMake> = emptyList(), val models: List<MotorcycleModel> = emptyList(), val locations: List<StorageLocation> = emptyList(), val templates: List<ProductTemplate> = emptyList(), val corrections: List<StockCorrection> = emptyList(), val openings: Map<String, OpeningStock> = emptyMap(), val carts: List<Cart> = emptyList(), val sales: List<Sale> = emptyList(), val arrivals: List<StockArrival> = emptyList(), val barcodeAliases: List<BarcodeAlias> = emptyList(), val barcodeHistory: List<BarcodeAssignment> = emptyList(), val labelPreferences: LabelPreferences = LabelPreferences())
enum class ProductField(@param:androidx.annotation.StringRes val label: Int) { Title(R.string.title), Category(R.string.category_2), OpeningStock(R.string.opening_stock), SellingPrice(R.string.selling_price_2), Attribute(R.string.category_field), DefinitionName(R.string.field_name_label), DefinitionType(R.string.field_type), Fitment(R.string.finding_fitment_only), Location(R.string.location_2), Photo(R.string.photo), CorrectionQuantity(R.string.correction_quantity), CorrectionReason(R.string.correction_reason), LowStockThreshold(R.string.threshold_field_label), CartQuantity(R.string.cart_quantity), CashReceived(R.string.cash_received_3), ArrivalQuantity(R.string.arrival_quantity), PurchaseCost(R.string.buying_price), LabelCopies(R.string.label_copies_label) }
class ShopValidation(val field: ProductField, message: String, val definitionId: String? = null) : IllegalArgumentException(message)

class Shop private constructor(private val database: ShopDatabase, private val photos: ProductPhotos, private val backupDirectory: java.io.File, private val driveDirectory: java.io.File, private val context: Context, private val storage: ShopStorage? = null, private val generation: String? = null) : AutoCloseable {
    val driveBackup = DriveBackup(this, driveDirectory, context)
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
        require(confirmed) { context.getString(R.string.confirm_replacement_local_changes_absent_from) }
        return driveBackup.exclusiveActivation {
            requireNotNull(storage) { context.getString(R.string.cannot_activate_an_isolated_preview) }.activate(this, staged, generation, boundary)
        }
    }

    suspend fun validateLocalRestore(file: java.io.File): ValidatedRestore =
        RestoreValidation.prepare(context, file, java.io.File(driveDirectory, "restore"))

    suspend fun restoreRecoveryCopy(): LocalCapture? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { storage?.recoveryCopy() }

    /** The callback reports a pinned committed revision; ordinary writes may continue during staging. */
    suspend fun captureLocalBackup(directory: java.io.File = backupDirectory, onCaptured: suspend (Long) -> Unit = {}): LocalCapture {
        checkActive()
        return captureBackup(context, database, photos, directory, onCaptured)
    }

    /** Incomplete attempts are never candidates. They are retried under a new identity. */
    suspend fun latestLocalBackup(): LocalCapture? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        backupDirectory.listFiles().orEmpty().filter { it.extension == "zip" }.mapNotNull { file ->
            try { LocalBackup.inspect(context, file) } catch (_: Exception) { null }
        }.maxWithOrNull(compareBy<LocalCapture> { it.revision }.thenBy { it.capturedAt })
    }

    /** Compare the confirmed assignment revision inside the same transaction as audit and ownership. */
    suspend fun linkBarcode(code: String, target: BarcodeTarget, namespace: String = "external",
        expectedAssignmentRevision: Long? = null) = activeTransaction {
        requireExternalBarcode(context, namespace, code)
        when (target.kind) {
            BarcodeTargetKind.Product -> requireActiveProduct(target.id)
            BarcodeTargetKind.Template -> require(dao.templates().any { it.id == target.id && !it.retired }) { context.getString(R.string.choose_an_active_template) }
        }
        val previous = dao.barcodeAliases().find { it.namespace == namespace && it.code == code }
        if (previous?.toAlias()?.target == target) return@activeTransaction
        if (previous != null && expectedAssignmentRevision != previous.revision) throw BarcodeConflict(previous.toAlias(), context.getString(R.string.this_barcode_already_belongs_to_another))
        require(previous != null || expectedAssignmentRevision == null) { context.getString(R.string.barcode_ownership_changed_scan_again) }
        val revision = Math.addExact(requireNotNull(dao.state()).revision, 1)
        dao.putBarcodeAlias(BarcodeAliasRow(namespace, code, target.kind.name, target.id, revision))
        dao.insertBarcodeAssignment(BarcodeAssignmentRow(revision, namespace, code, previous?.kind, previous?.targetId,
            target.kind.name, target.id, System.currentTimeMillis()))
        advanceRevision()
    }

    private suspend fun initialize() = activeTransaction {
        require(dao.state()?.normalizerVersion?.let { it == LookupNames.VERSION } != false) { context.getString(R.string.unsupported_lookup_normalizer_version) }
        if (dao.state() == null) {
            listOf(context.getString(R.string.brake_pads), context.getString(R.string.nuts), context.getString(R.string.bolts), context.getString(R.string.sealed_oil_bottles)).forEachIndexed { index, name ->
                dao.insertCategory(CategoryRow(UUID.randomUUID().toString(), name, LookupNames.key(name), index))
            }
            seedAttributes()
            dao.putState(StateRow(datasetId = UUID.randomUUID().toString()))
        }
        if (reduceOverstockedCarts(dao.balances().associate { it.productId to it.quantity })) advanceRevision()
    }

    suspend fun snapshot(): ShopSnapshot = activeTransaction {
        val state = requireNotNull(dao.state())
        val photoRows = dao.productPhotos().groupBy { it.productId }
        val balances = dao.balances().associateBy { it.productId }
        val values = dao.values().groupBy { it.productId }
        val fitments = dao.fitments().groupBy { it.productId }
        val locations = dao.productLocations().groupBy { it.productId }
        val cartLines = dao.cartLines().groupBy { it.cartId }
        val saleLines = dao.saleLines().groupBy { it.saleId }
        ShopSnapshot(dao.categories().map { Category(it.id, it.name) }, dao.products().map {
            val productPhotos = photoRows[it.id].orEmpty().map { row -> PhotoReference(row.sha256, row.sizeBytes) }
            Product(it.id, it.sku, it.title, it.categoryId, it.description, it.priceRupees, requireNotNull(balances[it.id]).quantity, values[it.id].orEmpty().filter { it.historyId.isEmpty() }.associate { value -> value.definitionId to decodeValue(value) }, values[it.id].orEmpty().filter { it.historyId.isNotEmpty() }.map { value -> HistoricalAttribute(value.historyId, value.definitionId, decodeValue(value)) }, fitments[it.id].orEmpty().map { row -> row.modelId }.toSet(), locations[it.id].orEmpty().map { row -> row.locationId }.toSet(), productPhotos.firstOrNull(), it.lowStockThreshold, it.archived, productPhotos.drop(1), it.buyingPriceRupees)
        }, state.revision, Appearance.valueOf(state.appearance),
            dao.attributes().map { CategoryAttribute(it.id, it.categoryId, it.name, AttributeType.valueOf(it.type), it.required, it.unit, it.productId, it.retired) },
            dao.options().map { AttributeOption(it.id, it.definitionId, it.name, it.retired) }, state.datasetId,
            dao.makes().map { MotorcycleMake(it.id, it.name, it.retired) },
            dao.models().map { MotorcycleModel(it.id, it.makeId, it.name, it.retired) },
            dao.locations().map { StorageLocation(it.id, it.name, it.retired) },
            dao.templates().map { ProductTemplate(it.id, decodeCatalog(it.catalog), it.retired) }, dao.corrections().map { it.toCorrection() }, dao.allOpenings().associate { it.productId to OpeningStock(it.quantity, it.recordedAt, it.buyingPriceRupees) },
            dao.carts().map { cart -> Cart(cart.id, cart.number, cart.customer, cart.editVersion, cartLines[cart.id].orEmpty().map { CartLine(it.productId, it.quantity, it.quotedPriceRupees) },
                cart.readStockReductions()) },
            dao.sales().map { it.toSale(saleLines[it.id].orEmpty()) }, dao.arrivals().map { it.toArrival() }, dao.barcodeAliases().map { it.toAlias() }, dao.barcodeAssignments().map { it.toAssignment() }, LabelPreferences(LabelMedia.valueOf(state.labelMedia), state.labelCopies))
    }

    /** Add a visibly labeled demo batch through the normal stock and checkout paths. */
    internal suspend fun addDummyData(): Unit = activeTransaction {
        val batch = context.getString(R.string.demo, UUID.randomUUID().toString().take(8))
        val category = createCategory(context.getString(R.string.parts, batch))
        val make = createMake(context.getString(R.string.motorcycles, batch))
        val models = setOf(createModel(make, context.getString(R.string.road_125)))
        val locations = setOf(createLocation(context.getString(R.string.shelf, batch)))
        val description = context.getString(R.string.dummy_data_for_development)

        val pads = createProduct(
            context.getString(R.string.brake_pads_2, batch), category, description, "18", "650",
            modelIds = models, locationIds = locations,
        )
        val cable = createProduct(
            context.getString(R.string.clutch_cable, batch), category, description, "3", "180",
            modelIds = models, locationIds = locations,
        )
        createProduct(context.getString(R.string.spark_plug, batch), category, description, "0", "120")
        val archived = createProduct(context.getString(R.string.discontinued_lever, batch), category, description, "0", "90")
        archiveProduct(archived)
        setLowStockThreshold(cable, "5")

        saveTemplate(CatalogDraft(context.getString(R.string.brake_pad_template, batch), category, description, modelIds = models))

        val cart = createCart()
        saveCart(cart, 0, context.getString(R.string.pending_customer, batch), mapOf(cable to "1"))

        val completedCart = createCart()
        val version = saveCart(completedCart, 0, context.getString(R.string.cash_customer, batch), mapOf(pads to "2"))
        checkout(completedCart, version, "1500")
    }

    suspend fun createProduct(title: String, categoryId: String, description: String, openingPieces: String, priceRupees: String, allowSimilar: Boolean = false, attributes: Map<String, AttributeValue> = emptyMap(), modelIds: Set<String> = emptySet(), locationIds: Set<String> = emptySet(), photo: PhotoChange = PhotoChange.Keep, buyingPriceRupees: String = "0"): String {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        return activeTransaction {
            validateProduct(title, categoryId)
            val values = validateAttributes(categoryId, attributes)
            val quantity = parseWhole(context, openingPieces, ProductField.OpeningStock)
            val price = parseWhole(context, priceRupees, ProductField.SellingPrice)
            val buyingPrice = parseWhole(context, buyingPriceRupees, ProductField.PurchaseCost)
            if (!allowSimilar && dao.products().any { LookupNames.key(it.title) == LookupNames.key(title) && it.categoryId == categoryId }) throw SimilarProduct(context.getString(R.string.a_similar_product_already_exists_open))
            val id = UUID.randomUUID().toString()
            dao.insertProduct(ProductRow(id, "BP-${id.uppercase(Locale.ROOT)}", title.trim(), categoryId, description.trim(), price, buyingPriceRupees = buyingPrice))
            dao.insertValues(values.map { it.copy(productId = id) })
            saveFindingDetails(id, modelIds, locationIds)
            if (imported != null) dao.putPhoto(ProductPhotoRow(id, imported.sha256, imported.sizeBytes))
            dao.insertBalance(BalanceRow(id, quantity))
            if (quantity > 0) dao.insertOpening(OpeningRow(id, quantity, System.currentTimeMillis(), buyingPrice))
            advanceRevision()
            id
        }
    }

    suspend fun editProduct(id: String, title: String, categoryId: String, description: String, attributes: Map<String, AttributeValue>? = null, categoryMappings: Map<String, String> = emptyMap(), modelIds: Set<String>? = null, locationIds: Set<String>? = null, photo: PhotoChange = PhotoChange.Keep, buyingPriceRupees: String? = null, sellingPriceRupees: String? = null) {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        activeTransaction {
            validateProduct(title, categoryId)
            val product = requireActiveProduct(id)
            val buyingPrice = buyingPriceRupees?.let { parseWhole(context, it, ProductField.PurchaseCost) } ?: product.buyingPriceRupees
            val sellingPrice = sellingPriceRupees?.let { parseWhole(context, it, ProductField.SellingPrice) } ?: product.priceRupees
            val previous = dao.values().filter { it.productId == id }
            val existing = previous.filter { it.historyId.isEmpty() }.associate { it.definitionId to decodeValue(it) }
            val definitions = dao.attributes()
            val applicable = definitions.filter { it.productId == id || it.productId == null && it.categoryId == categoryId }.map { it.id }.toSet()
            val historical = previous.filter { it.historyId.isNotEmpty() || it.definitionId !in applicable }.map { if (it.historyId.isNotEmpty()) it else it.copy(historyId = UUID.randomUUID().toString()) }
            val input = (attributes ?: existing.filterKeys { it in applicable }).toMutableMap()
            require(categoryMappings.isEmpty() || product.categoryId != categoryId) { context.getString(R.string.mapping_requires_a_category_change) }
            require(categoryMappings.values.toSet().size == categoryMappings.size) { context.getString(R.string.map_each_target_only_once) }
            for ((sourceId, targetId) in categoryMappings) {
                val source = definitions.single { it.id == sourceId }
                val target = definitions.single { it.id == targetId }
                require(source.productId == null && source.categoryId == product.categoryId && sourceId in existing) { context.getString(R.string.choose_a_previous_category_value) }
                require(target.productId == null && target.categoryId == categoryId && !target.retired && source.type == target.type && source.unit == target.unit && !AttributeType.valueOf(source.type).isChoice) { context.getString(R.string.map_fields_with_the_same_text) }
                val mapped = existing.getValue(sourceId)
                require(targetId !in input || input[targetId] == mapped) { context.getString(R.string.mapping_conflicts_with_the_entered_target) }
                input[targetId] = mapped
            }
            val values = validateAttributes(categoryId, input, id, existing)
            saveFindingDetails(id, modelIds ?: dao.fitments().filter { it.productId == id }.map { it.modelId }.toSet(), locationIds ?: dao.productLocations().filter { it.productId == id }.map { it.locationId }.toSet())
            dao.deleteValues(id)
            dao.insertValues(historical + values.map { it.copy(productId = id) })
            when (photo) {
                PhotoChange.Keep -> Unit
                PhotoChange.Remove -> dao.deletePhoto(id)
                is PhotoChange.Import -> {
                    dao.deletePhoto(id)
                    dao.putPhoto(ProductPhotoRow(id, requireNotNull(imported).sha256, imported.sizeBytes))
                }
            }
            repriceUnfinishedCarts(product, sellingPrice)
            dao.updateProduct(product.copy(title = title.trim(), categoryId = categoryId, description = description.trim(),
                priceRupees = sellingPrice, buyingPriceRupees = buyingPrice))
            advanceRevision()
        }
    }
    suspend fun readPhoto(reference: PhotoReference): ByteArray? = photos.read(reference)

    /** Recheck live stock and cart references in the same transaction as the archive write. */
    suspend fun archiveProduct(id: String) = activeTransaction {
        val product = dao.products().single { it.id == id }
        if (product.archived) return@activeTransaction
        require(dao.balances().single { it.productId == id }.quantity == 0L) {
            context.getString(R.string.cannot_archive_while_stock_remains_stock)
        }
        require(dao.cartLines().none { it.productId == id }) {
            context.getString(R.string.cannot_archive_while_an_unfinished_cart)
        }
        dao.updateProduct(product.copy(archived = true))
        advanceRevision()
    }

    suspend fun setLowStockThreshold(id: String, pieces: String) = activeTransaction {
        val threshold = pieces.takeIf { it.isNotBlank() }?.let { parseWhole(context, it, ProductField.LowStockThreshold) }
        val product = requireActiveProduct(id)
        if (product.lowStockThreshold != threshold) {
            dao.updateProduct(product.copy(lowStockThreshold = threshold))
            advanceRevision()
        }
    }

    suspend fun saveTemplate(catalog: CatalogDraft, id: String? = null, photo: PhotoChange = PhotoChange.Keep): String {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        return activeTransaction {
            val previous = id?.let { key -> dao.templates().single { it.id == key }.also { require(!it.retired) { context.getString(R.string.that_template_is_retired) } } }
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
        allowSimilar: Boolean = false, locationIds: Set<String> = emptySet(), photo: PhotoChange = PhotoChange.Keep,
        additionalPhotos: List<PhotoChange.Import> = emptyList(), buyingPriceRupees: String = "0"): String {
        val imported = (photo as? PhotoChange.Import)?.let { photos.import(it) }
        val importedAdditional = additionalPhotos.map { photos.import(it) }
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
                allowSimilar, draft.attributes.filterKeys { it !in customIds }, draft.modelIds, locationIds, buyingPriceRupees = buyingPriceRupees)
            for (field in draft.customFields) {
                dao.insertAttribute(field.row(draft.categoryId, id))
                field.options.forEach { dao.insertOption(OptionRow(it.id, field.id, it.name, LookupNames.key(it.name))) }
            }
            val values = validateAttributes(draft.categoryId, draft.attributes, id)
            dao.deleteValues(id)
            dao.insertValues(values.map { it.copy(productId = id) })
            (listOfNotNull(draft.photo) + importedAdditional).distinct().forEachIndexed { position, reference ->
                dao.putPhoto(ProductPhotoRow(id, reference.sha256, reference.sizeBytes, position))
            }
            // createProduct advanced the revision inside this same enclosing transaction.
            id
        }
    }

    private suspend fun validateCatalog(draft: CatalogDraft, previous: CatalogDraft? = null, allowIncomplete: Boolean = false) {
        validateProduct(draft.title, draft.categoryId)
        draft.requireDistinctCustomIdentities()
        val repeated = draft.customFields.groupBy { LookupNames.key(it.name) }.values.firstOrNull { it.size > 1 }?.last()
        if (repeated != null) throw ShopValidation(ProductField.DefinitionName, context.getString(R.string.custom_field_names_must_be_distinct), repeated.id)
        val definitions = draft.customFields.map { field ->
            if (LookupNames.display(field.name).isEmpty()) throw ShopValidation(ProductField.DefinitionName, context.getString(R.string.enter_a_custom_field_name), field.id)
            require(field.options.all { it.definitionId == field.id && !it.retired && LookupNames.key(it.name).isNotEmpty() }) { context.getString(R.string.choose_valid_custom_options) }
            require(field.options.map { LookupNames.key(it.name) }.distinct().size == field.options.size) { context.getString(R.string.custom_option_names_must_be_distinct) }
            require(field.type.isChoice || field.options.isEmpty()) { context.getString(R.string.only_choice_fields_have_options) }
            field.row(draft.categoryId, "catalog-draft")
        }
        val options = draft.customFields.flatMap { it.options }.map { OptionRow(it.id, it.definitionId, it.name, LookupNames.key(it.name)) }
        require(dao.attributes().none { existing -> definitions.any { it.id == existing.id } }) { context.getString(R.string.custom_fields_must_be_independently_owned) }
        validateAttributes(draft.categoryId, draft.attributes, "catalog-draft", previous?.attributes.orEmpty(), allowIncomplete, definitions, options)
        val snapshot = snapshot()
        if (draft.modelIds.any { id -> snapshot.models.none { it.id == id && ((!it.retired && snapshot.makes.any { make -> make.id == it.makeId && !make.retired }) || id in previous?.modelIds.orEmpty()) } })
            throw ShopValidation(ProductField.Fitment, context.getString(R.string.choose_active_motorcycle_models))
        draft.photo?.let { reference ->
            require(reference == previous?.photo || dao.productPhotos().any { it.sha256 == reference.sha256 && it.sizeBytes == reference.sizeBytes } || dao.templates().any { decodeCatalog(it.catalog).photo == reference } || photos.read(reference) != null) { context.getString(R.string.photo_unavailable_choose_it_again_or) }
        }
    }

    suspend fun createMake(name: String): String = activeTransaction {
        val display = lookupDisplay(context, name)
        dao.makes().find { it.normalizedName == LookupNames.key(display) }?.also {
            require(!it.retired) { context.getString(R.string.that_make_is_retired_choose_another) }
        }?.id ?: UUID.randomUUID().toString().also {
            dao.insertMake(MakeRow(it, display, LookupNames.key(display))); advanceRevision()
        }
    }

    suspend fun createModel(makeId: String, name: String): String = activeTransaction {
        require(dao.makes().any { it.id == makeId && !it.retired }) { context.getString(R.string.choose_an_active_make) }
        val display = lookupDisplay(context, name)
        dao.models().find { it.makeId == makeId && it.normalizedName == LookupNames.key(display) }?.also {
            require(!it.retired) { context.getString(R.string.that_model_is_retired_choose_another) }
        }?.id ?: UUID.randomUUID().toString().also {
            dao.insertModel(ModelRow(it, makeId, display, LookupNames.key(display))); advanceRevision()
        }
    }

    suspend fun createLocation(name: String): String = activeTransaction {
        val display = lookupDisplay(context, name)
        dao.locations().find { it.normalizedName == LookupNames.key(display) }?.also {
            require(!it.retired) { context.getString(R.string.that_location_is_retired_choose_another) }
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
            throw ShopValidation(ProductField.Fitment, context.getString(R.string.choose_active_motorcycle_models_retired_fitment))
        if (locationIds.any { locationId -> locations[locationId]?.let { !it.retired || locationId in previousLocations } != true })
            throw ShopValidation(ProductField.Location, context.getString(R.string.choose_active_locations_retired_locations_may))
        dao.deleteFitments(id)
        dao.deleteLocations(id)
        dao.insertFitments(modelIds.map { FitmentRow(id, it) })
        dao.insertLocations(locationIds.map { ProductLocationRow(id, it) })
    }

    suspend fun renameCategory(id: String, name: String) = activeTransaction {
        require(LookupNames.display(name).isNotEmpty()) { context.getString(R.string.enter_a_category_name) }
        val key = LookupNames.key(name)
        require(dao.categories().none { it.id != id && LookupNames.key(it.name) == key }) { context.getString(R.string.that_category_already_exists) }
        dao.updateCategory(dao.categories().single { it.id == id }.copy(name = LookupNames.display(name), normalizedName = key))
        advanceRevision()
    }

    suspend fun createCategory(name: String): String = activeTransaction {
        val display = LookupNames.display(name)
        require(display.isNotEmpty()) { context.getString(R.string.enter_a_category_name) }
        dao.categories().find { LookupNames.key(it.name) == LookupNames.key(display) }?.id ?: run {
            val id = UUID.randomUUID().toString()
            dao.insertCategory(CategoryRow(id, display, LookupNames.key(display), dao.categories().size))
            advanceRevision()
            id
        }
    }

    suspend fun createAttribute(categoryId: String, name: String, type: AttributeType, required: Boolean = false, unit: String = ""): String = activeTransaction {
        require(dao.categories().any { it.id == categoryId }) { context.getString(R.string.choose_a_category) }
        createDefinition(categoryId, null, name, type, required, unit)
    }

    private suspend fun createDefinition(categoryId: String, productId: String?, name: String, type: AttributeType, required: Boolean, unit: String): String {
        val display = LookupNames.display(name)
        if (display.isEmpty()) throw ShopValidation(ProductField.DefinitionName, context.getString(R.string.enter_a_field_name))
        val ownerKey = if (productId == null) "category:$categoryId" else "product:$productId"
        val existing = dao.attributes().find { it.ownerKey == ownerKey && it.normalizedName == LookupNames.key(display) }
        if (existing != null) {
            if (existing.retired || existing.type != type.name || existing.required != required || existing.unit != unit.trim())
                throw ShopValidation(ProductField.DefinitionName, context.getString(R.string.a_field_with_that_name_has))
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
        require(!field.retired) { context.getString(R.string.that_field_is_retired) }
        if (field.required != required) { dao.updateAttribute(field.copy(required = required)); advanceRevision() }
    }

    suspend fun replaceAttribute(id: String, name: String, type: AttributeType, required: Boolean = false, unit: String = ""): String = activeTransaction {
        val field = dao.attributes().single { it.id == id }
        if (field.retired || field.type == type.name) throw ShopValidation(ProductField.DefinitionType, context.getString(R.string.choose_a_new_field_type_for), id)
        val display = LookupNames.display(name)
        if (display.isEmpty()) throw ShopValidation(ProductField.DefinitionName, context.getString(R.string.enter_a_replacement_name), id)
        if (dao.attributes().any { it.ownerKey == field.ownerKey && it.normalizedName == LookupNames.key(display) })
            throw ShopValidation(ProductField.DefinitionName, context.getString(R.string.choose_a_distinct_replacement_name_the), id)
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
        require(field.productId != null && !field.retired) { context.getString(R.string.choose_an_active_product_custom_field) }
        val categoryId = dao.products().single { it.id == field.productId }.categoryId
        val display = LookupNames.display(name)
        if (display.isEmpty()) throw ShopValidation(ProductField.DefinitionName, context.getString(R.string.enter_a_category_field_name), definitionId)
        if (dao.attributes().any { it.productId == null && it.categoryId == categoryId && it.normalizedName == LookupNames.key(display) })
            throw ShopValidation(ProductField.DefinitionName, context.getString(R.string.that_category_field_already_exists_choose), definitionId)
        dao.updateAttribute(field.copy(categoryId = categoryId, productId = null, ownerKey = "category:$categoryId", name = display, normalizedName = LookupNames.key(display)))
        advanceRevision()
    }

    suspend fun createOption(definitionId: String, name: String): String = activeTransaction {
        require(dao.attributes().single { it.id == definitionId }.let { !it.retired && AttributeType.valueOf(it.type).isChoice }) { context.getString(R.string.choose_a_choice_field) }
        val display = LookupNames.display(name)
        require(display.isNotEmpty()) { context.getString(R.string.enter_an_option_name) }
        dao.options().find { it.definitionId == definitionId && it.normalizedName == LookupNames.key(display) }?.also { require(!it.retired) { context.getString(R.string.that_option_is_retired_choose_another) } }?.id ?: run {
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
                    val id = field(context.getString(R.string.position), AttributeType.SingleChoice)
                    listOf(context.getString(R.string.front), context.getString(R.string.rear)).forEach { dao.insertOption(OptionRow(UUID.randomUUID().toString(), id, it, LookupNames.key(it))) }
                }
                1, 2 -> {
                    field(context.getString(R.string.thread_diameter), AttributeType.Decimal, "mm")
                    field(context.getString(R.string.thread_pitch), AttributeType.Decimal, "mm")
                    if (category.position == 2) field(context.getString(R.string.length), AttributeType.Decimal, "mm")
                }
                3 -> {
                    field(context.getString(R.string.bottle_volume), AttributeType.Decimal, "ml")
                    field(context.getString(R.string.viscosity_grade), AttributeType.Text)
                }
            }
        }
    }

    private suspend fun validateAttributes(categoryId: String, values: Map<String, AttributeValue>, productId: String? = null, existing: Map<String, AttributeValue> = emptyMap(), allowIncomplete: Boolean = false, customDefinitions: List<AttributeRow> = emptyList(), customOptions: List<OptionRow> = emptyList()): List<AttributeValueRow> {
        val definitions = dao.attributes().filter { it.productId != null && it.productId == productId || it.productId == null && it.categoryId == categoryId } + customDefinitions
        require(values.keys.all { key -> definitions.any { it.id == key } }) { context.getString(R.string.choose_fields_from_the_selected_category) }
        val options = dao.options() + customOptions
        return definitions.mapNotNull { definition ->
            val value = values[definition.id]
            fun invalid(message: String): Nothing = throw ShopValidation(ProductField.Attribute, context.getString(R.string.formatted_text, definition.name, message), definition.id)
            if (definition.retired && value != null && value != existing[definition.id]) invalid(context.getString(R.string.retired_field_cannot_be_newly_selected))
            if (!allowIncomplete && !definition.retired && definition.required && (value == null || value is AttributeValue.Text && value.text.isBlank() || value is AttributeValue.Choices && value.optionIds.isEmpty())) invalid(context.getString(R.string.required))
            if (value == null) return@mapNotNull null
            when (value) {
                is AttributeValue.Text -> {
                    if (definition.type != AttributeType.Text.name) invalid(context.getString(R.string.enter_the_correct_field_type))
                    AttributeValueRow("", definition.id, value.text, null, null)
                }
                is AttributeValue.Decimal -> {
                    if (definition.type != AttributeType.Decimal.name) invalid(context.getString(R.string.enter_the_correct_field_type))
                    try { exactDecimal(value.exact) } catch (_: InvalidDecimal) { invalid(context.getString(R.string.enter_a_plain_decimal_with_up)) }
                    AttributeValueRow("", definition.id, null, value.exact.trim(), null)
                }
                is AttributeValue.Choices -> {
                    if (!AttributeType.valueOf(definition.type).isChoice) invalid(context.getString(R.string.enter_the_correct_field_type))
                    if (definition.type == AttributeType.SingleChoice.name && value.optionIds.size > 1) invalid(context.getString(R.string.choose_one_option))
                    if (!value.optionIds.all { id -> options.any { it.id == id && it.definitionId == definition.id && (!it.retired || id in (existing[definition.id] as? AttributeValue.Choices)?.optionIds.orEmpty()) } }) invalid(context.getString(R.string.choose_options_from_this_field))
                    AttributeValueRow("", definition.id, null, null, org.json.JSONArray(value.optionIds.sorted()).toString())
                }
            }
        }
    }

    suspend fun setLabelPreferences(media: LabelMedia, copies: String) = activeTransaction {
        val count = parseLabelCopies(context, copies)
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
        require(operationId.isNotBlank()) { context.getString(R.string.a_correction_operation_identity_is_required) }
        val amount = parseWhole(context, quantity, ProductField.CorrectionQuantity)
        if (kind != CorrectionKind.PhysicalCount && amount == 0L) throw ShopValidation(ProductField.CorrectionQuantity, context.getString(R.string.enter_a_positive_whole_quantity_to))
        val explanation = reason.trim()
        if (explanation.isEmpty()) throw ShopValidation(ProductField.CorrectionReason, context.getString(R.string.explain_why_this_stock_correction_is))
        val previous = dao.correction(operationId)
        if (previous != null) {
            require(previous.productId == productId && previous.kind == kind.name && previous.quantity == amount && previous.reason == explanation) { context.getString(R.string.this_operation_identity_already_belongs_to) }
            return@activeTransaction
        }
        requireActiveProduct(productId)
        val balance = dao.balances().singleOrNull { it.productId == productId }
            ?: throw IllegalArgumentException(context.getString(R.string.choose_an_existing_stocked_product))
        val result = if (kind == CorrectionKind.PhysicalCount) amount else {
            if (amount > balance.quantity) throw ShopValidation(ProductField.CorrectionQuantity, context.getString(R.string.cannot_remove_more_than_the_current))
            Math.subtractExact(balance.quantity, amount)
        }
        val difference = Math.subtractExact(result, balance.quantity)
        dao.insertCorrection(CorrectionRow(operationId, productId, kind.name, amount, explanation, balance.quantity, result, difference.takeIf { it != 0L }, System.currentTimeMillis(), Math.addExact(requireNotNull(dao.state()).revision, 1)))
        dao.updateBalance(balance.copy(quantity = result))
        if (result < balance.quantity) reduceOverstockedCarts(mapOf(productId to result))
        advanceRevision()
    }

    suspend fun receiveStock(operationId: String, productId: String, pieces: String, unitCostRupees: String, sellingPriceRupees: String? = null) = activeTransaction {
        require(operationId.isNotBlank()) { context.getString(R.string.an_arrival_operation_identity_is_required) }
        val quantity = parseWhole(context, pieces, ProductField.ArrivalQuantity)
        if (quantity == 0L) throw ShopValidation(ProductField.ArrivalQuantity, context.getString(R.string.enter_a_positive_whole_piece_quantity))
        val cost = parseWhole(context, unitCostRupees, ProductField.PurchaseCost)
        val requestedSellingPrice = sellingPriceRupees?.let { parseWhole(context, it, ProductField.SellingPrice) }
        val previous = dao.arrival(operationId)
        if (previous != null) {
            require(previous.productId == productId && previous.quantity == quantity && previous.unitCostRupees == cost &&
                previous.requestedSellingPriceRupees == requestedSellingPrice) { context.getString(R.string.this_operation_identity_already_belongs_to_2) }
            return@activeTransaction
        }
        val product = requireActiveProduct(productId)
        val balance = dao.balances().single { it.productId == productId }
        val result = try {
            Math.multiplyExact(quantity, cost)
            Math.addExact(balance.quantity, quantity)
        } catch (_: ArithmeticException) { throw ShopValidation(ProductField.ArrivalQuantity, context.getString(R.string.arrival_exceeds_the_supported_quantity_or)) }
        val sellingPrice = requestedSellingPrice ?: product.priceRupees
        repriceUnfinishedCarts(product, sellingPrice)
        dao.updateProduct(product.copy(priceRupees = sellingPrice, buyingPriceRupees = cost))
        dao.insertArrival(ArrivalRow(operationId, productId, quantity, cost, balance.quantity, result,
            System.currentTimeMillis(), Math.addExact(requireNotNull(dao.state()).revision, 1), requestedSellingPrice))
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
        require(lines.isNotEmpty()) { context.getString(R.string.add_products_before_checkout) }
        val total = lines.fold(0L) { sum, line -> Math.addExact(sum, Math.multiplyExact(line.quantity, line.quotedPriceRupees)) }
        val cash = parseWhole(context, cashReceived, ProductField.CashReceived)
        if (cash < total) throw ShopValidation(ProductField.CashReceived, context.getString(R.string.cash_received_must_cover_the_whole))
        val products = dao.products().associateBy { it.id }
        val balances = dao.balances().associateBy { it.productId }
        val shortages = lines.mapNotNull { line ->
            val balance = balances.getValue(line.productId).quantity
            if (line.quantity > balance) StockShortage(line.productId, products.getValue(line.productId).title, line.quantity, balance) else null
        }
        if (shortages.isNotEmpty()) {
            val details = shortages.joinToString("\n") {
                context.getString(R.string.requested_available, it.title, indianNumber(it.requested), indianNumber(it.available))
            }
            throw CheckoutShortage(shortages, context.getString(R.string.nno_sale_saved_edit_the_cart, details))
        }
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
                .map { "${makes.getValue(it.makeId).name} · ${it.name}" }.sorted().joinToString("; ").ifEmpty { context.getString(R.string.finding_unknown_fitment) }
            SaleLineRow(sale.id, product.id, product.sku, product.title, categories.getValue(product.categoryId).name,
                product.description, fitment, line.quantity, line.quotedPriceRupees, before,
                Math.subtractExact(before, line.quantity), Math.negateExact(line.quantity))
        }
        dao.insertSale(sale)
        dao.insertSaleLines(snapshots)
        snapshots.forEach { dao.updateBalance(BalanceRow(it.productId, it.resultingBalance)) }
        // Removing the open cart is its completion; the unique sale retains its identity forever.
        dao.deleteCart(cartId)
        reduceOverstockedCarts(snapshots.associate { it.productId to it.resultingBalance })
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
        val balances = dao.balances().associateBy { it.productId }
        val lines = quantities.mapNotNull { (productId, input) ->
            val product = requireNotNull(products[productId]) { context.getString(R.string.choose_an_existing_stocked_product) }
            require(!product.archived) { context.getString(R.string.this_product_is_archived_choose_an) }
            val quantity = parseCartQuantity(context, input, productId, allowZero = true)
            if (quantity == 0L) return@mapNotNull null
            val available = balances.getValue(productId).quantity
            if (quantity > available) throw ShopValidation(ProductField.CartQuantity,
                context.getString(R.string.cart_stock_maximum, indianNumber(available)), productId)
            CartLineRow(id, productId, quantity, old[productId]?.quotedPriceRupees ?: product.priceRupees)
        }
        try {
            lines.fold(0L) { total, line -> Math.addExact(total, Math.multiplyExact(line.quantity, line.quotedPriceRupees)) }
        } catch (_: ArithmeticException) {
            throw ShopValidation(ProductField.CartQuantity, context.getString(R.string.cart_total_exceeds_the_supported_whole))
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
        val amount = parseCartQuantity(context, pieces, productId)
        val lines = dao.cartLines().filter { it.cartId == id }
        val quantities = lines.associate { it.productId to it.quantity.toString() }.toMutableMap()
        requireActiveProduct(productId)
        val current = lines.find { it.productId == productId }?.quantity ?: 0L
        val available = dao.balances().single { it.productId == productId }.quantity
        val remaining = (available - current).coerceAtLeast(0)
        if (amount > remaining) throw ShopValidation(ProductField.CartQuantity,
            context.getString(R.string.cart_stock_remaining, indianNumber(remaining)), productId)
        quantities[productId] = (current + amount).toString()
        saveCart(id, expectedVersion, cart.customer, quantities)
    }

    private suspend fun reduceOverstockedCarts(balances: Map<String, Long>): Boolean {
        val affected = dao.cartLines().filter { line ->
            balances[line.productId]?.let { line.quantity > it } == true
        }.groupBy { it.cartId }
        for ((id, lines) in affected) {
            val cart = requireNotNull(dao.cart(id))
            val notice = JSONObject(cart.stockReductions)
            for (line in lines) {
                val quantity = balances.getValue(line.productId)
                if (quantity == 0L) dao.deleteCartLine(id, line.productId)
                else dao.updateCartLine(line.copy(quantity = quantity))
                notice.put(line.productId, quantity)
            }
            dao.updateCart(cart.copy(editVersion = Math.addExact(cart.editVersion, 1), stockReductions = notice.toString()))
        }
        return affected.isNotEmpty()
    }

    suspend fun dismissCartStockNotice(id: String, expectedVersion: Long) = activeTransaction {
        val cart = requireCurrentCart(id, expectedVersion)
        if (cart.stockReductions != "{}") {
            dao.updateCart(cart.copy(stockReductions = "{}"))
            advanceRevision()
        }
    }

    suspend fun discardCart(id: String, expectedVersion: Long) = activeTransaction {
        requireCurrentCart(id, expectedVersion)
        dao.deleteCart(id)
        advanceRevision()
    }

    private suspend fun requireCurrentCart(id: String, version: Long): CartRow {
        val cart = dao.cart(id) ?: throw StaleCart(context.getString(R.string.this_cart_changed_reload_the_saved))
        if (cart.editVersion != version) throw StaleCart(context.getString(R.string.this_cart_changed_reload_the_saved))
        return cart
    }

    suspend fun setSellingPrice(id: String, rupees: String) = activeTransaction {
        val price = parseWhole(context, rupees, ProductField.SellingPrice)
        val product = requireActiveProduct(id)
        if (product.priceRupees != price) {
            repriceUnfinishedCarts(product, price)
            dao.updateProduct(product.copy(priceRupees = price))
            advanceRevision()
        }
    }

    private suspend fun repriceUnfinishedCarts(product: ProductRow, price: Long) {
        if (product.priceRupees == price) return
        val id = product.id
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
            throw ShopValidation(ProductField.SellingPrice, context.getString(R.string.selling_price_makes_an_unfinished_cart))
        }
        if (affected.isNotEmpty()) {
            dao.repriceCartLines(id, price)
            val carts = dao.carts().associateBy { it.id }
            affected.keys.forEach { cartId ->
                val cart = requireNotNull(carts[cartId])
                dao.updateCart(cart.copy(editVersion = Math.addExact(cart.editVersion, 1)))
            }
        }
    }

    suspend fun correctionHistory(productId: String): List<StockCorrection> = dao.corrections().filter { it.productId == productId }.map { it.toCorrection() }

    suspend fun openingHistory(id: String): List<OpeningStock> = dao.openings(id).map { OpeningStock(it.quantity, it.recordedAt, it.buyingPriceRupees) }

    private suspend fun requireActiveProduct(id: String): ProductRow {
        val product = requireNotNull(dao.products().find { it.id == id }) { context.getString(R.string.choose_an_existing_stocked_product) }
        require(!product.archived) { context.getString(R.string.this_product_is_archived_choose_an) }
        return product
    }

    private suspend fun validateProduct(title: String, categoryId: String) {
        if (title.isBlank()) throw ShopValidation(ProductField.Title, context.getString(R.string.enter_a_product_title))
        if (dao.categories().none { it.id == categoryId }) throw ShopValidation(ProductField.Category, context.getString(R.string.choose_a_category))
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
                Shop(database, ProductPhotos(storage.photos(generation), context), storage.backups(generation), storage.drive(generation),
                    context.applicationContext, storage, generation).also {
                    try { it.initialize() } catch (error: Throwable) { database.close(); throw error }
                }
            }
        }
        internal fun openDatabase(context: Context, name: String): ShopDatabase =
            Room.databaseBuilder(context.applicationContext, ShopDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17).build()

        internal fun staged(context: Context, database: ShopDatabase, directory: java.io.File) =
            Shop(database, ProductPhotos(java.io.File(directory, "blobs"), context), directory, directory, context)
    }
}

class SimilarProduct(message: String) : IllegalArgumentException(message)

private fun parseCartQuantity(context: Context, value: String, productId: String, allowZero: Boolean = false): Long {
    val quantity = try { parseWhole(context, value, ProductField.CartQuantity) } catch (_: ShopValidation) { null }
    if (quantity == null || (!allowZero && quantity == 0L)) throw ShopValidation(ProductField.CartQuantity, context.getString(if (allowZero) R.string.cart_quantity_nonnegative else R.string.cart_quantity_must_be_a_positive), productId)
    return quantity
}

internal fun parseWhole(context: Context, value: String, field: ProductField): Long {
    val text = value.trim()
    if (!text.matches(Regex("[0-9]+"))) throw ShopValidation(field, context.getString(R.string.must_be_a_nonnegative_whole_number, context.getString(field.label)))
    return text.toLongOrNull() ?: throw ShopValidation(field, context.getString(R.string.exceeds_the_supported_whole_number_limit, context.getString(field.label)))
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
