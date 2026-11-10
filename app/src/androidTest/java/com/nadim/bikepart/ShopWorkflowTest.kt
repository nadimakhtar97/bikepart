package com.nadim.bikepart

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.provider.Settings

class ShopWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    @Before fun launchApp() { scenario = ActivityScenario.launch(MainActivity::class.java) }
    @After fun closeApp() { scenario.close() }

    @Test fun evolutionScreensRemainReadableInBothThemesAtNormalAndDoubleFontScale() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val title = "Evolution visual ${System.nanoTime()}"
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun screenshot(name: String) {
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            file.outputStream().buffered().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
        }
        scenario.close()
        kotlinx.coroutines.runBlocking {
            com.nadim.bikepart.shop.Shop.open(context).use { shop ->
                val source = shop.createCategory("Visual source ${System.nanoTime()}")
                val target = shop.createCategory("Visual target ${System.nanoTime()}")
                val old = shop.createAttribute(source, "Original diameter", com.nadim.bikepart.shop.AttributeType.Decimal, unit = "mm")
                val id = shop.createProduct(title, source, "", "12", "9223372036854775807", attributes = mapOf(old to com.nadim.bikepart.shop.AttributeValue.Decimal("008.00")))
                shop.editProduct(id, title, target, "")
                shop.createCustomAttribute(id, "Workshop specification for unusual replacement parts", com.nadim.bikepart.shop.AttributeType.Text)
            }
        }
        try {
            for (scale in listOf("1.0", "2.0")) for (mode in com.nadim.bikepart.shop.Appearance.entries.filter { it != com.nadim.bikepart.shop.Appearance.System }) {
                shell("settings put system font_scale $scale")
                kotlinx.coroutines.runBlocking { com.nadim.bikepart.shop.Shop.open(context).use { it.setAppearance(mode) } }
                scenario = ActivityScenario.launch(MainActivity::class.java)
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Stock").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Stock").performClick()
                compose.onNodeWithText(title).performScrollTo().performClick()
                compose.onNodeWithText("Historical attributes").performScrollTo().assertExists()
                compose.onNodeWithText("Original diameter: 008.00 mm").performScrollTo().assertExists()
                screenshot("evolution-history-${mode.name.lowercase()}-$scale")
                compose.onNodeWithText("Manage fields").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                compose.onNodeWithText("Manage custom Workshop specification for unusual replacement parts").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                compose.onNodeWithText("Category field name").performScrollTo().performTextReplacement("Workshop specification for unusual replacement parts")
                compose.onNodeWithText("Promote to category").performScrollTo().assertHeightIsAtLeast(48.dp)
                screenshot("evolution-manage-${mode.name.lowercase()}-$scale")
                scenario.close()
            }
        } finally {
            shell("settings put system font_scale $original")
        }
    }

    @Test fun staffCanPromoteCustomFieldsResolveCollisionsAndCompleteRequiredEdits() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = "Evolution part ${System.nanoTime()}"
        fun activate(label: String) {
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(label).performScrollTo().assertIsEnabled()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        }
        fun awaitLabel(label: String) {
            try {
                compose.waitUntil(10_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
            } catch (failure: Exception) {
                throw AssertionError(compose.onRoot().printToString(), failure)
            }
        }
        scenario.close()
        kotlinx.coroutines.runBlocking {
            com.nadim.bikepart.shop.Shop.open(context).use { shop ->
                val category = shop.createCategory("Evolution ${System.nanoTime()}")
                shop.createProduct(title, category, "", "12", "80")
                shop.createAttribute(category, "Finish", com.nadim.bikepart.shop.AttributeType.Text)
            }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Stock").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Stock").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithText("Manage fields").performScrollTo().performClick()
        compose.onNodeWithText("Add product custom field").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Field name").performScrollTo().performTextInput("Finish")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Create field") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        activate("Create field")
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Create field").fetchSemanticsNodes().isEmpty() }
        } catch (failure: AssertionError) {
            throw AssertionError(compose.onRoot().printToString(), failure)
        }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Edit details").performScrollTo().performClick()
        compose.onNodeWithText("Finish (optional) · product only").performScrollTo().performTextInput("Special")
        activate("Save product")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Finish: Special").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Manage fields").performScrollTo().performClick()
        compose.onNodeWithText("Manage custom Finish").performScrollTo().performClick()
        compose.onNodeWithText("Replace field type").performScrollTo().performClick()
        compose.onNodeWithText("Promote to category").assertDoesNotExist()
        compose.onNodeWithText("Replace field type").performScrollTo().performClick()
        activate("Promote to category")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("That category field already exists.", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Category field name").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Error))
        compose.onNodeWithText("Category field name").performScrollTo().performTextReplacement("Special finish")
        activate("Promote to category")
        awaitLabel("Manage Special finish")
        activate("Make required")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Make optional").fetchSemanticsNodes().isNotEmpty() }
        activate("Retire field")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Category field · Text · retired").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Manage Finish").performScrollTo().performClick()
        activate("Make required")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Make optional").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Special finish (retired): Special").performScrollTo().assertExists()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Catalog incomplete", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("12 pieces").performScrollTo().assertExists()
        compose.onNodeWithText("Edit details").performScrollTo().performClick()
        activate("Save product")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Finish: Required").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Finish (required)").performScrollTo().performTextInput("Zinc")
        activate("Save product")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Finish: Zinc").fetchSemanticsNodes().isNotEmpty() }
        scenario.recreate()
        compose.onNodeWithText("Special finish (retired): Special").performScrollTo().assertExists()
    }

    @Test fun labelledControlsAndValidationRemainUsableInBothThemesAtDoubleTextSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun screenshot(name: String) {
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            file.outputStream().buffered().use { output ->
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
        }
        try {
            shell("settings put system font_scale 2.0")
            scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty() }
            for (mode in listOf("Light", "Dark")) {
                compose.onNodeWithText("More").performClick()
                compose.onNodeWithText(mode).performClick()
                compose.waitUntil(10_000) { compose.onAllNodes(hasText(mode) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(mode).assertIsSelected()
                screenshot("more-${mode.lowercase()}-200")
                compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
                compose.onNodeWithText("Add product").assertHeightIsAtLeast(48.dp).performClick()
                compose.onNodeWithText("Title (required)").assertExists()
                screenshot("form-${mode.lowercase()}-200")
                compose.onNodeWithText("Save product").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Enter a product title").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Title (required)").performScrollTo().performTextInput("Validation $mode")
                compose.onNodeWithText("Bolts", useUnmergedTree = true).performScrollTo().performClick()
                compose.onNodeWithText("Selling price (whole rupees)").performScrollTo().performTextInput("1.5")
                compose.onNodeWithText("Save product").performScrollTo().performClick()
                try { compose.waitUntil(10_000) { compose.onAllNodesWithText("Selling price must be a nonnegative whole number").fetchSemanticsNodes().isNotEmpty() } } catch (failure: Exception) { compose.onRoot().printToLog("ValidationFailure"); throw failure }
                compose.onNodeWithText("Back").performClick()
                compose.onNodeWithText("Carts").performClick()
                compose.onNodeWithText("No unfinished carts").assertExists()
            }
            compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("System").performClick()
        } finally { shell("settings put system font_scale $original") }
    }

    @Test fun typedFieldsRemainReadableInBothThemesAtNormalAndDoubleTextSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val category = "Visual fields " + System.nanoTime()
        kotlinx.coroutines.runBlocking {
            com.nadim.bikepart.shop.Shop.open(context).use { shop ->
                val id = shop.createCategory(category)
                shop.createAttribute(id, "Bottle volume", com.nadim.bikepart.shop.AttributeType.Decimal, unit = "ml", required = true)
                shop.createAttribute(id, "Viscosity grade", com.nadim.bikepart.shop.AttributeType.Text)
                val choice = shop.createAttribute(id, "Compatible positions", com.nadim.bikepart.shop.AttributeType.MultipleChoice)
                shop.createOption(choice, "Front and rear replacement assemblies with extended workshop description")
            }
        }
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        try {
            for (scale in listOf(1f, 2f)) for (mode in listOf(com.nadim.bikepart.shop.Appearance.Light, com.nadim.bikepart.shop.Appearance.Dark)) {
                scenario.close()
                shell("settings put system font_scale " + scale)
                kotlinx.coroutines.runBlocking { com.nadim.bikepart.shop.Shop.open(context).use { it.setAppearance(mode) } }
                scenario = ActivityScenario.launch(MainActivity::class.java)
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Add product").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Add product").performClick()
                compose.onNodeWithText("Search or add category").performTextInput(category.dropLast(1))
                compose.onNodeWithText(category, useUnmergedTree = true).performScrollTo().performClick()
                compose.onNodeWithText("Bottle volume (ml) (required)").performScrollTo().performTextInput("0.123456789012")
                compose.onNodeWithText("Viscosity grade (optional)").performScrollTo().assertIsDisplayed()
                val file = java.io.File(context.getExternalFilesDir(null), "typed-fields-" + mode.name.lowercase() + "-" + scale + ".png")
                instrumentation.uiAutomation.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
                compose.onNodeWithText("Front and rear replacement assemblies with extended workshop description").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                compose.onNodeWithText("Save product").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Enter a product title").fetchSemanticsNodes().isNotEmpty() }
            }
        } finally { shell("settings put system font_scale " + original) }
    }

    @Test fun allTypedControlsAndInlineOptionsPersistThroughCancellationAndEditing() {
        val category = "Typed parts " + System.nanoTime()
        fun field(name: String, type: String) {
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Add category field") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Add category field").performScrollTo().performClick()
            compose.onNodeWithText("Field name").performScrollTo().performTextInput(name)
            compose.onNodeWithText(type, useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithText("Create field").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Create field").fetchSemanticsNodes().isEmpty() }
        }
        fun option(field: String, name: String) {
            compose.onNodeWithText("Search or add " + field + " option").performScrollTo().performTextInput(name)
            compose.onNodeWithText("Add option").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(name) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Add product").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add product").performClick()
        compose.onNodeWithText("Search or add category").performTextInput(category)
        compose.onNodeWithText("Add category").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add category field").fetchSemanticsNodes().isNotEmpty() }
        field("Grade", "Text")
        field("Size", "Decimal number")
        field("Shade", "Single choice")
        option("Shade", "Black")
        field("Treatments", "Multiple choice")
        option("Treatments", "Zinc")
        option("Treatments", "Oil")
        compose.onNodeWithText("Back").performClick()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Add product").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add product").performClick()
        compose.onNodeWithText("Title (required)").performTextInput(category + " product")
        compose.onNodeWithText("Search or add category").performTextInput(category.dropLast(1))
        compose.onNodeWithText(category, useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Grade (optional)").performScrollTo().performTextInput("SAE 10W-40")
        compose.onNodeWithText("Size (optional)").performScrollTo().performTextInput("10.2500")
        for (choice in listOf("Black", "Zinc", "Oil")) compose.onNodeWithText(choice, useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Selling price (whole rupees)").performScrollTo().performTextInput("80")
        scenario.recreate()
        compose.onNodeWithText("Save product").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Grade: SAE 10W-40").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Size: 10.2500").performScrollTo().assertExists()
        compose.onNodeWithText("Shade: Black").performScrollTo().assertExists()
        compose.onNodeWithText("Treatments: Oil, Zinc").performScrollTo().assertExists()
        compose.onNodeWithText("Edit details").performScrollTo().performClick()
        compose.onNodeWithText("Size (optional)").performScrollTo().performTextReplacement("2.500")
        compose.onNodeWithText("Save product").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Size: 2.500").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Grade: SAE 10W-40").assertExists()
    }

    @Test fun inlineCategoryAndRequiredDecimalSurviveCancellingTheirProductDraft() {
        val category = "Hardware " + System.nanoTime()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Add product").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add product").performClick()
        compose.onNodeWithText("Search or add category").performTextInput(category)
        compose.onNodeWithText("Add category").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(category).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Add category field") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add category field").performScrollTo().performClick()
        compose.onNodeWithText("Field name").performScrollTo().performTextInput("Diameter")
        compose.onNodeWithText("Decimal number", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Unit label").performScrollTo().performTextInput("mm")
        compose.onNodeWithText("Required field").performScrollTo().performClick()
        compose.onNodeWithText("Create field").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Diameter (mm) (required)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Back").performClick()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Add product").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add product").performClick()
        compose.onNodeWithText("Title (required)").performTextInput("Precision bolt")
        compose.onNodeWithText("Search or add category").performTextInput(category.dropLast(1))
        compose.onNodeWithText(category, useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Selling price (whole rupees)").performScrollTo().performTextInput("25")
        compose.onNodeWithText("Save product").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Diameter: Required").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Diameter (mm) (required)").performScrollTo().performTextInput("0.123456789012")
        compose.onNodeWithText("Save product").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Diameter: 0.123456789012 mm").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("0 pieces").assertExists()
    }

    @Test fun createEditAndReopenProductThroughTheApp() {
        val title = "Stainless steel hex bolt M8 x 30 for the workshop's replacement fastener bins ${System.nanoTime()}"
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add product").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add product").performClick()
        compose.onNodeWithText("Title (required)").performTextInput(title)
        compose.onNodeWithText("Bolts", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Opening stock (pieces)").performScrollTo().performTextReplacement("12")
        compose.onNodeWithText("Selling price (whole rupees)").performScrollTo().performTextInput("9223372036854775807")
        compose.onNodeWithText("Save product").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("12 pieces").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Selling price: ₹92,23,37,20,36,85,47,75,807").performScrollTo().assertIsDisplayed()
        val sku = compose.onAllNodes(hasText("SKU:", substring = true)).fetchSemanticsNodes().single().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        compose.onNodeWithText("Edit details").performScrollTo().performClick()
        compose.onNodeWithText("Description").performScrollTo().performTextInput("Zinc finish")
        compose.onNodeWithText("Save product").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Zinc finish").fetchSemanticsNodes().isNotEmpty() }
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Stock").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Stock").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithText(sku).assertExists()
        compose.onNodeWithText("Zinc finish").assertExists()
        compose.onNodeWithText("12 pieces").assertExists()
        compose.onNodeWithText("Selling price: ₹92,23,37,20,36,85,47,75,807").performScrollTo().assertIsDisplayed()
    }
}
