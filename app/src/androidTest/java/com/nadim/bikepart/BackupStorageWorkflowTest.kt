package com.nadim.bikepart

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nadim.bikepart.shop.*
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupStorageWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun ownerInspectsStorageAndConfirmsHistoryRemovalAcrossThemesAndLargeText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalScale = shell("settings get system font_scale").ifBlank { "1.0" }
        val appearance = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        try {
            for (mode in listOf(Appearance.Light, Appearance.Dark)) for (scale in listOf("1.0", "2.0")) {
                shell("settings put system font_scale $scale")
                val drive = RetentionDrive(File(context.cacheDir, "storage-ui-${UUID.randomUUID()}"))
                val now = System.currentTimeMillis()
                runBlocking { Shop.open(context).use { shop ->
                    shop.setAppearance(mode)
                    shop.driveBackup.connect(DriveTarget("owner@example.com", UUID.randomUUID().toString(),
                        "Owner's complete motorcycle spare parts shop recovery history"))
                    drive.publishAt(shop, Instant.ofEpochMilli(now - 40L * 86_400_000).toString())
                    repeat(7) { drive.publishAt(shop, Instant.ofEpochMilli(now - (7L - it) * 60_000).toString()) }
                } }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    lateinit var model: ShopViewModel
                    scenario.onActivity { model = ViewModelProvider(it)[ShopViewModel::class.java] }
                    compose.waitUntil(30_000) { model.state.value != null && model.driveStatus.value != null }
                    compose.onNodeWithText("More").performClick()
                    compose.onNodeWithText("Inspect backup storage").performScrollTo().assertIsDisplayed().assertIsEnabled()
                    // Substitute Google authorization/transport at the established external boundary.
                    scenario.onActivity { model.storageDriveAction(drive, DriveAction.InspectStorage) }
                    compose.waitUntil(30_000) { !model.driveBusy.value }
                    assertNull(model.driveError.value)
                    compose.onNodeWithText("8 complete restore points · 7 protected · 1 eligible older points").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Current folder file data:", substring = true).performScrollTo().assertIsDisplayed()
                    screenshot("storage-review-${mode.name}-$scale.png")
                    compose.onNodeWithText("Remove eligible older backups").performScrollTo().performClick()
                    compose.onNodeWithText("Delete reviewed history").assertIsEnabled()
                    screenshot("storage-confirm-${mode.name}-$scale.png")
                    if (scale == "2.0") {
                        compose.onNodeWithText("Cleanup pauses if identity, permissions or history are uncertain.").performScrollTo().assertIsDisplayed()
                        screenshot("storage-effects-${mode.name}-$scale.png")
                    }
                    scenario.recreate()
                    compose.onNodeWithText("Delete reviewed history").assertIsDisplayed()
                    compose.onNodeWithText("Cancel").performClick()
                    assertTrue(drive.deleted.isEmpty())
                    compose.onNodeWithText("Remove eligible older backups").performScrollTo().performClick()
                    compose.onNodeWithText("Delete reviewed history").assertIsEnabled()
                    val revision = model.state.value!!.revision
                    // Substitute the external authorization result for the separately reviewed confirmation action.
                    scenario.onActivity { model.storageDriveAction(drive, DriveAction.CleanupStorage) }
                    compose.waitUntil(60_000) { !model.driveBusy.value }
                    assertNull(model.driveError.value)
                    assertTrue(model.cleanupComplete.value)
                    assertEquals(revision, model.state.value!!.revision)
                    compose.onNodeWithText("Reviewed cleanup finished.", substring = true).performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("No older backup history is eligible for removal.").performScrollTo().assertIsDisplayed()
                    assertEquals(7, runBlocking { Shop.open(context).use { it.driveBackup.recoverable(drive).size } })
                }
            }
        } finally {
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(appearance) } }
        }
    }

    private fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
    private fun screenshot(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.getExternalFilesDir(null), name)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        shell("cp ${file.absolutePath} /data/local/tmp/${file.name}")
    }
}
