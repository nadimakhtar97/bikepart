package com.nadim.bikepart

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun staffSearchFilterSetAndClearThresholdsAcrossThemesAndLargeText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalScale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val token = System.nanoTime().toString()
        val title = "Workshop flanged stainless bolt with a very long description $token"
        val categoryName = "Search parts $token"
        val makeName = "Workshop make $token"
        val locationName = "Workshop shelf $token"
        lateinit var id: String
        val originalAppearance = runBlocking { Shop.open(context).use { shop ->
            val appearance = shop.snapshot().appearance
            val category = shop.createCategory(categoryName)
            val diameter = shop.createAttribute(category, "Diameter", AttributeType.Decimal, unit = "mm")
            val make = shop.createMake(makeName)
            val model = shop.createModel(make, "Touring")
            val location = shop.createLocation(locationName)
            id = shop.createProduct(title, category, "Zinc special $token", "5", Long.MAX_VALUE.toString(),
                attributes = mapOf(diameter to AttributeValue.Decimal("008.00")), modelIds = setOf(model), locationIds = setOf(location))
            shop.createProduct("Empty companion $token", category, "Zinc special $token", "0", "80")
            appearance
        } }
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun await(text: String) {
            try { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
            catch (failure: Throwable) { throw AssertionError("Waiting for $text\n" + compose.onAllNodes(isRoot()).onFirst().printToString(), failure) }
        }
        fun click(text: String) {
            // Tap the visible product title, rather than the centre of its tall
            // merged row, which can sit beneath the FAB at 200% text size.
            compose.onNodeWithText(text, useUnmergedTree = text == title).performScrollTo().performClick()
        }
        fun choose(label: String, choice: String) {
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            click("$label: Any")
            await("Find $label")
            compose.onNodeWithText("Find $label").performTextInput(choice)
            compose.onNode(hasText(choice) and hasAnyAncestor(isDialog()) and hasClickAction() and !hasSetTextAction()).performClick()
        }
        fun capture(name: String) {
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
            shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
        }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            for (scale in listOf(1f, 2f)) for (appearance in listOf(Appearance.Light, Appearance.Dark)) {
                scenario?.close()
                shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(appearance); it.setLowStockThreshold(id, "") } }
                scenario = ActivityScenario.launch(MainActivity::class.java)
                await("Stock"); compose.onNodeWithText("Stock").performClick()
                await("Search products")
                compose.onNodeWithText("Search products").performTextInput("$token 8.000")
                await("1 matching products")
                compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
                capture("search-row-${appearance.name.lowercase()}-$scale")
                click("More filters")
                choose("Category", categoryName)
                choose("Make", makeName)
                choose("Model", "$makeName · Touring")
                choose("Location", locationName)
                choose("Decimal field", "Diameter · mm · $categoryName")
                compose.onNodeWithText("Exact decimal value").performTextInput("1e3")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                androidx.test.espresso.Espresso.pressBack()
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Catalog filters").fetchSemanticsNodes().isEmpty() }
                await("Enter a plain exact decimal, without grouping or exponent")
                click("More filters")
                compose.onNodeWithText("Exact decimal value").performScrollTo().performTextReplacement("8.000")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                click("Show results")
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Catalog filters").fetchSemanticsNodes().isEmpty() }
                compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
                click(title)
                await("Low-stock threshold")
                compose.onNodeWithText("Low-stock threshold").performScrollTo().performTextInput("-1")
                click("Save threshold"); await("Low-stock threshold must be a nonnegative whole number")
                compose.onNodeWithText("Low-stock threshold").performScrollTo().performTextReplacement("5")
                compose.onNodeWithText("Save threshold").performScrollTo().assertHeightIsAtLeast(48.dp)
                capture("threshold-keyboard-${appearance.name.lowercase()}-$scale")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                click("Save threshold")
                await("Threshold: 5 pieces")
                compose.onNodeWithText("Low stock").performScrollTo().assertIsDisplayed()
                capture("threshold-saved-${appearance.name.lowercase()}-$scale")
                scenario.close()
                scenario = ActivityScenario.launch(MainActivity::class.java)
                await("Stock attention")
                compose.onNodeWithText("Low stock: ", substring = true).performScrollTo().performClick()
                await("Search products")
                compose.onNodeWithText("Search products").performScrollTo().performTextInput(token)
                await("1 matching products")
                compose.onAllNodesWithText("Out of stock").onFirst().performScrollTo().performClick()
                await("Empty companion $token")
                compose.onNodeWithText("Home").performClick()
                compose.onNodeWithText("Low stock: ", substring = true).performScrollTo().performClick()
                await("Search products")
                compose.onAllNodesWithText("Low stock").onFirst().assertIsSelected()
                compose.onNodeWithText("Search products").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
                compose.onNodeWithText("Search products").performScrollTo().performTextInput(token)
                await("1 matching products"); click(title)
                compose.onNodeWithText("Low-stock threshold").performScrollTo().performTextClearance()
                click("Save threshold"); await("Threshold: Not set")
                compose.waitUntil(15_000) { compose.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Back").performClick(); await("Search products")
                click("Clear search and filters")
                compose.onNodeWithText("Search products").performScrollTo().performTextInput(token)
                compose.onAllNodesWithText("Out of stock").onFirst().performScrollTo().performClick()
                await("1 matching products")
                compose.onNodeWithText("Empty companion $token").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Search products").performScrollTo().performTextReplacement("no-such-part-$token")
                await("No matching products")
                compose.onNodeWithText("No matching products").performScrollTo().assertIsDisplayed()
                capture("search-empty-${appearance.name.lowercase()}-$scale")
            }
            runBlocking { Shop.open(context).use { shop ->
                val product = shop.snapshot().products.single { it.id == id }
                assertNull(product.lowStockThreshold); assertEquals(5L, product.quantity)
            } }
        } finally {
            scenario?.close()
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(originalAppearance) } }
        }
    }
}
