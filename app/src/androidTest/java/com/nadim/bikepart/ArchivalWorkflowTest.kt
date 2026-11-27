package com.nadim.bikepart

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.provider.Settings
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArchivalWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun awaitSearch(label: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
    }
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun await(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String) {
        val matcher = hasText(text) and hasClickAction() and !hasSetTextAction()
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNode(matcher)
        if (text in listOf("Stock", "More", "Back")) node.performClick() else node.performScrollTo().performClick()
    }

    @Test fun archivalRemainsReadableAcrossThemesAndLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalScale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val originalAppearance = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        val title = "Stainless motorcycle flanged bolt with an exceptionally long archived title ${System.nanoTime()}"
        val product = runBlocking { Shop.open(context).use { shop ->
            shop.createProduct(title, shop.snapshot().categories.first().id, "Retained archived description", "0", Long.MAX_VALUE.toString())
        } }
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun capture(name: String) {
            compose.waitForIdle(); android.os.SystemClock.sleep(300); instrumentation.waitForIdleSync()
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
            shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
        }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { Shop.open(context).use { it.archiveProduct(product) } }
            for (scale in listOf(1f, 2f)) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                scenario?.close(); shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                scenario = ActivityScenario.launch(MainActivity::class.java)
                await("More"); click("More"); click("Archived products")
                awaitSearch("Search archived products")
                compose.onNodeWithContentDescription("Search archived products").performTextReplacement("No archived match ${System.nanoTime()}")
                compose.onNodeWithText("No matching archived products").performScrollTo().assertIsDisplayed()
                capture("archive-empty-${mode.name}-$scale")
                compose.onNodeWithContentDescription("Search archived products").performTextReplacement(title)
                compose.onNode(hasText(title) and !hasSetTextAction()).performScrollTo().assertIsDisplayed()
                capture("archive-list-${mode.name}-$scale")
                click(title); await("Archived product")
                capture("archive-detail-${mode.name}-$scale")
                compose.onNodeWithText("Selling price: ₹92,23,37,20,36,85,47,75,807").performScrollTo().assertIsDisplayed()
                capture("archive-price-${mode.name}-$scale")
                compose.onNodeWithText("Retained archived description").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("SKU:", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Edit details").assertDoesNotExist()
                click("Preview product labels"); await("Label copies")
                click("Back"); await("Archived product")
            }
        } finally {
            scenario?.close(); shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(originalAppearance) } }
        }
    }

    @Test fun staffSeeLiveBlockersAndReopenArchivedDetailsAndHistory() {
        val title = "Archival bolt ${System.nanoTime()}"
        val product = runBlocking { Shop.open(context).use { shop ->
            shop.createProduct(title, shop.snapshot().categories.first().id, "Original archival detail", "1", "80")
        } }
        var scenario: ActivityScenario<MainActivity>? = ActivityScenario.launch(MainActivity::class.java)
        try {
            await("Stock"); click("Stock")
            compose.onNodeWithContentDescription("Search products").performTextReplacement(title)
            await(title); click(title)
            click("Archive product")
            await("Cannot archive while stock remains. Stock must be zero.")
            val cart = runBlocking { Shop.open(context).use { shop ->
                shop.correctStock("archive-ui-$product", product, CorrectionKind.PhysicalCount, "0", "No pieces remain")
                val id = shop.createCart()
                shop.addCartProduct(id, 0, product, "1")
                id
            } }
            click("Archive product")
            await("Cannot archive while an unfinished cart contains this product. Remove its line or discard the cart first.")
            runBlocking { Shop.open(context).use { it.discardCart(cart, 1) } }
            click("Archive product"); await("Archived product")
            compose.onNodeWithText("Edit details").assertDoesNotExist()
            compose.onNodeWithText("Archive product").assertDoesNotExist()
            compose.onNodeWithText("Original archival detail").performScrollTo().assertIsDisplayed()
            scenario!!.recreate(); await("Archived product")
            click("Back"); click("Back"); click("Stock")
            compose.onNodeWithText(title).assertDoesNotExist()
            scenario!!.close(); scenario = ActivityScenario.launch(MainActivity::class.java)
            await("More"); click("More"); click("Archived products"); await(title); click(title)
            await("Archived product")
            compose.onNodeWithText("No pieces remain", substring = true).performScrollTo().assertIsDisplayed()
            runBlocking { Shop.open(context).use { assertTrue(it.snapshot().products.single { row -> row.id == product }.archived) } }
        } finally { scenario?.close() }
    }
}
