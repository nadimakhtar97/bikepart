package com.nadim.bikepart

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import org.json.JSONObject

class RestoreWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun lostJournalCommitResponseStillReopensTheRunningAppAndAllowsTheNextWrite() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        lateinit var expected: ShopSnapshot
        val capture = runBlocking { Shop.open(context).use { shop ->
            expected = shop.snapshot()
            shop.captureLocalBackup().also {
                shop.createProduct("Lost response fixture ${UUID.randomUUID()}", expected.categories.first().id, "", "0", "0")
            }
        } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ShopViewModel
            compose.waitUntil(30_000) { compose.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty() }
            scenario.onActivity { model = ViewModelProvider(it)[ShopViewModel::class.java] }
            runBlocking { Shop.open(context).use { shop ->
                shop.validateLocalRestore(capture.file).use { staged ->
                    try {
                        shop.activateRestore(staged, true) { if (it == RestoreBoundary.JournalCommitted) throw java.io.IOException("Lost commit response") }
                        fail("Missing lost response")
                    } catch (_: java.io.IOException) { }
                }
            } }
            compose.waitUntil(30_000) { model.state.value == expected }
            scenario.onActivity { model.write({ setAppearance(if (expected.appearance == Appearance.Dark) Appearance.Light else Appearance.Dark) }) }
            compose.waitUntil(30_000) { model.state.value!!.revision > expected.revision || model.error.value != null }
            assertNull(model.error.value)
            assertEquals(expected.revision + 1, model.state.value!!.revision)
            runBlocking { Shop.open(context).use { it.setAppearance(expected.appearance) } }
        }
    }

    @Test fun ownerMustConfirmReplacementAndCanReopenTheRestoredShop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalScale = shell("settings get system font_scale").ifBlank { "1.0" }
        val original = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        try {
            for (mode in listOf(Appearance.Light, Appearance.Dark)) for (scale in listOf("1.0", "2.0")) {
                shell("settings put system font_scale $scale")
                lateinit var expected: ShopSnapshot
                val drive = runBlocking { Shop.open(context).use { shop ->
                    shop.setAppearance(mode)
                    shop.driveBackup.connect(DriveTarget("owner@example.com", "activation-folder", "Shop backups"))
                    expected = shop.snapshot()
                    RestoreDrive(shop.captureLocalBackup().file).also {
                        shop.createProduct("Absent local change ${UUID.randomUUID()}", expected.categories.first().id, "", "7", "999")
                    }
                } }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    lateinit var model: ShopViewModel
                    compose.waitUntil(30_000) { compose.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("More").performClick()
                    compose.onNodeWithText("Shop backups").performScrollTo().performClick()
                    scenario.onActivity { model = ViewModelProvider(it)[ShopViewModel::class.java]; model.loadRestorePoints(drive) }
                    compose.waitUntil(30_000) { model.restorePoints.value != null && !model.driveBusy.value }
                    val point = model.restorePoints.value!!.single()
                    compose.onNodeWithText("${point.products} products · ${point.sales} sales", substring = true).performScrollTo().performClick()
                    scenario.onActivity { model.validateSelectedRestore(drive) }
                    compose.waitUntil(30_000) { model.restoreValidated.value && !model.driveBusy.value }
                    compose.onNodeWithText("Replace shop with validated restore").performScrollTo().performClick()
                    compose.onNodeWithText("Replace this phone's shop?").assertIsDisplayed()
                    screenshot("activation-confirm-${mode.name}-$scale.png")
                    compose.onNodeWithText("Cancel").performClick()
                    assertEquals(expected.products.size + 1, model.state.value!!.products.size)
                    compose.onNodeWithText("Replace shop with validated restore").performScrollTo().performClick()
                    compose.onNodeWithText("Replace shop now").performClick()
                    compose.waitUntil(30_000) { model.restoreComplete.value || model.driveError.value != null }
                    assertNull(model.driveError.value)
                    compose.onNodeWithText("Shop restored.", substring = true).performScrollTo().assertIsDisplayed()
                    screenshot("activation-success-${mode.name}-$scale.png")
                    assertEquals(expected, model.state.value)
                    assertNull(model.driveStatus.value!!.coveredRevision)
                    compose.onNodeWithText("Save recovery copy…").performScrollTo().assertIsDisplayed().assertIsEnabled()
                    screenshot("activation-recovery-${mode.name}-$scale.png")
                    val recovery = requireNotNull(model.recoveryCapture.value)
                    runBlocking { Shop.open(context).use { shop -> shop.validateLocalRestore(recovery.file).use { assertEquals(expected.products.size + 1, it.snapshot.products.size) } } }
                    scenario.recreate()
                    compose.waitUntil(30_000) { compose.onAllNodesWithText("Shop backups").fetchSemanticsNodes().isNotEmpty() }
                    lateinit var reopened: ShopViewModel
                    scenario.onActivity { reopened = ViewModelProvider(it)[ShopViewModel::class.java] }
                    compose.waitUntil(30_000) { reopened.state.value != null && reopened.recoveryCapture.value != null }
                    assertEquals(expected, reopened.state.value)
                }
            }
        } finally {
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText().trim()
    }

    private fun screenshot(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.getExternalFilesDir(null), name)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        shell("cp ${file.absolutePath} /data/local/tmp/${file.name}")
    }
    @Test fun ownerPreviewsAndValidatesWithoutReplacingTheShopAcrossThemesAndLargeText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText().trim()
        }
        fun screenshot(name: String) {
            val file = File(context.getExternalFilesDir(null), name)
            instrumentation.uiAutomation.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
            shell("cp ${file.absolutePath} /data/local/tmp/${file.name}")
        }
        val originalScale = shell("settings get system font_scale").ifBlank { "1.0" }
        val original = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        val target = DriveTarget("owner@example.com", "restore-folder", "Shop restore points")
        try {
            for (mode in listOf(Appearance.Light, Appearance.Dark)) for (scale in listOf("1.0", "2.0")) {
                shell("settings put system font_scale $scale")
                val drive = runBlocking { Shop.open(context).use { shop ->
                    shop.setAppearance(mode)
                    shop.driveBackup.connect(target)
                    RestoreDrive(shop.captureLocalBackup().file)
                } }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    compose.waitUntil(30_000) { compose.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("More").performClick()
                    compose.onNodeWithText("Shop backups").performScrollTo().performClick()
                    compose.onNodeWithText("Find restore points").performScrollTo().assertIsDisplayed().assertIsEnabled()
                    // Substitute the Google transport after authorization, retaining the actual app/model/Room workflow.
                    lateinit var model: ShopViewModel
                    scenario.onActivity { model = ViewModelProvider(it)[ShopViewModel::class.java]; model.loadRestorePoints(drive) }
                    compose.waitUntil(30_000) { model.restorePoints.value != null && !model.driveBusy.value }
                    val point = model.restorePoints.value!!.single()
                    compose.onNodeWithText("${point.products} products · ${point.sales} sales", substring = true).performScrollTo().performClick()
                    screenshot("restore-point-${mode.name}-$scale.png")
                    compose.onNodeWithText("Local changes absent from this restore point", substring = true).performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Validate selected restore point").performScrollTo().assertIsDisplayed().assertIsEnabled()
                    scenario.onActivity { model.validateSelectedRestore(drive) }
                    compose.waitUntil(30_000) { model.restoreValidated.value || model.driveError.value != null }
                    assertNull(model.driveError.value)
                    compose.onNodeWithText("Validation passed.", substring = true).performScrollTo().assertIsDisplayed()
                    screenshot("restore-${mode.name}-$scale.png")
                    scenario.recreate()
                    compose.waitUntil(30_000) { compose.onAllNodesWithText("Validation passed.", substring = true).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("Validation passed.", substring = true).performScrollTo().assertIsDisplayed()
                    drive.files.remove(drive.manifestId)
                    scenario.onActivity { model.loadRestorePoints(drive) }
                    compose.waitUntil(30_000) { model.restorePoints.value?.isEmpty() == true && !model.driveBusy.value }
                    compose.onNodeWithText("No complete supported restore points found.", substring = true).performScrollTo().assertIsDisplayed()
                    compose.onAllNodesWithText("Validation passed.", substring = true).assertCountEquals(0)
                    screenshot("restore-empty-${mode.name}-$scale.png")
                    assertEquals(mode, runBlocking { Shop.open(context).use { it.snapshot().appearance } })
                }
            }
        } finally {
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }

    @Test fun discoveryOmitsIncompleteCopiesAndSelectionRejectsChangedOrMissingFilesOnAReplacementPhone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = "restore-source-${UUID.randomUUID()}.db"
        val replacement = "restore-replacement-${UUID.randomUUID()}.db"
        try {
            val drive = Shop.open(context, source).use { shop ->
                shop.createProduct("Recovered", shop.snapshot().categories.first().id, "", "4", "50")
                RestoreDrive(shop.captureLocalBackup().file)
            }
            Shop.open(context, replacement).use { shop ->
                shop.driveBackup.connect(DriveTarget("owner@example.com", "restore-folder", "Shop restore points"))
                val before = shop.snapshot()
                val preview = shop.driveBackup.restorePoints(drive).single()
                assertEquals(1, preview.products)
                assertNotEquals(before.datasetId, preview.datasetId)
                shop.driveBackup.validateRestore(drive, preview).use { assertEquals("Recovered", it.snapshot.products.single().title) }
                val dependency = drive.files.keys.first { it != drive.manifestId }
                drive.interruptedId = dependency
                try { shop.driveBackup.validateRestore(drive, preview).close(); fail("Transport interruption accepted") } catch (_: java.io.IOException) { }
                assertEquals(before, shop.snapshot())
                shop.driveBackup.validateRestore(drive, preview).close()
                val bytes = drive.files.remove(dependency)!!
                assertTrue(shop.driveBackup.restorePoints(drive).isEmpty())
                try { shop.driveBackup.validateRestore(drive, preview).close(); fail("Missing dependency accepted") } catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("missing")) }
                drive.files[dependency] = bytes
                val manifest = JSONObject(drive.files.getValue(drive.manifestId).toString(Charsets.UTF_8))
                manifest.put("generation", "changed")
                drive.files[drive.manifestId] = manifest.toString().toByteArray()
                try { shop.driveBackup.validateRestore(drive, preview).close(); fail("Changed preview accepted") } catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("changed")) }
                assertEquals(before, shop.snapshot())
            }
        } finally { context.deleteDatabase(source); context.deleteDatabase(replacement) }
    }
}

internal class RestoreDrive(capture: File) : DriveTransport {
    val files = mutableMapOf<String, ByteArray>()
    val manifestId = UUID.randomUUID().toString()
    var interruptedId: String? = null
    private val names = mutableMapOf<String, String>()
    init {
        ZipFile(capture).use { zip ->
            val local = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText())
            val entries = local.getJSONArray("files")
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val id = UUID.randomUUID().toString()
                files[id] = zip.getInputStream(zip.getEntry(entry.getString("path"))).readBytes()
                entry.put("id", id)
            }
            val manifest = JSONObject().put("driveFormat", 1).put("generation", UUID.randomUUID().toString())
                .put("files", entries).put("localManifest", local)
            listOf("captureId", "datasetId", "revision", "capturedAt").forEach { manifest.put(it, local.getString(it)) }
            files[manifestId] = manifest.toString().toByteArray()
            names[manifestId] = "complete-${local.getString("captureId")}.json"
            names["partial"] = "incomplete-upload"
            names["corrupt"] = "complete-corrupt.json"
            files["corrupt"] = "bad-json".toByteArray()
        }
    }
    override suspend fun newId(): String = error("Restore must not upload")
    override suspend fun list(folder: String) = names.map { DriveEntry(it.key, it.value) }
    override suspend fun download(id: String): ByteArray? {
        if (interruptedId == id) { interruptedId = null; throw java.io.IOException("Download interrupted") }
        return files[id]
    }
    override suspend fun upload(id: String, folder: String, name: String, bytes: ByteArray, session: String?, progress: suspend (String?, Long) -> Unit): Unit = error("Restore must not upload")
}
