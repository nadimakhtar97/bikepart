package com.nadim.bikepart

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TemplateWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun await(text: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) {
        val node = compose.onNode((hasText(text) or hasContentDescription(text)) and hasClickAction())
        if (text !in listOf("More", "Back")) node.performScrollTo()
        node.performClick()
    }
    private fun input(label: String, text: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(text) }
    private fun snapshot(): ShopSnapshot = runBlocking { Shop.open(ApplicationProvider.getApplicationContext<Context>()).use { it.snapshot() } }

    @Test fun templateScreensRemainUsableInLightDarkAndLargeText() {
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalScale = android.provider.Settings.System.getFloat(context.contentResolver, android.provider.Settings.System.FONT_SCALE, 1f)
        val originalAppearance = snapshot().appearance
        val suffix = System.nanoTime().toString()
        val title = "Workshop replacement assembly with a long descriptive template name $suffix"
        runBlocking { Shop.open(context).use { shop -> shop.saveTemplate(CatalogDraft(title, shop.snapshot().categories.first().id, "Staff supplied catalog details for workshop use")) } }
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun capture(name: String) {
            compose.waitForIdle()
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
            shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
        }
        try {
            for (scale in listOf(1f, 2f)) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                scenario.close()
                shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                scenario = ActivityScenario.launch(MainActivity::class.java)
                    await("More"); click("More"); click("Product templates")
                    compose.onNodeWithContentDescription("Search templates").performScrollTo().performTextReplacement("No matching template $suffix")
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    compose.onNodeWithText("No matching templates").assertExists()
                    capture("templates-empty-${mode.name}-$scale")
                    compose.onNodeWithContentDescription("Search templates").performScrollTo().performTextReplacement(suffix)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    compose.onNodeWithContentDescription("Use template: $title").performScrollTo().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
                    capture("templates-list-${mode.name}-$scale")
                    click("Open template: $title")
                    compose.onNodeWithText("Use template").performScrollTo()
                    capture("templates-detail-${mode.name}-$scale")
                    click("Use template")
                    input("Selling price (whole rupees)", "9223372036854775807")
                    compose.onNodeWithText("Selling price (whole rupees)").performClick()
                    capture("templates-keyboard-${mode.name}-$scale")
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    click("Save product")
                    await("Opening stock must be a nonnegative whole number")
                    capture("templates-validation-${mode.name}-$scale")
            }
        } finally {
            runCatching { shell("settings put system font_scale $originalScale") }
            runBlocking { Shop.open(context).use { it.setAppearance(originalAppearance) } }
            scenario.close()
        }
    }

    @Test fun manuallyCreatedTemplateCanBeFoundEditedUsedAndRetiredWithoutInventingStock() {
        val suffix = System.nanoTime().toString()
        val title = "Template $suffix"
        val changed = "Updated template $suffix"
        val productTitle = "Independent product $suffix"
        val before = snapshot()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await("More"); click("More"); click("Product templates"); click("Create template")
            input("Title (required)", title)
            click(before.categories.first().name)
            input("Description", "Workshop catalog description")
            click("Add custom field to draft")
            input("Field name", "Workshop code")
            click("Create field")
            input("Workshop code (optional) · copy only", "A-01")
            compose.onAllNodesWithText("Selling price (whole rupees)").assertCountEquals(0)
            compose.onAllNodesWithContentDescription("Search or add location").assertCountEquals(0)
            input("Custom field name", "")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            click("Save template"); await("Enter a custom field name")
            compose.onAllNodesWithText("Enter a custom field name", useUnmergedTree = true).onLast().assertIsDisplayed()
            input("Custom field name", "Workshop code")
            compose.onNodeWithText("Save template").performScrollTo()
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            click("Save template"); await("Edit template")
            assertEquals(before.products, snapshot().products)
            click("Back"); compose.onNodeWithContentDescription("Search templates").performScrollTo().performTextReplacement(suffix)
            click("Open template: $title"); click("Edit template")
            input("Title (required)", changed)
            input("Custom field name", "Local code")
            compose.onNodeWithText("Save template").performScrollTo()
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            click("Save template"); await("Edit template")
            val saved = snapshot()
            click("Use template"); await("Save product")
            scenario.recreate()
            compose.onNodeWithText("Title (required)").assertTextContains(changed)
            compose.onNodeWithText("Selling price (whole rupees)").performScrollTo().assert(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
            compose.onNodeWithText("Opening stock (pieces)").performScrollTo().assert(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
            click("Back")
            assertEquals(saved, snapshot())
            click("More"); click("Product templates"); compose.onNodeWithContentDescription("Search templates").performScrollTo().performTextReplacement(suffix)
            click("Use template: $changed"); input("Title (required)", productTitle)
            input("Opening stock (pieces)", "0"); input("Selling price (whole rupees)", "120")
            compose.onNodeWithText("Save product").performScrollTo()
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            click("Save product"); await("Edit details")
            val product = snapshot().products.single { it.title == productTitle }
            assertEquals(0L, product.quantity); assertEquals(120L, product.priceRupees)
            assertTrue(product.locationIds.isEmpty())
            val definition = snapshot().attributes.single { it.productId == product.id }
            assertEquals("Local code", definition.name)
            assertEquals(AttributeValue.Text("A-01"), product.attributes[definition.id])
            click("Copy catalog details"); await("Save product")
            input("Title (required)", "Copied product $suffix")
            input("Opening stock (pieces)", "1"); input("Selling price (whole rupees)", "85")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            click("Save product"); await("Edit details")
            val copied = snapshot().products.single { it.title == "Copied product $suffix" }
            assertEquals(1L, copied.quantity); assertEquals(85L, copied.priceRupees)
            assertNotEquals(product.sku, copied.sku)
            assertNotEquals(definition.id, snapshot().attributes.single { it.productId == copied.id }.id)
            click("Create template from product")
            input("Title (required)", "From product $suffix")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            click("Save template"); await("Edit template")
            val fromProduct = snapshot()
            assertEquals(copied, fromProduct.products.single { it.id == copied.id })
            click("Use template"); await("Save product"); click("Back")
            assertEquals(fromProduct, snapshot())
            assertEquals(product, snapshot().products.single { it.id == product.id })
            click("More"); click("Product templates"); compose.onNodeWithContentDescription("Search templates").performScrollTo().performTextReplacement(suffix)
            click("Open template: $changed"); click("Retire template"); await("Retired template · retained for reference")
            click("Back"); compose.onNodeWithContentDescription("Search templates").performScrollTo().performTextReplacement(suffix)
            compose.onAllNodesWithContentDescription("Use template: $changed").assertCountEquals(0)
            click("Retired templates"); click("Open template: $changed")
            compose.onNodeWithText("Workshop catalog description").assertExists()
            assertEquals(product, snapshot().products.single { it.id == product.id })
        }
    }
}
