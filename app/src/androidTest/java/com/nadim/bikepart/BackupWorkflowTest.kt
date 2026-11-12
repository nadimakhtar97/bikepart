package com.nadim.bikepart

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun priorCompletePointCurrentCaptureAndNewerEditsRemainVisibleDuringUpload() = runBlocking {
        Shop.open(context).use { shop ->
            val drive = MemoryDrive()
            shop.driveBackup.connect(DriveTarget("coverage@example.com", "coverage-${java.util.UUID.randomUUID()}", "Coverage"))
            shop.driveBackup.publish(drive)
            val previous = shop.driveBackup.status().coveredRevision!!
            val appearance = shop.snapshot().appearance
            shop.setAppearance(if (appearance == Appearance.Dark) Appearance.Light else Appearance.Dark)
            val current = shop.snapshot().revision
            val paused = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            drive.beforeContent = { paused.complete(Unit); release.await() }
            val upload = async { shop.driveBackup.publish(drive) }
            try {
                kotlinx.coroutines.withTimeout(10_000) { paused.await() }
                shop.setAppearance(appearance)
                ActivityScenario.launch(MainActivity::class.java).use {
                    compose.waitUntil(30_000) { compose.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("More").performClick()
                    compose.waitUntil(30_000) { compose.onAllNodesWithText("Verified backup · revision $previous", substring = true).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("Verified backup · revision $previous", substring = true).performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("revision $current · captured", substring = true).performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Newer shop changes are waiting for another backup.").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Back up now").performScrollTo().assertIsEnabled()
                }
            } finally { release.complete(Unit) }
            upload.await()
            assertEquals(current, shop.driveBackup.status().coveredRevision)
            assertTrue(shop.snapshot().revision > current)
        }
    }

    @Test fun captureFailureKeepsPriorSuccessAndControlsRemainUsableAcrossThemesAndLargeText() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText().trim()
        }
        val originalScale = shell("settings get system font_scale").ifBlank { "1.0" }
        val original = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        val bytes = "workflow-photo-${System.nanoTime()}".toByteArray()
        val product = runBlocking { Shop.open(context).use { shop ->
            val id = shop.createProduct("Backup workflow ${System.nanoTime()}", shop.snapshot().categories.first().id, "", "0", "0", photo = PhotoChange.Import { bytes.inputStream() })
            shop.captureLocalBackup()
            shop.snapshot().products.single { it.id == id }
        } }
        val dbHash = java.security.MessageDigest.getInstance("SHA-256").digest("shop.db".toByteArray()).joinToString("") { "%02x".format(it) }
        val photo = java.io.File(context.filesDir, "product-photos/$dbHash/${product.photo!!.sha256}")
        try {
            photo.delete()
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(30_000) { compose.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("More").performClick()
                compose.waitUntil(30_000) { compose.onAllNodesWithText("Local capture complete", substring = true).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Capture local backup").performScrollTo().performClick()
                compose.waitUntil(30_000) { compose.onAllNodesWithText("Could not capture local backup.", substring = true).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Local capture complete", substring = true).performScrollTo().assertExists()
                compose.onNodeWithText("Save a copy…").performScrollTo().assertIsEnabled()
                photo.writeBytes(bytes)
            }
            for (mode in listOf(Appearance.Light, Appearance.Dark)) for (scale in listOf("1.0", "2.0")) {
                shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                ActivityScenario.launch(MainActivity::class.java).use {
                    compose.waitUntil(30_000) { compose.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("More").performClick()
                    compose.onNodeWithText("Capture local backup").performScrollTo().performClick()
                    compose.waitUntil(30_000) { compose.onAllNodesWithText("Local capture complete", substring = true).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("Capture local backup").performScrollTo().assertIsDisplayed().assertIsEnabled()
                    compose.onNodeWithText("Save a copy…").performScrollTo().assertIsDisplayed().assertIsEnabled()
                    compose.waitForIdle()
                    compose.onNodeWithText("Connect or repair Drive").performScrollTo().assertIsDisplayed().assertIsEnabled()
                    compose.onNodeWithText("Back up now").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Allow mobile data for automatic backups").performScrollTo().assertIsDisplayed()
                    val name = "backup-${mode.name}-$scale.png"
                    val file = java.io.File(context.getExternalFilesDir(null), name)
                    instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                    }
                    shell("cp ${file.absolutePath} /data/local/tmp/$name")
                }
            }
        } finally {
            photo.writeBytes(bytes)
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }

    @Test fun selectedDriveConnectionReopensWithPendingCoverageAndLabelledControls() {
        val target = DriveTarget("owner-with-a-long-name@example.com", "owner-selected-folder", "Shop recovery copies with a long folder name")
        runBlocking { Shop.open(context).use { it.driveBackup.connect(target) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(30_000) { compose.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Connect or repair Drive").performScrollTo().assertIsEnabled()
            compose.onNodeWithText("Back up now").performScrollTo().assertIsEnabled()
            compose.onNodeWithText("Allow mobile data for automatic backups").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Use Wi-Fi only").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Use Wi-Fi only").performScrollTo().performClick()
            compose.onNodeWithText("No verified Drive backup for this connection.").performScrollTo().assertIsDisplayed()
            scenario.recreate()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Account: ${target.account}", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Account: ${target.account}", substring = true).performScrollTo().assertIsDisplayed()
        }
    }
    @Test fun staffCaptureAndReopenLocalSuccessWithoutChangingBusinessRevision() {
        val before = runBlocking { Shop.open(context).use { it.snapshot() } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Capture local backup").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Local capture complete", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Save a copy…").performScrollTo().assertIsEnabled()
            assertEquals(before, runBlocking { Shop.open(context).use { it.snapshot() } })
            scenario.recreate()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Local capture complete", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Saved on this phone. This does not confirm a Drive backup.").performScrollTo().assertIsDisplayed()
        }
    }
}
