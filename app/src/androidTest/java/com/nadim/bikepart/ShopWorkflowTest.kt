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
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Selling price must be a nonnegative whole number").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Back").performClick()
                compose.onNodeWithText("Carts").performClick()
                compose.onNodeWithText("No unfinished carts").assertExists()
            }
            compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("System").performClick()
        } finally { shell("settings put system font_scale $original") }
    }

    @Test fun createEditAndReopenProductThroughTheApp() {
        val title = "Bolt ${System.nanoTime()}"
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add product").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add product").performClick()
        compose.onNodeWithText("Title (required)").performTextInput(title)
        compose.onNodeWithText("Bolts", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Opening stock (pieces)").performScrollTo().performTextReplacement("12")
        compose.onNodeWithText("Selling price (whole rupees)").performScrollTo().performTextInput("120000")
        compose.onNodeWithText("Save product").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("12 pieces").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Selling price: ₹1,20,000").assertExists()
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
        compose.onNodeWithText("Selling price: ₹1,20,000").assertExists()
    }
}
