package com.nadim.bikepart.shop

import android.database.Cursor
import android.system.Os
import android.system.OsConstants
import androidx.room.withTransaction
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LocalCapture(val file: File, val id: String, val revision: Long, val capturedAt: Long)
data class BackupTable(val columns: List<String>, val records: List<Map<String, String?>>)
data class BackupContents(val datasetId: String, val revision: Long, val tables: Map<String, BackupTable>, val blobs: Map<String, ByteArray>)

/** Portable codec, versioned independently of Room. Reading verifies every required byte. */
object LocalBackup {
    const val FORMAT_VERSION = 1

    fun inspect(file: File): LocalCapture {
        readContents(file, includeBlobs = false)
        return ZipFile(file).use { zip ->
            val manifest = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader(Charsets.UTF_8).use { it.readText() })
            LocalCapture(file, manifest.getString("captureId"), manifest.getString("revision").toLong(), manifest.getString("capturedAt").toLong())
        }
    }
    // Explicit coverage: a future entity must be classified before this codec can capture it.
    internal val businessTables = setOf("categories", "products", "balances", "opening_stock", "shop_state",
        "category_attributes", "attribute_options", "attribute_values", "motorcycle_makes", "motorcycle_models",
        "storage_locations", "product_fitments", "product_locations", "product_photos", "product_templates",
        "stock_corrections", "carts", "cart_lines", "cart_sequence", "sales", "sale_lines", "stock_arrivals",
        "barcode_aliases", "barcode_assignments")

    fun read(file: File): BackupContents = readContents(file, includeBlobs = true)

    internal fun readForRestore(file: File): BackupContents = readContents(file, includeBlobs = true, historicalSchema = true)

    private fun readContents(file: File, includeBlobs: Boolean, historicalSchema: Boolean = false): BackupContents = ZipFile(file).use { zip ->
        val names = zip.entries().asSequence().map { it.name }.toList()
        require(names.size == names.toSet().size) { "Duplicate backup file" }
        val manifest = JSONObject(utf8(zip.getInputStream(requireNotNull(zip.getEntry("manifest.json")) { "Missing backup manifest" }).use { it.readBytes() }))
        require(manifest.get("exportVersion") == FORMAT_VERSION) { "Unsupported export version" }
        require(manifest.get("complete") == true && manifest.getString("encoding") == "UTF-8") { "Incomplete backup or unsupported encoding" }
        require(manifest.getString("revision").matches(Regex("0|[1-9][0-9]*")) && manifest.getString("revision").toLongOrNull() != null) { "Invalid capture revision" }
        require(manifest.getString("capturedAt").toLongOrNull() != null) { "Invalid capture time" }
        for (key in listOf("captureId", "datasetId")) require(runCatching { UUID.fromString(manifest.getString(key)).toString() == manifest.getString(key) }.getOrDefault(false)) { "Invalid $key" }
        val files = manifest.getJSONArray("files")
        val verified = mutableMapOf<String, ByteArray>()
        val sizes = mutableMapOf<String, Long>()
        for (i in 0 until files.length()) {
            val item = files.getJSONObject(i)
            val path = item.getString("path")
            require(integer(item, "size") >= 0) { "Invalid file size: $path" }
            require(path.matches(Regex("(tables/[a-z_]+\\.csv|blobs/[0-9a-f]{64}|schema\\.json)")))
            require(path !in sizes)
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val collected = if (!path.startsWith("blobs/") || includeBlobs) java.io.ByteArrayOutputStream() else null
            zip.getInputStream(requireNotNull(zip.getEntry(path))).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size = Math.addExact(size, count.toLong())
                    digest.update(buffer, 0, count); collected?.write(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            require(size == item.getLong("size") && hash == item.getString("sha256")) { "Backup content failed verification: $path" }
            if (path.startsWith("blobs/")) require(path == "blobs/$hash")
            sizes[path] = size
            val bytes = collected?.toByteArray() ?: byteArrayOf()
            verified[path] = bytes
        }
        require(names.toSet() == verified.keys + "manifest.json") { "Unexpected backup contents" }
        val schema = JSONObject(utf8(requireNotNull(verified["schema.json"])))
        require(schema.get("roomVersion") is Int && schema.get("roomVersion") == manifest.get("roomVersion")) { "Schema version mismatch" }
        val tableMetadata = schema.getJSONArray("tables")
        require(tableMetadata.toString() == manifest.getJSONArray("tables").toString())
        val tables = mutableMapOf<String, BackupTable>()
        for (i in 0 until tableMetadata.length()) {
            val metadata = tableMetadata.getJSONObject(i)
            val name = metadata.getString("name")
            require(name !in tables)
            val rows = decodeCsv(utf8(requireNotNull(verified["tables/$name.csv"])))
            val columns = rows.first().map { requireNotNull(it) }
            require(columns.size == columns.toSet().size) { "Duplicate column in $name" }
            require(columns == metadata.getJSONArray("columns").let { array -> (0 until array.length()).map { array.getJSONObject(it).getString("name") } })
            require((rows.size - 1).toLong() == integer(metadata, "rowCount")) { "Invalid row count: $name" }
            tables[name] = BackupTable(columns, rows.drop(1).map { row -> require(row.size == columns.size); columns.zip(row).toMap() })
        }
        require(historicalSchema || tables.keys == businessTables)
        require(verified.keys == tables.keys.map { "tables/$it.csv" }.toSet() + "schema.json" + verified.keys.filter { it.startsWith("blobs/") }) { "Unexpected files outside the declared schema" }
        val state = tables.getValue("shop_state").records.single()
        require(state["datasetId"] == manifest.getString("datasetId") && state["revision"] == manifest.getString("revision"))
        val dependencies = manifest.getJSONArray("dependencies")
        val blobs = verified.filterKeys { it.startsWith("blobs/") }.mapKeys { it.key.removePrefix("blobs/") }
        val expected = mutableSetOf<String>()
        for (i in 0 until dependencies.length()) {
            val dependency = dependencies.getJSONObject(i)
            val digest = dependency.getString("sha256")
            require(sizes["blobs/$digest"] == integer(dependency, "size"))
            require(expected.add(digest)) { "Duplicate blob dependency" }
        }
        require(blobs.keys == expected)
        val references = tables["product_photos"]?.records.orEmpty().map { PhotoReference(requireNotNull(it["sha256"]), requireNotNull(it["sizeBytes"]).toLong()) } +
            tables["product_templates"]?.records.orEmpty().mapNotNull { decodeCatalog(requireNotNull(it["catalog"])).photo }
        require(references.map { it.sha256 }.toSet() == expected)
        references.forEach { require(sizes["blobs/${it.sha256}"] == it.sizeBytes) }
        BackupContents(manifest.getString("datasetId"), manifest.getString("revision").toLong(), tables, blobs)
    }

    internal fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    internal fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes)).toString()

    internal fun integer(json: JSONObject, key: String): Long {
        val value = json.get(key)
        require(value is Int || value is Long) { "$key must be a whole number" }
        return (value as Number).toLong()
    }

    internal fun csv(rows: List<List<String?>>): ByteArray = rows.joinToString("") { row ->
        row.joinToString(",") { value -> value?.let { "\"${it.replace("\"", "\"\"")}\"" } ?: "\\N" } + "\r\n"
    }.toByteArray(Charsets.UTF_8)

    private fun decodeCsv(text: String): List<List<String?>> {
        val rows = mutableListOf<List<String?>>()
        var position = 0
        while (position < text.length) {
            val row = mutableListOf<String?>()
            while (true) {
                if (text[position] == '"') {
                    position++
                    val value = StringBuilder()
                    while (true) {
                        require(position < text.length)
                        val character = text[position++]
                        if (character != '"') value.append(character)
                        else if (position < text.length && text[position] == '"') { value.append('"'); position++ }
                        else break
                    }
                    row += value.toString()
                } else {
                    require(text.startsWith("\\N", position)); position += 2; row += null
                }
                if (position < text.length && text[position] == ',') { position++; continue }
                require(text.startsWith("\r\n", position)); position += 2; break
            }
            rows += row
        }
        return rows
    }
}

private data class CapturedShop(val revision: Long, val time: Long, val dataset: String, val schema: JSONObject,
    val tables: Map<String, List<List<String?>>>, val photos: List<PhotoReference>)

/** Only the in-memory read is transactional. Disk staging never holds the business transaction. */
internal suspend fun captureBackup(database: ShopDatabase, photos: ProductPhotos, directory: File,
    onCaptured: suspend (Long) -> Unit): LocalCapture {
    val captured = database.withTransaction {
        val db = database.openHelper.writableDatabase
        val actual = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        check(actual == LocalBackup.businessTables) { "Update backup coverage for the current schema" }
        val state = requireNotNull(database.dao().state())
        val metadata = JSONArray()
        val data = LocalBackup.businessTables.sorted().associateWith { name ->
            val columns = JSONArray()
            db.query("PRAGMA table_info(`$name`)").use { cursor -> while (cursor.moveToNext()) {
                columns.put(JSONObject().put("name", cursor.getString(1)).put("type", cursor.getString(2))
                    .put("notNull", cursor.getInt(3)).put("default", cursor.getString(4) ?: JSONObject.NULL).put("primaryKey", cursor.getInt(5)))
            } }
            val dependencies = JSONArray()
            db.query("PRAGMA foreign_key_list(`$name`)").use { cursor -> while (cursor.moveToNext()) {
                dependencies.put(JSONObject().put("table", cursor.getString(2)).put("from", cursor.getString(3)).put("to", cursor.getString(4)))
            } }
            val rows = db.query("SELECT * FROM `$name`").use { cursor ->
                buildList {
                    add(cursor.columnNames.toList())
                    while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                        when (cursor.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
                            Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                            else -> error("Unsupported portable column type")
                        }
                    })
                }
            }
            metadata.put(JSONObject().put("name", name).put("columns", columns).put("foreignKeys", dependencies).put("rowCount", rows.size - 1))
            rows
        }
        val references = (database.dao().productPhotos().map { PhotoReference(it.sha256, it.sizeBytes) } +
            database.dao().templates().mapNotNull { decodeCatalog(it.catalog).photo }).distinct()
        val ddl = JSONArray()
        db.query("SELECT sql FROM sqlite_master WHERE type IN ('table', 'index') AND sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY type DESC, name").use { cursor ->
            while (cursor.moveToNext()) ddl.put(cursor.getString(0))
        }
        val roomIdentity = db.query("SELECT identity_hash FROM room_master_table WHERE id=42").use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }
        val schema = JSONObject().put("roomVersion", db.version).put("roomIdentity", roomIdentity).put("sqliteSchema", ddl).put("tables", metadata)
        CapturedShop(state.revision, System.currentTimeMillis(), state.datasetId, schema, data, references)
    }
    return withContext(Dispatchers.IO) {
        check(directory.isDirectory || directory.mkdirs()) { "Could not create local backup storage" }
        val id = UUID.randomUUID().toString()
        val partial = File(directory, "$id.partial")
        val target = File(directory, "$id.zip")
        val dependencies = JSONArray().apply { captured.photos.forEach { put(JSONObject().put("sha256", it.sha256).put("size", it.sizeBytes)) } }
        // Retained even after interruption. Photo cleanup must treat these references as roots.
        FileOutputStream(File(directory, "$id.pins.json")).use { output ->
            output.write(JSONObject().put("captureId", id).put("revision", captured.revision.toString()).put("dependencies", dependencies).toString().toByteArray(Charsets.UTF_8)); output.fd.sync()
        }
        syncDirectory(directory); syncDirectory(requireNotNull(directory.parentFile))
        directory.parentFile?.parentFile?.let { syncDirectory(it) }
        onCaptured(captured.revision)
        val manifestFiles = JSONArray()
        FileOutputStream(partial).use { output ->
            ZipOutputStream(output).use { zip ->
                fun entry(path: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
                    manifestFiles.put(JSONObject().put("path", path).put("size", bytes.size.toLong()).put("sha256", LocalBackup.hash(bytes)))
                }
                entry("schema.json", captured.schema.toString().toByteArray(Charsets.UTF_8))
                for ((name, rows) in captured.tables) { currentCoroutineContext().ensureActive(); entry("tables/$name.csv", LocalBackup.csv(rows)) }
                for (reference in captured.photos) {
                    currentCoroutineContext().ensureActive()
                    entry("blobs/${reference.sha256}", checkNotNull(photos.read(reference)) { "A required photo is missing or damaged" })
                }
                val manifest = JSONObject().put("exportVersion", LocalBackup.FORMAT_VERSION).put("roomVersion", captured.schema.getInt("roomVersion"))
                    .put("captureId", id).put("datasetId", captured.dataset).put("revision", captured.revision.toString()).put("capturedAt", captured.time.toString())
                    .put("encoding", "UTF-8").put("csv", "RFC4180; all non-null fields quoted; unquoted \\N is null; integers base10; decimals exact text")
                    .put("complete", true).put("files", manifestFiles).put("dependencies", dependencies)
                    .put("tables", captured.schema.getJSONArray("tables"))
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
                zip.finish(); zip.flush(); output.fd.sync()
            }
        }
        LocalBackup.inspect(partial)
        currentCoroutineContext().ensureActive()
        Os.rename(partial.path, target.path)
        syncDirectory(directory)
        LocalCapture(target, id, captured.revision, captured.time)
    }
}

private fun syncDirectory(directory: File) {
    val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
    try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
}
