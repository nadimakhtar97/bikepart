package com.nadim.bikepart

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LabelWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun snapshot() = runBlocking { Shop.open(context).use { it.snapshot() } }
    private fun await(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction() and !hasSetTextAction())
        if (text !in listOf("Stock", "Back")) node.performScrollTo()
        node.performClick()
    }
    private fun open(title: String) {
        await("Stock"); click("Stock")
        compose.onNodeWithText("Search products").performTextReplacement(title)
        click(title); click("Preview product labels"); await("Label copies")
    }

    @Test fun staffChooseProfilesValidateCopiesAndRepreviewStableIdentityAfterCatalogEdits() {
        val title = "Label bolt ${System.nanoTime()}"
        val id = runBlocking { Shop.open(context).use { shop ->
            shop.createProduct(title, shop.snapshot().categories.first().id, "", "12", "80")
        } }
        val initial = snapshot()
        val payload = ItemQr.product(initial.datasetId, id)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(title)
            for (invalid in listOf("0", "-1", "1.5", "", "9223372036854775808")) {
                compose.onNodeWithText("Label copies").performScrollTo().performTextReplacement(invalid)
                click("Preview label")
                compose.onNodeWithText("Label preview").assertDoesNotExist()
                assertEquals(initial, snapshot())
            }
            compose.onNodeWithText("Label copies").performScrollTo().performTextReplacement("3")
            click("Package · 50 × 30 mm"); click("Preview label")
            compose.onNodeWithContentDescription("Product label QR: $payload").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("SKU: ${initial.products.single { it.id == id }.sku}").performScrollTo().assertIsDisplayed()
            assertEquals(initial, snapshot())
            click("Shared bin · 70 × 40 mm"); click("Preview label")
            compose.onNodeWithText("Shared bin · 70 × 40 mm · 3 copies").performScrollTo().assertIsDisplayed()
            click("Save label preferences")
            compose.waitUntil(15_000) { snapshot().labelPreferences == LabelPreferences(LabelMedia.SharedBin, 3) }
            val saved = snapshot()
            assertEquals(initial.copy(revision = initial.revision + if (initial.labelPreferences == saved.labelPreferences) 0 else 1, labelPreferences = saved.labelPreferences), saved)
            scenario.recreate(); await("Label copies")
            compose.onNodeWithText("Label copies").assertTextContains("3")
            compose.onNodeWithText("Shared bin · 70 × 40 mm").assertIsSelected()
            val renamed = "$title renamed with a much longer product title"
            click("Back"); click("Edit details")
            compose.onNodeWithText("Title (required)").performScrollTo().performTextReplacement(renamed)
            click("Save product"); await(renamed)
            click("Preview product labels"); click("Preview label")
            compose.onNodeWithContentDescription("Product label QR: $payload").performScrollTo().assertIsDisplayed()
            assertEquals(12L, snapshot().products.single { it.id == id }.quantity)
        }
        val renamed = snapshot().products.single { it.id == id }.title
        ActivityScenario.launch(MainActivity::class.java).use {
            open(renamed)
            compose.onNodeWithText("Label copies").assertTextContains("3")
            click("Preview label")
            compose.onNodeWithContentDescription("Product label QR: $payload").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun bothProfilesRemainReadableInLightDarkAndDoubleSystemText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalScale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val original = snapshot().appearance
        val title = "Stainless motorcycle flanged mounting bolt with an exceptionally long replacement product title ${System.nanoTime()}"
        val id = runBlocking { Shop.open(context).use { shop -> shop.createProduct(title, shop.snapshot().categories.first().id, "", "12", "80") } }
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        try {
            for (scale in listOf(1f, 2f)) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                ActivityScenario.launch(MainActivity::class.java).use {
                    open(title)
                    for (media in LabelMedia.entries) {
                        click(media.label); click("Preview label")
                        val payload = ItemQr.product(snapshot().datasetId, id)
                        compose.onNodeWithContentDescription("Product label QR: $payload").performScrollTo().assertIsDisplayed()
                        compose.waitForIdle(); android.os.SystemClock.sleep(300)
                        val name = "label-${media.name}-${mode.name}-$scale"
                        val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
                        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
                        shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
                        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
                        compose.onNodeWithText("SKU: ${snapshot().products.single { it.id == id }.sku}").performScrollTo().assertIsDisplayed()
                    }
                }
            }
        } finally {
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }
}
