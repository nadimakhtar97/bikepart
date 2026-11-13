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
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhoneReplacementWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun ownerReviewsRestoredIdentityAndRetirementAcrossThemesAndLargeText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }
        fun screenshot(name: String) {
            val file = File(context.getExternalFilesDir(null), name)
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
            shell("cp ${file.absolutePath} /data/local/tmp/${file.name}")
        }
        val scaleBefore = shell("settings get system font_scale").ifBlank { "1.0" }
        val appearance = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        try {
            for (mode in listOf(Appearance.Light, Appearance.Dark)) for (scale in listOf("1.0", "2.0")) {
                shell("settings put system font_scale $scale")
                val drive = ReplacementDrive()
                runBlocking { Shop.open(context).use { shop ->
                    shop.setAppearance(mode)
                    shop.driveBackup.connect(DriveTarget("owner@example.com", UUID.randomUUID().toString(), "Owner's replacement backups"))
                    shop.driveBackup.publish(drive)
                    shop.driveBackup.validateRestore(drive, shop.driveBackup.restorePoints(drive).single()).use { shop.activateRestore(it, true) }
                } }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    lateinit var model: ShopViewModel
                    scenario.onActivity { model = ViewModelProvider(it)[ShopViewModel::class.java] }
                    compose.waitUntil(30_000) { model.state.value != null && model.driveStatus.value != null }
                    compose.onNodeWithText("More").performClick()
                    compose.onNodeWithText("Review replacement phone").performScrollTo().assertIsDisplayed().assertIsEnabled()
                    // Substitute only the external Google authorization/transport boundary.
                    scenario.onActivity { model.phoneDriveAction(drive, DriveAction.ReviewPhone) }
                    compose.waitUntil(30_000) { model.phoneReplacement.value != null && !model.driveBusy.value }
                    compose.onNodeWithText("Activate for account:", substring = true).performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Restored capture:", substring = true).performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Activate replacement phone").performScrollTo().assertIsNotEnabled()
                    compose.onNodeWithText("I have retired the old installation").performScrollTo().performClick()
                    compose.onNodeWithText("Activate replacement phone").performScrollTo().assertIsEnabled()
                    screenshot("phone-review-${mode.name}-$scale.png")
                    scenario.recreate()
                    compose.onNodeWithText("Activate replacement phone").performScrollTo().assertIsEnabled()
                    scenario.onActivity { model.phoneDriveAction(drive, DriveAction.ActivatePhone) }
                    compose.waitUntil(30_000) { !model.driveBusy.value && model.phoneReplacement.value == null }
                    assertNull(model.driveError.value)
                    compose.onNodeWithText("This phone is the confirmed active backup writer.").performScrollTo().assertIsDisplayed()
                    screenshot("phone-active-${mode.name}-$scale.png")
                    assertNull(model.driveStatus.value!!.coveredRevision)
                    scenario.onActivity { model.phoneDriveAction(drive, DriveAction.CheckPhone) }
                    compose.waitUntil(30_000) { !model.driveBusy.value }
                    compose.onNodeWithText("Retire this installation").performScrollTo().performClick()
                    compose.onNodeWithText("Retire this phone now").assertIsDisplayed()
                    compose.onNodeWithText("Cancel").performClick()
                    assertEquals(ActivePhoneState.Active, model.driveStatus.value!!.phoneState)
                }
            }
        } finally {
            shell("settings put system font_scale $scaleBefore")
            runBlocking { Shop.open(context).use { it.setAppearance(appearance) } }
        }
    }
}
