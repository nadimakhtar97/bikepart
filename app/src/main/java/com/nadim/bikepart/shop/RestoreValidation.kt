package com.nadim.bikepart.shop

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A disposable, migrated copy. No operation here can switch the active database or photo root. */
class ValidatedRestore internal constructor(internal val directory: File, val snapshot: ShopSnapshot) : AutoCloseable {
    internal var provenance: JSONObject = ZipFile(File(directory, "capture.zip")).use { zip ->
        JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText()).let {
            JSONObject().put("sourceDataset", it.getString("datasetId")).put("sourceCapture", it.getString("captureId"))
                .put("sourceRevision", it.getString("revision")).put("sourceCapturedAt", it.getString("capturedAt"))
        }
    }
    fun readPhoto(reference: PhotoReference): ByteArray? = File(directory, "blobs/${reference.sha256}")
        .takeIf { it.isFile }?.readBytes()?.takeIf { it.size.toLong() == reference.sizeBytes && LocalBackup.hash(it) == reference.sha256 }
    override fun close() { directory.deleteRecursively() }
}

internal object RestoreValidation {
    suspend fun prepare(context: Context, archive: File, parent: File, beforeMigration: suspend () -> Unit = {}): ValidatedRestore = withContext(Dispatchers.IO) {
        val directory = File(parent, UUID.randomUUID().toString())
        check(directory.mkdirs()) { "Could not create isolated restore staging. Free space and retry." }
        try {
            // Retain our own input; never extract remote paths or execute backup-provided DDL.
            val copy = File(directory, "capture.zip")
            archive.copyTo(copy)
            val contents = LocalBackup.readForRestore(copy)
            val schema = ZipFile(copy).use { zip -> JSONObject(zip.getInputStream(zip.getEntry("schema.json")).bufferedReader(Charsets.UTF_8).readText()) }
            val version = schema.getInt("roomVersion")
            require(version in 1..15) { "Unsupported shop schema version $version. Update the app before restoring." }
            val trusted = context.assets.open("com.nadim.bikepart.shop.ShopDatabase/$version.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            require(schema.getString("roomIdentity") == trusted.getString("identityHash")) { "Shop schema identity does not match version $version." }
            val entities = trusted.getJSONArray("entities")
            val expectedNames = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }.toSet()
            require(contents.tables.keys == expectedNames) { "Missing or unexpected shop tables." }
            val path = File(directory, "shop.db")
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                // Foreign keys are checked after all rows, allowing arbitrary table order.
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val name = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", name))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", name))
                }
                db.beginTransaction()
                try {
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val name = entity.getString("tableName")
                        val table = contents.tables.getValue(name)
                        val fields = entity.getJSONArray("fields")
                        val columns = (0 until fields.length()).associate { j -> fields.getJSONObject(j).let { it.getString("columnName") to it } }
                        require(table.columns.size == columns.size && table.columns.toSet() == columns.keys) { "$name: unexpected columns." }
                        val metadata = schema.getJSONArray("tables").let { array -> (0 until array.length()).map { array.getJSONObject(it) }.single { it.getString("name") == name } }
                        val declared = metadata.getJSONArray("columns")
                        require(declared.length() == columns.size) { "$name: column metadata is incomplete." }
                        val primary = entity.getJSONObject("primaryKey").getJSONArray("columnNames")
                        val primaryKeys = (0 until primary.length()).map { primary.getString(it) }
                        for (j in 0 until declared.length()) {
                            val column = declared.getJSONObject(j)
                            val field = columns.getValue(column.getString("name"))
                            require(column.getString("type") == field.getString("affinity") && (column.getInt("notNull") == 1) == field.optBoolean("notNull")) { "$name: column type/nullability mismatch." }
                            require(column.getInt("primaryKey") == primaryKeys.indexOf(column.getString("name")) + 1) { "$name: primary key metadata mismatch." }
                        }
                        val declaredKeys = metadata.getJSONArray("foreignKeys").let { keys -> (0 until keys.length()).map { j -> keys.getJSONObject(j).let { listOf(it.getString("table"), it.getString("from"), it.getString("to")) } } }
                        val trustedKeys = db.rawQuery("PRAGMA foreign_key_list(`$name`)", null).use { cursor -> buildList { while (cursor.moveToNext()) add(listOf(cursor.getString(2), cursor.getString(3), cursor.getString(4))) } }
                        require(declaredKeys.size == trustedKeys.size && declaredKeys.toSet() == trustedKeys.toSet()) { "$name: foreign key metadata mismatch." }
                        table.records.forEach { row ->
                            val values = ContentValues()
                            row.forEach { (column, value) ->
                                val field = columns.getValue(column)
                                require(value != null || !field.optBoolean("notNull")) { "$name.$column: required value is null." }
                                when {
                                    value == null -> values.putNull(column)
                                    field.getString("affinity") == "INTEGER" -> {
                                        require(value.matches(Regex("-?(0|[1-9][0-9]*)")) && value.toLongOrNull() != null) { "$name.$column: invalid whole number or money." }
                                        values.put(column, value.toLong())
                                    }
                                    else -> values.put(column, value)
                                }
                            }
                            try { db.insertOrThrow(name, null, values) }
                            catch (error: android.database.SQLException) { throw IllegalArgumentException("$name: duplicate identity, scoped name or invalid row.", error) }
                        }
                    }
                    db.rawQuery("PRAGMA foreign_key_check", null).use { require(!it.moveToFirst()) { "Restore contains an invalid relationship." } }
                    db.version = version
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            val blobs = File(directory, "blobs").apply { mkdirs() }
            contents.blobs.forEach { (hash, bytes) -> File(blobs, hash).writeBytes(bytes) }
            // Only trusted application migrations run, and only against the isolated copy.
            beforeMigration()
            val database = Shop.openDatabase(context, path.absolutePath)
            try {
                database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { require(it.moveToFirst() && it.getString(0) == "ok") { "Staged database integrity check failed." } }
                validate(database)
                val snapshot = Shop.staged(context, database, directory).snapshot()
                require(snapshot.datasetId == contents.datasetId && snapshot.revision == contents.revision) { "Staged migration changed the capture identity." }
                ValidatedRestore(directory, snapshot)
            } finally {
                try {
                    if (database.isOpen) database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use {
                        check(it.moveToFirst() && it.getInt(0) == 0) { "Could not finish the staged database." }
                    }
                } finally { database.close() }
            }
        } catch (cancelled: CancellationException) { directory.deleteRecursively(); throw cancelled }
        catch (error: Exception) {
            directory.deleteRecursively()
            throw IllegalArgumentException("Restore validation failed: ${error.message ?: "damaged or unsupported contents"} Current shop data is unchanged.", error)
        }
    }

    private suspend fun validate(database: ShopDatabase) {
        val dao = database.dao()
        val state = requireNotNull(dao.state()) { "Missing shop settings." }
        val tables = LocalBackup.businessTables.associateWith { name ->
            database.openHelper.writableDatabase.query("SELECT * FROM `$name`").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.columnNames.indices.associate { cursor.columnNames[it] to if (cursor.isNull(it)) null else cursor.getString(it) }) }
            }
        }
        require(tables.getValue("shop_state").size == 1 && state.id == 1 && state.revision >= 0 &&
            tables.getValue("shop_state").single()["normalizerVersion"] == LookupNames.VERSION.toString()) { "Invalid shop settings or normalizer version." }
        uuid(state.datasetId)
        Appearance.valueOf(state.appearance); LabelMedia.valueOf(state.labelMedia)
        require(state.labelCopies > 0) { "Invalid label copies." }
        tables.forEach { (name, rows) -> rows.forEach { row ->
            listOf("id", "productId", "categoryId", "definitionId", "makeId", "modelId", "locationId", "saleId", "cartId").filter { it in row }.forEach { key ->
                row[key]?.let { if (name !in setOf("shop_state", "cart_sequence")) uuid(it) }
            }
            listOf("archived", "retired", "required").filter { it in row }.forEach { require(row[it] in setOf("0", "1")) { "$name: invalid boolean." } }
            // Schema 1 category keys deliberately survive migration under their original normalizer.
            row["normalizedName"]?.let { require(it.isNotBlank() && (name == "categories" || it == LookupNames.key(requireNotNull(row["name"])))) { "$name: invalid normalized name." } }
            row["revision"]?.toLong()?.let { require(it in 1..state.revision || name == "shop_state" && it == 0L) { "$name: invalid revision." } }
            row["operationId"]?.let { require(it.isNotBlank()) { "$name: empty operation identity." } }
        } }
        val products = dao.products().associateBy { it.id }
        require(tables.getValue("categories").all { requireNotNull(it["position"]).toLong() in 0..Int.MAX_VALUE.toLong() }) { "Category position exceeds the supported whole-number limit." }
        require(dao.categories().all { it.name.isNotBlank() && it.position >= 0 }) { "Invalid category name or position." }
        val balances = dao.balances().associate { it.productId to it.quantity }
        require(balances.keys == products.keys && balances.values.all { it >= 0 }) { "Every product needs one nonnegative balance." }
        products.values.forEach { product ->
            require(product.sku == "BP-${product.id.uppercase(java.util.Locale.ROOT)}" && product.title.isNotBlank() && product.priceRupees >= 0 && product.lowStockThreshold?.let { it >= 0 } != false) { "Invalid product identity, title, price or threshold." }
        }
        val attributes = dao.attributes().associateBy { it.id }
        val options = dao.options()
        attributes.values.forEach { field ->
            AttributeType.valueOf(field.type)
            require(field.ownerKey == (field.productId?.let { "product:$it" } ?: "category:${field.categoryId}")) { "Invalid field ownership." }
        }
        options.forEach { require(AttributeType.valueOf(attributes.getValue(it.definitionId).type).isChoice) { "Options require a choice field." } }
        dao.values().forEach { value ->
            val field = attributes.getValue(value.definitionId)
            require(field.productId?.let { it == value.productId } ?: (value.historyId.isNotEmpty() || field.categoryId == products.getValue(value.productId).categoryId)) { "Field is outside the product's scope." }
            if (value.historyId.isNotEmpty()) uuid(value.historyId)
            require(listOf(value.textValue, value.decimalValue, value.choiceIds).count { it != null } == 1) { "Field must contain exactly one typed value." }
            validateValue(AttributeType.valueOf(field.type), when {
                value.textValue != null -> AttributeValue.Text(value.textValue)
                value.decimalValue != null -> AttributeValue.Decimal(value.decimalValue)
                else -> {
                    val ids = org.json.JSONArray(value.choiceIds)
                    val list = (0 until ids.length()).map { ids.getString(it) }
                    require(list.size == list.toSet().size) { "Repeated choice identity." }
                    AttributeValue.Choices(list.toSet())
                }
            }, options.filter { it.definitionId == field.id }.map { it.id }.toSet())
        }
        val models = dao.models().map { it.id }.toSet()
        dao.templates().forEach { template ->
            validateCatalogEncoding(template.catalog)
            val draft = decodeCatalog(template.catalog)
            draft.requireDistinctCustomIdentities()
            require(draft.categoryId == template.categoryId && draft.title.isNotBlank() && draft.modelIds.all { it in models }) { "Invalid template category, title or fitment." }
            require(draft.customFields.map { it.id }.distinct().size == draft.customFields.size && draft.customFields.map { LookupNames.key(it.name) }.distinct().size == draft.customFields.size) { "Duplicate template fields." }
            draft.customFields.forEach { field ->
                uuid(field.id); require(field.id !in attributes && field.name.isNotBlank())
                require(field.options.map { it.id }.distinct().size == field.options.size && field.options.map { LookupNames.key(it.name) }.distinct().size == field.options.size) { "Duplicate template options." }
                require(field.type.isChoice || field.options.isEmpty())
                field.options.forEach { uuid(it.id); require(it.name.isNotBlank()) }
            }
            fun value(id: String, value: AttributeValue, historical: Boolean) {
                val custom = draft.customFields.find { it.id == id }
                val shared = attributes[id]
                require(custom != null || shared != null && shared.productId == null && (historical || shared.categoryId == draft.categoryId)) { "Invalid template field relationship." }
                validateValue(custom?.type ?: AttributeType.valueOf(shared!!.type), value,
                    custom?.options?.map { it.id }?.toSet() ?: options.filter { it.definitionId == id }.map { it.id }.toSet())
            }
            draft.attributes.forEach { (id, entered) -> value(id, entered, false) }
            require(draft.historicalAttributes.map { it.id }.distinct().size == draft.historicalAttributes.size)
            draft.historicalAttributes.forEach { uuid(it.id); value(it.definitionId, it.value, true) }
        }
        val carts = dao.carts()
        val sales = dao.sales()
        val sequence = dao.cartSequence()
        require(tables.getValue("cart_sequence").size == (if (sequence == null) 0 else 1) &&
            (sequence == null || sequence.id == 1 && sequence.lastNumber in 0..state.revision)) { "Invalid cart sequence." }
        val numbers = carts.map { it.number } + sales.map { it.cartNumber }
        require(numbers.distinct().size == numbers.size && numbers.all { it > 0 && it <= (sequence?.lastNumber ?: 0) }) { "Invalid or reused cart number." }
        require(carts.none { cart -> sales.any { it.cartId == cart.id } } && carts.all { it.editVersion in 0..state.revision }) { "Completed cart remains unfinished or has an invalid edit version." }
        val cartLines = dao.cartLines()
        carts.forEach { cart ->
            cartLines.filter { it.cartId == cart.id }.fold(0L) { total, line ->
                require(line.quantity > 0 && line.quotedPriceRupees >= 0 && !products.getValue(line.productId).archived) { "Invalid unfinished cart line." }
                Math.addExact(total, Math.multiplyExact(line.quantity, line.quotedPriceRupees))
            }
        }
        val revisions = mutableSetOf<Long>()
        fun revision(value: Long) { require(value in 1..state.revision && revisions.add(value)) { "Duplicate or invalid business event revision." } }
        data class Movement(val revision: Long, val before: Long, val after: Long, val delta: Long)
        val movements = mutableMapOf<String, MutableList<Movement>>()
        fun movement(id: String, rev: Long, before: Long, after: Long, delta: Long) {
            require(before >= 0 && after >= 0 && Math.addExact(before, delta) == after) { "Stock movement does not reconcile." }
            movements.getOrPut(id) { mutableListOf() }.add(Movement(rev, before, after, delta))
        }
        dao.arrivals().forEach { row ->
            revision(row.revision)
            require(row.quantity > 0 && row.unitCostRupees >= 0) { "Invalid stock arrival quantity or cost." }
            Math.multiplyExact(row.quantity, row.unitCostRupees)
            movement(row.productId, row.revision, row.previousBalance, row.resultingBalance, row.quantity)
        }
        dao.corrections().forEach { row ->
            revision(row.revision)
            require(row.quantity >= 0 && row.reason.isNotBlank()) { "Invalid stock correction." }
            val delta = when (CorrectionKind.valueOf(row.kind)) {
                CorrectionKind.PhysicalCount -> Math.subtractExact(row.quantity, row.previousBalance)
                else -> { require(row.quantity > 0); -row.quantity }
            }
            require(row.movement == delta.takeIf { it != 0L }) { "Invalid correction movement." }
            movement(row.productId, row.revision, row.previousBalance, row.resultingBalance, delta)
        }
        val saleLines = dao.saleLines()
        sales.forEach { sale ->
            revision(sale.revision)
            val lines = saleLines.filter { it.saleId == sale.id }
            require(lines.isNotEmpty()) { "Sale has no receipt lines." }
            val total = lines.fold(0L) { total, line ->
                require(line.quantity > 0 && line.quotedPriceRupees >= 0 && line.title.isNotBlank() && line.category.isNotBlank() && line.sku == products.getValue(line.productId).sku && line.movement == -line.quantity) { "Invalid sale snapshot or movement." }
                movement(line.productId, sale.revision, line.previousBalance, line.resultingBalance, line.movement)
                Math.addExact(total, Math.multiplyExact(line.quantity, line.quotedPriceRupees))
            }
            require(sale.totalRupees == total && sale.cashRupees >= total && sale.changeRupees == Math.subtractExact(sale.cashRupees, total)) { "Sale totals, cash or change do not reconcile." }
        }
        val openings = dao.allOpenings().associateBy { it.productId }
        openings.values.forEach { require(it.quantity > 0) { "Invalid opening stock." } }
        products.values.forEach { product ->
            var balance = openings[product.id]?.quantity ?: 0L
            movements[product.id].orEmpty().sortedBy { it.revision }.forEach { event ->
                require(event.before == balance) { "Stock history has a broken balance chain." }; balance = event.after
            }
            require(balance == balances.getValue(product.id) && (!product.archived || balance == 0L)) { "Saved stock balance differs from stock history." }
        }
        fun target(kind: String, id: String) {
            require(when (BarcodeTargetKind.valueOf(kind)) { BarcodeTargetKind.Product -> id in products; BarcodeTargetKind.Template -> tables.getValue("product_templates").any { it["id"] == id } }) { "Invalid barcode target." }
        }
        val history = dao.barcodeAssignments()
        history.groupBy { it.namespace to it.code }.forEach { (_, events) ->
            var previous: Pair<String, String>? = null
            events.sortedBy { it.revision }.forEach { event ->
                revision(event.revision); requireExternalBarcode(event.namespace, event.code); target(event.kind, event.targetId)
                require((event.previousKind == null) == (event.previousTargetId == null))
                require(previous == event.previousKind?.let { it to requireNotNull(event.previousTargetId) }) { "Broken barcode assignment history." }
                previous = event.kind to event.targetId
            }
        }
        val aliases = dao.barcodeAliases()
        require(aliases.map { it.namespace to it.code }.toSet() == history.map { it.namespace to it.code }.toSet()) { "Missing barcode assignment history." }
        aliases.forEach { alias ->
            requireExternalBarcode(alias.namespace, alias.code); target(alias.kind, alias.targetId)
            val last = history.filter { it.namespace == alias.namespace && it.code == alias.code }.maxBy { it.revision }
            require(last.revision == alias.revision && last.kind == alias.kind && last.targetId == alias.targetId) { "Barcode alias differs from assignment history." }
        }
    }

    private fun uuid(value: String) { require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) { "Invalid stable identity: $value" } }
    private fun validateCatalogEncoding(payload: String) {
        val json = JSONObject(payload)
        require(json.get("version") == 1) { "Unsupported template version." }
        fun strings(objectValue: JSONObject, vararg keys: String) { keys.forEach { require(objectValue.get(it) is String) { "Template $it must be text." } } }
        strings(json, "title", "category", "description")
        val models = json.getJSONArray("models")
        require((0 until models.length()).all { models.get(it) is String }) { "Invalid template fitment identity type." }
        require((0 until models.length()).map { models.getString(it) }.distinct().size == models.length()) { "Repeated template fitment." }
        fun value(json: JSONObject) {
            val keys = json.keys().asSequence().toSet()
            require(keys.size == 1 && keys.single() in setOf("text", "decimal", "choices")) { "Template field must contain exactly one typed value." }
            if (keys.single() == "choices") {
                val array = json.getJSONArray("choices")
                require((0 until array.length()).all { array.get(it) is String } && (0 until array.length()).map { array.getString(it) }.distinct().size == array.length()) { "Invalid template choice identities." }
            } else require(json.get(keys.single()) is String) { "Template text and decimals must be strings." }
        }
        val values = json.getJSONObject("values")
        values.keys().forEach { value(values.getJSONObject(it)) }
        val history = json.optJSONArray("history") ?: org.json.JSONArray()
        for (i in 0 until history.length()) {
            strings(history.getJSONObject(i), "id", "definition")
            value(history.getJSONObject(i).getJSONObject("value"))
        }
        val fields = json.getJSONArray("fields")
        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            strings(field, "id", "name", "type", "unit")
            require(field.get("required") is Boolean) { "Invalid template field boolean." }
            val options = field.getJSONArray("options")
            for (j in 0 until options.length()) strings(options.getJSONObject(j), "id", "name")
        }
        json.optJSONObject("photo")?.let { photo ->
            strings(photo, "hash")
            require(photo.get("size") is Int || photo.get("size") is Long) { "Invalid template photo size." }
        }
    }
    private fun validateValue(type: AttributeType, value: AttributeValue, options: Set<String>) {
        require(when (value) {
            is AttributeValue.Text -> type == AttributeType.Text
            is AttributeValue.Decimal -> { exactDecimal(value.exact); type == AttributeType.Decimal }
            is AttributeValue.Choices -> type.isChoice && value.optionIds.all { it in options } && (type != AttributeType.SingleChoice || value.optionIds.size <= 1)
        }) { "Invalid field type, decimal or choice relationship." }
    }
}
