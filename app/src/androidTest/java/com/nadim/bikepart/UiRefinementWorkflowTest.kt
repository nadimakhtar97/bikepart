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

class UiRefinementWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun awaitSearch(label: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
    }
    private fun await(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "refinement-$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    private fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
    }

    @Test fun stockMenusScanAndDateLogRemainUsableAcrossAppearancesAndLargeText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalScale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val token = System.nanoTime().toString()
        val title = "Workshop brake assembly $token"
        lateinit var productId: String
        lateinit var sale: Sale
        val originalMode = runBlocking { Shop.open(context).use { shop ->
            val mode = shop.snapshot().appearance
            productId = shop.createProduct(title, shop.snapshot().categories.first().id, "", "5", "250")
            val cartId = shop.createCart()
            shop.saveCart(cartId, 0, "Log customer $token", mapOf(productId to "1"))
            sale = shop.checkout(cartId, shop.snapshot().carts.single { it.id == cartId }.editVersion, "250")
            mode
        } }
        try {
            for (scale in listOf(1f, 2f)) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    await("Stock"); compose.onNodeWithText("Stock").performClick()
                    awaitSearch("Search products")
                    compose.onNodeWithText("Add product").assertHeightIsAtLeast(48.dp).assertIsDisplayed()
                    compose.onNodeWithContentDescription("Search products").performTextInput(token)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    await("1 matching products")
                    capture("stock-${mode.name}-$scale")
                    compose.onNodeWithContentDescription("Actions for $title").performScrollTo().performClick()
                    await("Edit details")
                    capture("menu-${mode.name}-$scale")
                    compose.onNodeWithText("Edit details").performClick()
                    await("Title (required)")
                    compose.onNodeWithText("Back").performClick()
                    await("Product details")
                    compose.onNodeWithText("Back").performClick()
                    awaitSearch("Search products")
                    click("More filters"); await("Catalog filters")
                    capture("filters-${mode.name}-$scale")
                    click("Show results"); awaitSearch("Search products")
                    compose.onNodeWithContentDescription("Scan stock").performScrollTo().performClick()
                    await("Scan shared label")
                    compose.onNodeWithText("Back").performClick()
                    awaitSearch("Search products")
                    compose.onNodeWithContentDescription("Search products").assertTextContains(token)
                    compose.onNodeWithText("Home").performClick()
                    click("Sales history"); awaitSearch("Search receipts or customer")
                    compose.onNodeWithContentDescription("Search receipts or customer").performTextInput(sale.id)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    await("1 completed cash sale")
                    capture("sales-${mode.name}-$scale")
                    click("Date range"); await("Apply dates")
                    capture("dates-${mode.name}-$scale")
                    compose.onNodeWithText("Cancel").performClick()
                    scenario.recreate(); awaitSearch("Search receipts or customer")
                    compose.onNodeWithContentDescription("Search receipts or customer").assertTextContains(sale.id)
                    click("Open receipt · Cart ${sale.cartNumber}"); await("Sale saved on this phone")
                    runBlocking { Shop.open(context).use { shop ->
                        assertEquals(sale, shop.snapshot().sales.single { it.id == sale.id })
                        assertEquals(4L, shop.snapshot().products.single { it.id == productId }.quantity)
                    } }
                }
            }
        } finally {
            // Preserve the original assertion if Android's automation transport
            // fails while cleaning up; do not replace it with a shell exception.
            runCatching { shell("settings put system font_scale $originalScale") }
            runBlocking { Shop.open(context).use { it.setAppearance(originalMode) } }
        }
    }
}
