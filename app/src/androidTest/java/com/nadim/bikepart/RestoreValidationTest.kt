package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.json.JSONArray

class RestoreValidationTest {
    @Test fun historicalCustomFieldsStayProductOwnedAndTemplateIdentitiesStayDistinct() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "restore-scope-${UUID.randomUUID()}.db"
        val fixtures = File(context.cacheDir, name).apply { mkdirs() }
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val first = shop.createProduct("First", category, "", "0", "0")
                val second = shop.createProduct("Second", category, "", "0", "0")
                val custom = shop.createCustomAttribute(first, "Private", AttributeType.Text)
                shop.editProduct(first, "First", category, "", attributes = mapOf(custom to AttributeValue.Text("Owned")))
                val field = UUID.randomUUID().toString()
                val option = UUID.randomUUID().toString()
                shop.saveTemplate(CatalogDraft("Template", category, customFields = listOf(DraftField(field, "Choice", AttributeType.SingleChoice, false, "", listOf(AttributeOption(option, field, "A"))))))
                val cart = shop.createCart()
                shop.discardCart(cart, 0)
                val before = shop.snapshot()
                val capture = shop.captureLocalBackup()
                val values = LocalBackup.read(capture.file).tables.getValue("attribute_values")
                val historical = rewriteCapture(capture.file, File(fixtures, "history.zip")) { entries, _, _ ->
                    val row = values.records.single().toMutableMap().apply { put("productId", second); put("historyId", UUID.randomUUID().toString()) }
                    entries["tables/attribute_values.csv"] = LocalBackup.csv(listOf(values.columns, values.columns.map { row[it] }))
                }
                reject(shop, historical, "another product's historical custom value")
                val templates = LocalBackup.read(capture.file).tables.getValue("product_templates")
                val collision = rewriteCapture(capture.file, File(fixtures, "template.zip")) { entries, _, _ ->
                    val row = templates.records.single().toMutableMap()
                    val catalog = JSONObject(row.getValue("catalog")!!)
                    catalog.getJSONArray("fields").getJSONObject(0).getJSONArray("options").getJSONObject(0).put("id", field)
                    row["catalog"] = catalog.toString()
                    entries["tables/product_templates.csv"] = LocalBackup.csv(listOf(templates.columns, templates.columns.map { row[it] }))
                }
                reject(shop, collision, "colliding template definition/option identities")
                val counter = LocalBackup.read(capture.file).tables.getValue("cart_sequence")
                val invalidCounter = rewriteCapture(capture.file, File(fixtures, "counter.zip")) { entries, _, _ ->
                    val row = counter.records.single() + ("id" to "0")
                    entries["tables/cart_sequence.csv"] = LocalBackup.csv(listOf(counter.columns, counter.columns.map { row[it] }))
                }
                reject(shop, invalidCounter, "invalid singleton cart counter identity")
                assertEquals(before, shop.snapshot())
            }
        } finally { context.deleteDatabase(name); fixtures.deleteRecursively() }
    }

    @Test fun invalidBusinessDataAndCorruptDependenciesLeaveTheCurrentShopAndPhotosUnchanged() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "restore-reject-${UUID.randomUUID()}.db"
        val fixtures = File(context.cacheDir, name).apply { mkdirs() }
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val decimal = shop.createAttribute(category, "Size", AttributeType.Decimal)
                val product = shop.createProduct("Original", category, "", "12", "80",
                    attributes = mapOf(decimal to AttributeValue.Decimal("1.250")), photo = PhotoChange.Import { "intact".byteInputStream() })
                shop.linkBarcode("0123", BarcodeTarget(BarcodeTargetKind.Product, product))
                shop.receiveStock("arrival", product, "3", "40")
                shop.correctStock("count", product, CorrectionKind.PhysicalCount, "14", "Count")
                val cart = shop.createCart()
                val version = shop.saveCart(cart, 0, "Customer", mapOf(product to "2"))
                shop.checkout(cart, version, "200")
                val before = shop.snapshot()
                val capture = shop.captureLocalBackup()
                val mutations = listOf<Triple<String, String, String?>>(
                    Triple("products", "priceRupees", "1.5"), Triple("products", "priceRupees", "-1"),
                    Triple("products", "priceRupees", "9223372036854775808"), Triple("products", "title", null),
                    Triple("products", "id", "bad-id"), Triple("products", "archived", "2"),
                    Triple("products", "categoryId", UUID.randomUUID().toString()),
                    Triple("balances", "quantity", "999"), Triple("stock_arrivals", "previousBalance", "5"),
                    Triple("stock_corrections", "movement", "-2"), Triple("sale_lines", "quantity", "0"),
                    Triple("sale_lines", "sku", "wrong-snapshot"), Triple("sales", "totalRupees", "10"),
                    Triple("sales", "changeRupees", "0"), Triple("attribute_values", "decimalValue", "1e3"),
                    Triple("attribute_values", "textValue", "wrong-type"),
                    Triple("barcode_aliases", "targetId", UUID.randomUUID().toString()),
                    Triple("category_attributes", "ownerKey", "category:wrong"),
                    Triple("shop_state", "normalizerVersion", "99"), Triple("shop_state", "normalizerVersion", "4294967297"),
                    Triple("categories", "position", "4294967296"), Triple("shop_state", "appearance", "Unknown")
                )
                for ((index, mutation) in mutations.withIndex()) {
                    val contents = LocalBackup.read(capture.file)
                    val table = contents.tables.getValue(mutation.first)
                    val rows = table.records.mapIndexed { rowIndex, row -> if (rowIndex == 0) row + (mutation.second to mutation.third) else row }
                    val file = rewriteCapture(capture.file, File(fixtures, "$index.zip")) { entries, _, _ ->
                        entries["tables/${mutation.first}.csv"] = LocalBackup.csv(listOf(table.columns) + rows.map { row -> table.columns.map { row[it] } })
                    }
                    reject(shop, file, mutation.toString())
                    assertEquals(before, shop.snapshot())
                    assertArrayEquals("intact".toByteArray(), shop.readPhoto(before.products.single().photo!!))
                }
                val missing = rewriteCapture(capture.file, File(fixtures, "missing.zip")) { entries, _, _ -> entries.keys.filter { it.startsWith("blobs/") }.forEach { entries.remove(it) } }
                reject(shop, missing, "missing photo")
                val noncanonical = rewriteCapture(capture.file, File(fixtures, "identity.zip")) { entries, _, _ ->
                    val identity = "AAAAAAAA-1111-4111-8111-111111111111"
                    entries.keys.filter { it.startsWith("tables/") }.forEach { path -> entries[path] = entries.getValue(path).toString(Charsets.UTF_8)
                        .replace(product, identity).replace(product.uppercase(java.util.Locale.ROOT), identity).toByteArray(Charsets.UTF_8) }
                }
                reject(shop, noncanonical, "noncanonical identity breaks permanent QR encoding")
                val fractionalSize = rewriteCapture(capture.file, File(fixtures, "fractional-size.zip")) { _, manifest, _ ->
                    manifest.getJSONArray("dependencies").getJSONObject(0).put("size", 6.5)
                }
                reject(shop, fractionalSize, "fractional blob size")
                val fractionalCount = rewriteCapture(capture.file, File(fixtures, "fractional-count.zip")) { _, _, schema ->
                    val tables = schema.getJSONArray("tables")
                    for (i in 0 until tables.length()) if (tables.getJSONObject(i).getString("name") == "products") tables.getJSONObject(i).put("rowCount", 1.5)
                }
                reject(shop, fractionalCount, "fractional row count")
                val unsupported = rewriteCapture(capture.file, File(fixtures, "version.zip")) { _, manifest, _ -> manifest.put("exportVersion", 99) }
                reject(shop, unsupported, "unsupported format")
                val duplicate = rewriteCapture(capture.file, File(fixtures, "duplicate.zip")) { entries, _, _ ->
                    val table = LocalBackup.read(capture.file).tables.getValue("categories")
                    val rows = table.records.toMutableList()
                    rows[1] = rows[1] + ("normalizedName" to rows[0]["normalizedName"])
                    entries["tables/categories.csv"] = LocalBackup.csv(listOf(table.columns) + rows.map { row -> table.columns.map { row[it] } })
                }
                reject(shop, duplicate, "scoped uniqueness")
                val corrupt = File(fixtures, "corrupt.zip").apply { writeText("not a manifest") }
                reject(shop, corrupt, "corrupt manifest")
                assertEquals(before, shop.snapshot())
            }
            Shop.open(context, name).use { assertEquals(12L, it.snapshot().products.single().quantity) }
        } finally { context.deleteDatabase(name); fixtures.deleteRecursively() }
    }

    @Test fun supportedHistoricalSchemaMigratesOnlyInsideStaging() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "restore-migrate-${UUID.randomUUID()}.db"
        val fixture = File(context.cacheDir, "$name.zip")
        try {
            Shop.open(context, name).use { shop ->
                shop.createProduct("Preserved", shop.snapshot().categories.first().id, "", "4", "50")
                val before = shop.snapshot()
                val capture = shop.captureLocalBackup()
                val table = LocalBackup.read(capture.file).tables.getValue("shop_state")
                val columns = table.columns - setOf("labelMedia", "labelCopies")
                rewriteCapture(capture.file, fixture) { entries, manifest, schema ->
                    entries["tables/shop_state.csv"] = LocalBackup.csv(listOf(columns) + table.records.map { row -> columns.map { row[it] } })
                    val metadata = schema.getJSONArray("tables").let { array -> (0 until array.length()).map { array.getJSONObject(it) }.single { it.getString("name") == "shop_state" } }
                    metadata.put("columns", JSONArray().apply { val old = metadata.getJSONArray("columns"); for (i in 0 until old.length()) if (old.getJSONObject(i).getString("name") in columns) put(old.getJSONObject(i)) })
                    val trusted = context.assets.open("com.nadim.bikepart.shop.ShopDatabase/13.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
                    schema.put("roomVersion", 13).put("roomIdentity", trusted.getString("identityHash"))
                    manifest.put("roomVersion", 13)
                    // A category created before lookup-normalizer v1 retains its legacy key.
                    val categories = LocalBackup.read(capture.file).tables.getValue("categories")
                    entries["tables/categories.csv"] = LocalBackup.csv(listOf(categories.columns) + categories.records.mapIndexed { index, row -> categories.columns.map { if (index == 0 && it == "normalizedName") "legacy-category-key" else row[it] } })
                }
                shop.validateLocalRestore(fixture).use { assertEquals(before, it.snapshot) }
                assertEquals(before, shop.snapshot())
            }
        } finally { context.deleteDatabase(name); fixture.delete() }
    }

    private suspend fun reject(shop: Shop, file: File, label: String) {
        try { shop.validateLocalRestore(file).use { fail("Accepted $label") } }
        catch (error: IllegalArgumentException) { assertTrue(error.message, error.message!!.contains("Current shop data is unchanged")) }
    }

    @Test fun stagedShopPreservesUnicodeDecimalsNullsReceiptsAndPhotosWithoutReplacingLiveState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "restore-${UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val decimal = shop.createAttribute(category, "Diameter", AttributeType.Decimal)
                val product = shop.createProduct("पुर्जा, \"A\"", category, "Line\nsecond", "12", "80",
                    attributes = mapOf(decimal to AttributeValue.Decimal("123456789012345678.123456789012")),
                    photo = PhotoChange.Import { "shared-photo".byteInputStream() })
                shop.saveTemplate(shop.snapshot().copyCatalog(product))
                shop.receiveStock("arrival", product, "3", "40")
                shop.correctStock("count", product, CorrectionKind.PhysicalCount, "14", "Count")
                val cart = shop.createCart()
                val version = shop.saveCart(cart, 0, "ग्राहक", mapOf(product to "2"))
                shop.checkout(cart, version, "200")
                val expected = shop.snapshot()
                val capture = shop.captureLocalBackup()
                shop.setSellingPrice(product, "99")
                val live = shop.snapshot()
                shop.validateLocalRestore(capture.file).use { staged ->
                    assertEquals(expected, staged.snapshot)
                    assertArrayEquals("shared-photo".toByteArray(), staged.readPhoto(expected.products.single().photo!!))
                    assertEquals(live, shop.snapshot())
                }
            }
            Shop.open(context, name).use { assertEquals(99L, it.snapshot().products.single().priceRupees) }
        } finally { context.deleteDatabase(name) }
    }
}

/** Build deliberately hostile, internally checksummed archives rather than merely breaking a hash. */
internal fun rewriteCapture(source: File, target: File, edit: (MutableMap<String, ByteArray>, JSONObject, JSONObject) -> Unit): File {
    val entries = ZipFile(source).use { zip -> zip.entries().asSequence().associate { it.name to zip.getInputStream(it).readBytes() }.toMutableMap() }
    val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
    val schema = JSONObject(entries.getValue("schema.json").toString(Charsets.UTF_8))
    edit(entries, manifest, schema)
    entries["schema.json"] = schema.toString().toByteArray(Charsets.UTF_8)
    manifest.put("tables", schema.getJSONArray("tables"))
    manifest.put("files", JSONArray().apply { entries.filterKeys { it != "manifest.json" }.forEach { (path, bytes) ->
        put(JSONObject().put("path", path).put("size", bytes.size).put("sha256", java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }))
    } })
    entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
    ZipOutputStream(target.outputStream()).use { zip -> entries.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() } }
    return target
}
