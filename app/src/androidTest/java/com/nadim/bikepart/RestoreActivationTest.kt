package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import java.util.UUID
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RestoreActivationTest {
    @Test fun unfinishedJournalCannotReplaceAnAlreadyRestoredGeneration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "activation-journal-${UUID.randomUUID()}.db"
        val capture = Shop.open(context, name).use { shop ->
            shop.createProduct("Keep selected generation", shop.snapshot().categories.first().id, "", "8", "10")
            shop.captureLocalBackup().also { capture ->
                shop.validateLocalRestore(capture.file).use { shop.activateRestore(it, true) }
            }
        }
        Shop.open(context, name).use { shop ->
            shop.setSellingPrice(shop.snapshot().products.single().id, "20")
            shop.validateLocalRestore(capture.file).use { staged ->
                try {
                    shop.activateRestore(staged, true) { if (it == RestoreBoundary.JournalWritten) throw IOException("Lost journal write") }
                    fail("Missing journal interruption")
                } catch (_: IOException) { }
            }
        }
        // Death can retain an unfinished AtomicFile .new entry beside the selected journal.
        val key = LocalBackup.hash(name.toByteArray())
        File(context.filesDir, "shop-activations/$key/active.json.new").writeText("unfinished replacement")
        Shop.open(context, name).use { reopened ->
            assertEquals(20L, reopened.snapshot().products.single().priceRupees)
            assertEquals(8L, reopened.snapshot().products.single().quantity)
            reopened.setSellingPrice(reopened.snapshot().products.single().id, "30")
        }
    }

    @Test fun migratedRestoreDropsOldJobsCoverageAndWriterAuthorityAndRetainsProvenance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "activation-writer-${UUID.randomUUID()}.db"
        val drive = MemoryDrive()
        val target = DriveTarget("owner@example.com", "generation-folder", "Shop backups")
        lateinit var expected: ShopSnapshot
        Shop.open(context, name).use { shop ->
            shop.createProduct("Preserved", shop.snapshot().categories.first().id, "", "4", "50")
            shop.driveBackup.connect(target)
            shop.driveBackup.publish(drive)
            val preview = shop.driveBackup.restorePoints(drive).single()
            expected = shop.snapshot()
            // A failed subsequent upload leaves real durable work and coverage behind.
            shop.setSellingPrice(expected.products.single().id, "60")
            drive.interrupt = true
            try { shop.driveBackup.publish(drive); fail("Expected interrupted upload") } catch (_: IOException) { }
            assertTrue(shop.driveBackup.status().pending)
            shop.driveBackup.validateRestore(drive, preview).use { staged -> shop.activateRestore(staged, true) }
            try { shop.setAppearance(Appearance.Dark); fail("Stale writer accepted") } catch (_: ShopReplaced) { }
            try { shop.driveBackup.publish(drive); fail("Stale publisher accepted") } catch (_: ShopReplaced) { }
        }
        Shop.open(context, name).use { restored ->
            assertEquals(expected, restored.snapshot())
            assertEquals(target, restored.driveBackup.status().target)
            assertFalse(restored.driveBackup.status().pending)
            assertNull(restored.driveBackup.status().coveredRevision)
            val filesBefore = drive.files.mapValues { it.value.toList() }
            try { restored.driveBackup.publish(drive); fail("Restored generation stole phone authority") }
            catch (error: IllegalStateException) { assertTrue(error.message!!.contains("active shop phone")) }
            assertEquals(filesBefore, drive.files.mapValues { it.value.toList() })
            val key = LocalBackup.hash(name.toByteArray())
            val journal = org.json.JSONObject(File(context.filesDir, "shop-activations/$key/active.json").readText())
            assertEquals(expected.datasetId, journal.getString("sourceDataset"))
            assertTrue(journal.has("sourceWriter"))
            assertNotEquals(journal.getString("sourceWriter"), journal.getString("generation"))
            restored.setSellingPrice(expected.products.single().id, "70")
            assertEquals(expected.revision + 1, restored.snapshot().revision)
        }
    }

    @Test fun supportedSchemaMigratesBeforeActivationAndMigrationIoFailureNeverReplacesTheShop() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "activation-migration-${UUID.randomUUID()}.db"
        Shop.open(context, name).use { shop ->
            shop.createProduct("Old schema", shop.snapshot().categories.first().id, "", "4", "50")
            val expected = shop.snapshot()
            val capture = shop.captureLocalBackup()
            val table = LocalBackup.read(capture.file).tables.getValue("shop_state")
            val columns = table.columns - setOf("labelMedia", "labelCopies")
            val historical = rewriteCapture(capture.file, File(context.cacheDir, "$name.zip")) { entries, manifest, schema ->
                entries["tables/shop_state.csv"] = LocalBackup.csv(listOf(columns) + table.records.map { row -> columns.map { row[it] } })
                val metadata = schema.getJSONArray("tables").let { array -> (0 until array.length()).map { array.getJSONObject(it) }.single { it.getString("name") == "shop_state" } }
                metadata.put("columns", org.json.JSONArray().apply { val old = metadata.getJSONArray("columns"); for (i in 0 until old.length()) if (old.getJSONObject(i).getString("name") in columns) put(old.getJSONObject(i)) })
                val trusted = context.assets.open("com.nadim.bikepart.shop.ShopDatabase/13.json").bufferedReader().use { org.json.JSONObject(it.readText()).getJSONObject("database") }
                schema.put("roomVersion", 13).put("roomIdentity", trusted.getString("identityHash"))
                manifest.put("roomVersion", 13)
            }
            shop.setSellingPrice(expected.products.single().id, "60")
            val before = shop.snapshot()
            shop.validateLocalRestore(historical).use { staged ->
                try {
                    shop.activateRestore(staged, true) { boundary ->
                        if (boundary == RestoreBoundary.MigrationsStarting) {
                            // Real filesystem failure when Room opens the historical staging DB.
                            staged.directory.parentFile!!.listFiles()!!.filter { it != staged.directory && File(it, "shop.db").isFile }.forEach {
                                val file = File(it, "shop.db")
                                check(file.delete()); check(file.mkdir())
                            }
                        }
                    }
                    fail("Staged migration I/O failure accepted")
                } catch (_: IllegalArgumentException) { }
                assertEquals(before, shop.snapshot())
                shop.activateRestore(staged, true)
            }
            Shop.open(context, name).use { assertEquals(expected, it.snapshot()) }
        }
    }

    @Test fun everyInterruptedBoundaryReopensWholeStockReceiptsCartsPhotosAndQrIdentity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = "activation-source-${UUID.randomUUID()}.db"
        lateinit var expected: ShopSnapshot
        val capture = Shop.open(context, source).use { shop ->
            val category = shop.snapshot().categories.first().id
            val id = shop.createProduct("पुर्जा", category, "Receipt text", "12", "80",
                photo = PhotoChange.Import { "source photo".byteInputStream() })
            shop.receiveStock("delivery", id, "3", "40")
            shop.correctStock("count", id, CorrectionKind.PhysicalCount, "14", "Counted")
            val sold = shop.createCart()
            val version = shop.saveCart(sold, 0, "Customer", mapOf(id to "2"))
            shop.checkout(sold, version, "200")
            val unfinished = shop.createCart()
            shop.saveCart(unfinished, 0, "Waiting", mapOf(id to "3"))
            expected = shop.snapshot()
            shop.captureLocalBackup()
        }
        val qr = ItemQr.product(expected.datasetId, expected.products.single().id)
        for (stop in RestoreBoundary.entries) {
            val name = "activation-$stop-${UUID.randomUUID()}.db"
            lateinit var before: ShopSnapshot
            Shop.open(context, name).use { shop ->
                shop.createProduct("Local only", shop.snapshot().categories.first().id, "", "7", "99",
                    photo = PhotoChange.Import { "local photo".byteInputStream() })
                before = shop.snapshot()
                shop.validateLocalRestore(capture.file).use { staged ->
                    try {
                        shop.activateRestore(staged, true) { if (it == stop) throw IOException("Simulated process death at $stop") }
                        fail("Missing interruption at $stop")
                    } catch (_: IOException) { }
                    catch (error: IllegalArgumentException) { assertTrue(error.cause is IOException) }
                }
            }
            Shop.open(context, name).use { reopened ->
                if (stop in setOf(RestoreBoundary.JournalCommitted, RestoreBoundary.Activated)) {
                    assertEquals(expected, reopened.snapshot())
                    assertEquals(12L, reopened.snapshot().products.single().quantity)
                    assertEquals(160L, reopened.receipt(expected.sales.single().cartId)!!.totalRupees)
                    assertEquals(3L, reopened.snapshot().carts.single().lines.single().quantity)
                    assertEquals(expected.products.single(), (reopened.snapshot().resolveScan(qr) as ScanTarget.StockedProduct).product)
                    assertArrayEquals("source photo".toByteArray(), reopened.readPhoto(expected.products.single().photo!!))
                    assertNull(reopened.driveBackup.status().coveredRevision)
                } else {
                    assertEquals(before, reopened.snapshot())
                    assertArrayEquals("local photo".toByteArray(), reopened.readPhoto(before.products.single().photo!!))
                }
            }
        }
    }

    @Test fun safetyStorageFailureAndChangedStagingLeaveTheCurrentShopWritable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "activation-failure-${UUID.randomUUID()}.db"
        Shop.open(context, name).use { shop ->
            val id = shop.createProduct("Keep", shop.snapshot().categories.first().id, "", "6", "10")
            val capture = shop.captureLocalBackup()
            shop.validateLocalRestore(capture.file).use { staged ->
                val base = File(context.filesDir, "shop-activations/${LocalBackup.hash(name.toByteArray())}").apply { mkdirs() }
                val blocked = File(base, "recovery").apply { writeText("full storage fixture") }
                try { shop.activateRestore(staged, true); fail("Missing safety copy accepted") }
                catch (_: IllegalStateException) { }
                blocked.delete()
                shop.setSellingPrice(id, "11")
                val before = shop.snapshot()
                // The retained input is rechecked rather than trusting a previous preview result.
                val input = File(staged.directory, "capture.zip")
                rewriteCapture(input, File(staged.directory, "unsupported.zip")) { _, manifest, _ -> manifest.put("exportVersion", 99) }.copyTo(input, overwrite = true)
                try { shop.activateRestore(staged, true); fail("Unsupported staged input accepted") }
                catch (_: IllegalArgumentException) { }
                assertEquals(before, shop.snapshot())
                shop.setSellingPrice(id, "12")
            }
        }
        Shop.open(context, name).use { assertEquals(12L, it.snapshot().products.single().priceRupees) }
    }

    @Test fun replacementRequiresConfirmationAndKeepsAUsableRecoveryCopy() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "activation-${UUID.randomUUID()}.db"
        Shop.open(context, name).use { shop ->
            val category = shop.snapshot().categories.first().id
            val id = shop.createProduct("Original", category, "", "10", "50",
                photo = PhotoChange.Import { "original photo".byteInputStream() })
            val capture = shop.captureLocalBackup()
            shop.setSellingPrice(id, "75")
            val before = shop.snapshot()
            shop.validateLocalRestore(capture.file).use { staged ->
                try { shop.activateRestore(staged, confirmed = false); fail("Unconfirmed replacement") }
                catch (_: IllegalArgumentException) { }
                assertEquals(before, shop.snapshot())
                val recovery = shop.activateRestore(staged, confirmed = true)
                shop.validateLocalRestore(recovery.file).use { assertEquals(before, it.snapshot) }
            }
        }
        Shop.open(context, name).use { reopened ->
            assertEquals(50L, reopened.snapshot().products.single().priceRupees)
            assertArrayEquals("original photo".toByteArray(), reopened.readPhoto(reopened.snapshot().products.single().photo!!))
            assertNull(reopened.driveBackup.status().coveredRevision)
        }
    }
}
