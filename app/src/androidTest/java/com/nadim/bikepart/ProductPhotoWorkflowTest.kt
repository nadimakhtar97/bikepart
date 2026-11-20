package com.nadim.bikepart

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.nadim.bikepart.shop.Shop
import com.nadim.bikepart.shop.Appearance
import com.nadim.bikepart.shop.PhotoChange
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProductPhotoWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun undecodablePhotosAndRemovalStayUsableAcrossThemesAndFontScales() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalScale = android.provider.Settings.System.getFloat(context.contentResolver, android.provider.Settings.System.FONT_SCALE, 1f)
        val title = "Workshop replacement assembly with a long staff supplied identification ${System.nanoTime()}"
        val id = runBlocking { Shop.open(context).use { shop ->
            shop.createProduct(title, shop.snapshot().categories.first().id, "Photo decoding must not hide the product", "12", Long.MAX_VALUE.toString())
        } }
        var scenario: ActivityScenario<MainActivity>? = null
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun screenshot(name: String) {
            val file = File(context.getExternalFilesDir(null), "$name.png")
            file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        try {
            for (scale in listOf(1f, 2f)) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                scenario?.close()
                shell("settings put system font_scale $scale")
                val before = runBlocking { Shop.open(context).use { shop ->
                    shop.setAppearance(mode)
                    val product = shop.snapshot().products.single { it.id == id }
                    shop.editProduct(id, title, product.categoryId, product.description, photo = PhotoChange.Import { "undecodable".byteInputStream() })
                    shop.snapshot()
                } }
                scenario = ActivityScenario.launch(MainActivity::class.java)
                await("Stock")
                click("Stock")
                click(title)
                awaitDescription("Photo unavailable")
                compose.onNodeWithText("12 pieces").assertExists()
                compose.onNodeWithText("Selling price: ₹92,23,37,20,36,85,47,75,807").assertExists()
                screenshot("photo-fallback-${mode.name}-$scale")
                runBlocking { Shop.open(context).use { assertEquals(before, it.snapshot()) } }
                click("Edit details")
                compose.onNodeWithText("Replace photo").performScrollTo().assertHeightIsAtLeast(48.dp)
                compose.onNodeWithText("Remove photo").performScrollTo().assertHeightIsAtLeast(48.dp)
                screenshot("photo-controls-${mode.name}-$scale")
                click("Remove photo")
                awaitDescription("No product photo")
                compose.onNodeWithText("Choose photo").performScrollTo().assertHeightIsAtLeast(48.dp)
                screenshot("photo-empty-${mode.name}-$scale")
                click("Save product")
                await("Edit details")
                awaitDescription("No product photo")
            }
        } finally {
            scenario?.close()
            shell("settings put system font_scale $originalScale")
        }
    }

    @Test fun staffCanSelectReplaceRemoveAndReopenOwnedPhotos() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = "Photographed workshop part ${System.nanoTime()}"
        // Synthetic picker transport fixture, never bundled as shop content.
        val source = File(context.cacheDir, "$title.png")
        val replacement = File(context.cacheDir, "$title-replacement.png")
        val uri = Uri.fromFile(source)
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.eraseColor(android.graphics.Color.DKGRAY)
        replacement.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        Intents.init()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            Intents.intending(hasAction(MediaStore.ACTION_PICK_IMAGES)).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("Add product")
            click("Add product")
            compose.onNodeWithText("Title (required)").performTextInput(title)
            compose.onNodeWithText("Bolts", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithText("Selling price (whole rupees)").performScrollTo().performTextInput("80")
            click("Choose photo")
            click("Choose from gallery")
            awaitDescription("Product photo")
            scenario.recreate()
            click("Save product")
            await("Edit details")
            val first = runBlocking { Shop.open(context).use { it.snapshot().products.single { it.title == title }.photo!! } }
            assertTrue(source.delete())
            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("Stock")
            click("Stock")
            click(title)
            awaitDescription("Product photo")
            click("Edit details")
            Intents.intending(hasAction(MediaStore.ACTION_PICK_IMAGES)).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(replacement))))
            click("Replace photo")
            click("Choose from gallery")
            awaitDescription("Product photo")
            click("Save product")
            await("Edit details")
            runBlocking { Shop.open(context).use { shop -> assertNotEquals(first, shop.snapshot().products.single { it.title == title }.photo) } }
            assertTrue(replacement.delete())
            click("Edit details")
            click("Remove photo")
            click("Save product")
            await("Edit details")
            awaitDescription("No product photo")
            runBlocking { Shop.open(context).use { shop ->
                assertNull(shop.snapshot().products.single { it.title == title }.photo)
                assertNotNull(shop.readPhoto(first))
            } }
        } finally {
            scenario?.close()
            Intents.release()
            source.delete()
            replacement.delete()
        }
    }

    @Test fun staffCanTakePhotoOnTheSpotForProduct() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = "Camera captured workshop part ${System.nanoTime()}"
        Intents.init()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            Intents.intending(hasAction(MediaStore.ACTION_IMAGE_CAPTURE)).respondWith(ActivityResult(Activity.RESULT_OK, Intent()))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("Add product")
            click("Add product")
            compose.onNodeWithText("Title (required)").performTextInput(title)
            compose.onNodeWithText("Bolts", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithText("Selling price (whole rupees)").performScrollTo().performTextInput("120")
            click("Choose photo")
            click("Take photo")
            awaitDescription("Product photo")
            scenario.recreate()
            click("Save product")
            await("Edit details")
            runBlocking { Shop.open(context).use { shop ->
                val product = shop.snapshot().products.single { it.title == title }
                assertNotNull(product.photo)
            } }
        } finally {
            scenario?.close()
            Intents.release()
        }
    }

    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (text !in listOf("Stock", "Home", "More", "Back")) node.performScrollTo()
        node.performClick()
    }
    private fun await(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun awaitDescription(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty() } }
}
