package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalBackupTest {
    @Test fun reopeningChoosesHighestCommittedRevisionWhenThePhoneClockMovesBackward() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "backup-clock-${UUID.randomUUID()}.db"
        try {
            lateinit var newest: LocalCapture
            Shop.open(context, name).use { shop ->
                val previous = shop.captureLocalBackup()
                shop.setAppearance(Appearance.Dark)
                newest = shop.captureLocalBackup()
                // A clock-change fixture alters only the manifest timestamp, not captured state.
                val changed = File(newest.file.parentFile, "clock-fixture.tmp")
                java.util.zip.ZipFile(newest.file).use { source ->
                    java.util.zip.ZipOutputStream(changed.outputStream()).use { output ->
                        source.entries().asSequence().forEach { entry ->
                            val bytes = source.getInputStream(entry).use { it.readBytes() }
                            output.putNextEntry(java.util.zip.ZipEntry(entry.name))
                            output.write(if (entry.name == "manifest.json") org.json.JSONObject(bytes.toString(Charsets.UTF_8))
                                .put("capturedAt", (previous.capturedAt - 60_000).toString()).toString().toByteArray(Charsets.UTF_8) else bytes)
                            output.closeEntry()
                        }
                    }
                }
                check(changed.renameTo(newest.file))
            }
            Shop.open(context, name).use { shop ->
                val reopened = requireNotNull(shop.latestLocalBackup())
                assertEquals(newest.id, reopened.id)
                assertEquals(1L, reopened.revision)
                assertEquals(Appearance.Dark, shop.snapshot().appearance)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun portableTablesReopenAsTheSameShopWithExactDecimalsAndAllImplementedRelationships() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "backup-roundtrip-${UUID.randomUUID()}.db"
        val restoredName = "$name-restored"
        val destination = File(context.cacheDir, name)
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val decimal = shop.createAttribute(category, "Exact size", AttributeType.Decimal)
                val text = shop.createAttribute(category, "Marking", AttributeType.Text)
                val choice = shop.snapshot().attributes.first { it.categoryId == category && it.type == AttributeType.SingleChoice }
                val option = shop.snapshot().options.first { it.definitionId == choice.id }
                val make = shop.createMake("मोटर")
                val model = shop.createModel(make, "Model, \"A\"")
                val location = shop.createLocation("Shelf १")
                val id = shop.createProduct("पुर्जा", category, "", "12", "80", attributes = mapOf(
                    decimal to AttributeValue.Decimal("123456789012345678.123456789012"), text to AttributeValue.Text("\\N\n\"quoted\", text"),
                    choice.id to AttributeValue.Choices(setOf(option.id))),
                    modelIds = setOf(model), locationIds = setOf(location), photo = PhotoChange.Import { "shared photo".byteInputStream() })
                val template = shop.saveTemplate(shop.snapshot().copyCatalog(id))
                shop.retireTemplate(template)
                shop.retireAttribute(text)
                shop.linkBarcode("000,\"123", BarcodeTarget(BarcodeTargetKind.Product, id))
                shop.receiveStock("arrival", id, "3", "40")
                shop.correctStock("correction", id, CorrectionKind.PhysicalCount, "14", "Count\nchecked")
                val sold = shop.createCart()
                val edit = shop.saveCart(sold, 0, "Customer", mapOf(id to "2"))
                shop.checkout(sold, edit, "200")
                val pending = shop.createCart()
                shop.saveCart(pending, 0, "", mapOf(id to "1"))
                shop.setAppearance(Appearance.Dark)
                shop.setLabelPreferences(LabelMedia.SharedBin, "3")
                val expected = shop.snapshot()
                val capture = shop.captureLocalBackup(destination)
                val portable = LocalBackup.read(capture.file)
                assertEquals("123456789012345678.123456789012", portable.tables.getValue("attribute_values").records.single { it["definitionId"] == decimal }["decimalValue"])
                assertEquals("", portable.tables.getValue("products").records.single()["description"])
                assertEquals(1, portable.blobs.size) // Shared product/template dependency is exported once.
                // Independent fixture importer: reconstruct the exported schema and values, then use Shop.
                val schema = java.util.zip.ZipFile(capture.file).use { zip -> org.json.JSONObject(zip.getInputStream(zip.getEntry("schema.json")).bufferedReader(Charsets.UTF_8).readText()) }
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(restoredName), null).use { db ->
                    val ddl = schema.getJSONArray("sqliteSchema")
                    for (i in 0 until ddl.length()) db.execSQL(ddl.getString(i))
                    portable.tables.forEach { (table, content) -> content.records.forEach { row ->
                        val values = android.content.ContentValues()
                        row.forEach { (column, value) -> if (value == null) values.putNull(column) else values.put(column, value) }
                        db.insertOrThrow(table, null, values)
                    } }
                    db.version = schema.getInt("roomVersion")
                }
                val photoRoot = File(context.filesDir, "product-photos/" + java.security.MessageDigest.getInstance("SHA-256").digest(restoredName.toByteArray()).joinToString("") { "%02x".format(it) })
                photoRoot.mkdirs()
                portable.blobs.forEach { (hash, bytes) -> File(photoRoot, hash).writeBytes(bytes) }
                Shop.open(context, restoredName).use { restored ->
                    assertEquals(expected, restored.snapshot())
                    assertArrayEquals("shared photo".toByteArray(), restored.readPhoto(expected.products.single().photo!!))
                    assertEquals(expected.sales.single(), restored.receipt(expected.sales.single().cartId))
                }
            }
        } finally { context.deleteDatabase(name); context.deleteDatabase(restoredName); destination.deleteRecursively() }
    }

    @Test fun missingPhotosAndCorruptArchivesCannotPublishOrValidateCompleteCaptures() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "backup-damaged-${UUID.randomUUID()}.db"
        val destination = File(context.cacheDir, name)
        try {
            Shop.open(context, name).use { shop ->
                shop.createProduct("Photo", shop.snapshot().categories.first().id, "", "0", "0", photo = PhotoChange.Import { "original".byteInputStream() })
                val before = shop.snapshot()
                val good = shop.captureLocalBackup(destination)
                val photoRoot = File(context.filesDir, "product-photos/" + java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) })
                File(photoRoot, before.products.single().photo!!.sha256).delete()
                try { shop.captureLocalBackup(destination); fail("Missing dependency must fail") } catch (_: IllegalStateException) { }
                assertEquals(listOf(good.file), destination.listFiles()!!.filter { it.extension == "zip" })
                assertEquals(before.revision, LocalBackup.read(good.file).revision)
                assertEquals(before, shop.snapshot())
                val corrupt = File(destination, "corrupt.zip")
                java.util.zip.ZipFile(good.file).use { source ->
                    java.util.zip.ZipOutputStream(corrupt.outputStream()).use { output ->
                        source.entries().asSequence().forEach { entry ->
                            output.putNextEntry(java.util.zip.ZipEntry(entry.name))
                            output.write(if (entry.name == "tables/products.csv") "changed".toByteArray() else source.getInputStream(entry).readBytes())
                            output.closeEntry()
                        }
                    }
                }
                try { LocalBackup.read(corrupt); fail("Corrupt bytes must fail") } catch (_: IllegalArgumentException) { }
                // A new identity is required after incomplete output and a newer shop write.
                shop.editProduct(before.products.single().id, "No photo", before.categories.first().id, "", photo = PhotoChange.Remove)
                val retry = shop.captureLocalBackup(destination)
                assertNotEquals(good.id, retry.id)
                assertEquals(before.revision + 1, LocalBackup.read(retry.file).revision)
            }
        } finally { context.deleteDatabase(name); destination.deleteRecursively() }
    }

    @Test fun concurrentSaleAndPhotoReplacementCannotMixCapturedRevisionsAndInterruptedAttemptsUseNewIdentities() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "backup-concurrent-${UUID.randomUUID()}.db"
        val destination = File(context.cacheDir, name)
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val id = shop.createProduct("Original", category, "", "12", "80", photo = PhotoChange.Import { "old photo".byteInputStream() })
                val cart = shop.createCart()
                val version = shop.saveCart(cart, 0, "Customer", mapOf(id to "2"))
                val before = shop.snapshot()
                var interruptedRevision = -1L
                try {
                    shop.captureLocalBackup(destination) { revision ->
                        interruptedRevision = revision
                        throw java.io.IOException("Power lost after pinning")
                    }
                    fail("Interrupted staging must fail")
                } catch (_: java.io.IOException) { }
                assertEquals(before.revision, interruptedRevision)
                assertTrue(destination.listFiles().orEmpty().none { it.extension == "zip" })
                val abandoned = destination.listFiles()!!.single().name.substringBefore('.')
                val capture = shop.captureLocalBackup(destination) {
                    // A second Room connection commits while the first capture is staging.
                    Shop.open(context, name).use { editor ->
                        editor.checkout(cart, version, "160")
                        editor.editProduct(id, "Replacement", category, "Changed", photo = PhotoChange.Import { "new photo".byteInputStream() })
                    }
                }
                assertNotEquals(abandoned, capture.id)
                val backup = LocalBackup.read(capture.file)
                assertEquals(before.revision, backup.revision)
                assertEquals("Original", backup.tables.getValue("products").records.single()["title"])
                assertEquals("12", backup.tables.getValue("balances").records.single()["quantity"])
                assertEquals(cart, backup.tables.getValue("carts").records.single()["id"])
                assertTrue(backup.tables.getValue("sales").records.isEmpty())
                assertArrayEquals("old photo".toByteArray(), backup.blobs.values.single())
                assertEquals(10L, shop.snapshot().products.single().quantity)
                assertEquals(before.revision + 2, shop.snapshot().revision)
            }
        } finally { context.deleteDatabase(name); destination.deleteRecursively() }
    }

    @Test fun portableCapturePreservesCommittedValuesAndDoesNotAdvanceRevision() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "backup-${UUID.randomUUID()}.db"
        val destination = File(context.cacheDir, name)
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val id = shop.createProduct("पुर्जा, \"bolt\"", category, "Line one\nLine two", "12", "9007199254740993",
                    photo = PhotoChange.Import { "photo bytes".byteInputStream() })
                val before = shop.snapshot()
                val capture = shop.captureLocalBackup(destination)
                val backup = LocalBackup.read(capture.file)
                assertEquals(before.revision, backup.revision)
                assertEquals(before.datasetId, backup.datasetId)
                val product = backup.tables.getValue("products").records.single()
                assertEquals(id, product["id"])
                assertEquals("पुर्जा, \"bolt\"", product["title"])
                assertEquals("Line one\nLine two", product["description"])
                assertEquals("9007199254740993", product["priceRupees"])
                assertNull(product["lowStockThreshold"])
                assertArrayEquals("photo bytes".toByteArray(), backup.blobs.values.single())
                assertEquals(before, shop.snapshot())
            }
        } finally { context.deleteDatabase(name); destination.deleteRecursively() }
    }
}
