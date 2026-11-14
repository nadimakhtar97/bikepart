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

class CartWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun await(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (text in listOf("Carts", "Home", "Back", "New sale")) node.performClick() else node.performScrollTo().performClick()
    }
    private fun snapshot() = runBlocking { Shop.open(context).use { it.snapshot() } }
    private fun waitSaved(customer: String) { compose.waitUntil(15_000) { snapshot().carts.any { it.customer == customer } } }
    private fun label(customer: String) { compose.onNodeWithText("Customer label (optional)").performScrollTo().performTextReplacement(customer); waitSaved(customer); await("Saved on this phone") }
    private fun addProduct(title: String, pieces: String) {
        click("Add products")
        compose.onNodeWithText("Search products for cart").performTextInput(title)
        compose.onNode(hasText(title) and hasClickAction() and !hasSetTextAction()).performScrollTo().performClick()
        compose.onNodeWithText("Pieces to add").performScrollTo().performTextReplacement(pieces)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        click("Add to cart")
        await("Saved on this phone")
    }

    @Test fun cashierSwitchesAutosavedCartsRetainsQuotesAndRecoversStaleEdits() {
        val token = System.nanoTime().toString()
        val title = "Counter bolt $token"
        val customer = "Ravi $token"
        val secondCustomer = "Meena $token"
        val product = runBlocking { Shop.open(context).use { it.createProduct(title, it.snapshot().categories.first().id, "", "1", "80") } }
        var scenario: ActivityScenario<MainActivity>? = null
        val cartIds = mutableSetOf<String>()
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("New sale"); click("New sale")
            await("Customer label (optional)"); label(customer)
            val first = snapshot().carts.single { it.customer == customer }; cartIds += first.id
            addProduct(title, "2")
            compose.onNodeWithText("On hand: 1 pieces").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Pieces · $title").performScrollTo().performTextReplacement("3")
            await("Saved cart total: ₹240")
            click("Save and return to carts")
            click("New sale"); await("Customer label (optional)"); label(secondCustomer)
            val second = snapshot().carts.single { it.customer == secondCustomer }; cartIds += second.id
            addProduct(title, "1")
            compose.onNodeWithText("Other carts: 3 pieces").performScrollTo().assertIsDisplayed()
            click("Save and return to carts"); click("Open ${first.name}")
            runBlocking { Shop.open(context).use { it.setSellingPrice(product, "95") } }
            compose.onNodeWithContentDescription("Pieces · $title").performScrollTo().performTextReplacement("5")
            await("Saved cart total: ₹400")
            scenario.recreate(); await("Saved cart total: ₹400")
            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("Carts"); click("Carts"); click("Open ${first.name}")
            await("Saved cart total: ₹400")
            compose.onNodeWithText("Customer label (optional)").assertTextContains(customer)
            runBlocking { Shop.open(context).use { shop ->
                val current = shop.snapshot().carts.single { it.id == first.id }
                shop.saveCart(current.id, current.editVersion, "Changed elsewhere $token", current.lines.associate { it.productId to it.quantity.toString() })
            } }
            compose.onNodeWithText("Customer label (optional)").performScrollTo().performTextReplacement("Stale $token")
            await("This cart changed. Reload the saved cart before editing again.")
            assertEquals("Changed elsewhere $token", snapshot().carts.single { it.id == first.id }.customer)
            click("Reload saved cart"); await("Saved on this phone")
            compose.onNodeWithText("Customer label (optional)").assertTextContains("Changed elsewhere $token")
            compose.onNodeWithContentDescription("Remove $title").performScrollTo().performClick(); await("No products in this cart. Add a product to begin.")
            addProduct(title, "2"); await("Saved cart total: ₹190")
            compose.onNodeWithContentDescription("Pieces · $title").performScrollTo().performTextReplacement("0")
            await("Cart quantity must be a positive whole number within the supported limit")
            assertEquals(190L, snapshot().carts.single { it.id == first.id }.totalRupees)
            click("Reload saved cart"); await("Saved on this phone")
            click("Discard cart"); compose.onNodeWithText("Discard").performClick()
            await("New sale")
            assertFalse(snapshot().carts.any { it.id == first.id })
            click("Open ${second.name}"); await("Saved cart total: ₹80")
            assertEquals(1L, snapshot().products.single { it.id == product }.quantity)
            // Hold the real writer lock so typing during a commit is deterministic.
            val storage = ShopStorage(context, "shop.db")
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(storage.database(storage.active)).path, null, 0).use { writer ->
                writer.beginTransactionNonExclusive()
                try {
                    compose.onNodeWithText("Customer label (optional)").performScrollTo().performClick().performTextReplacement("$secondCustomer paused")
                    await("Saving cart…")
                    compose.onNodeWithText("Customer label (optional)").performTextReplacement("$secondCustomer queued ")
                } finally { writer.endTransaction() }
            }
            waitSaved("$secondCustomer queued")
            await("Saved on this phone")
            compose.onNodeWithText("Customer label (optional)").assertIsFocused().assertTextContains("$secondCustomer queued ")
            compose.onNodeWithText("Customer label (optional)").performTextInput("urgent")
            click("Back"); await("New sale")
            assertEquals("$secondCustomer queued urgent", snapshot().carts.single { it.id == second.id }.customer)
        } finally {
            scenario?.close()
            runBlocking { Shop.open(context).use { shop -> shop.snapshot().carts.filter { it.id in cartIds }.forEach { shop.discardCart(it.id, it.editVersion) } } }
        }
    }

    @Test fun cartScreensRemainUsableAcrossThemesAndTwoHundredPercentText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalScale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val token = System.nanoTime().toString()
        val title = "Stainless motorcycle flanged bolt with an exceptionally long product title $token"
        lateinit var id: String
        lateinit var cart: Cart
        lateinit var empty: Cart
        val appearance = runBlocking { Shop.open(context).use { shop ->
            val original = shop.snapshot().appearance
            id = shop.createProduct(title, shop.snapshot().categories.first().id, "", "0", Long.MAX_VALUE.toString())
            val cartId = shop.createCart()
            shop.saveCart(cartId, 0, "Workshop customer with a long optional label $token", mapOf(id to "1"))
            cart = shop.snapshot().carts.single { it.id == cartId }
            val emptyId = shop.createCart(); empty = shop.snapshot().carts.single { it.id == emptyId }
            original
        } }
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun capture(name: String) {
            compose.waitForIdle()
            android.os.SystemClock.sleep(300)
            instrumentation.waitForIdleSync()
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
            shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
        }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            for (scale in listOf(1f, 2f)) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                scenario?.close(); shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                scenario = ActivityScenario.launch(MainActivity::class.java)
                await("Carts"); click("Carts"); await("Open ${cart.name}")
                compose.onNodeWithText("Open ${cart.name}").performScrollTo().assertHeightIsAtLeast(48.dp)
                capture("carts-list-${mode.name}-$scale")
                click("Open ${cart.name}"); await("Customer label (optional)")
                capture("carts-label-${mode.name}-$scale")
                compose.onNodeWithContentDescription("Pieces · $title").performScrollTo().assertIsDisplayed()
                compose.onNodeWithContentDescription("Pieces · $title").performClick().performTextReplacement("0")
                await("Cart quantity must be a positive whole number within the supported limit")
                compose.onNodeWithContentDescription("Pieces · $title").assertIsFocused()
                capture("carts-error-keyboard-${mode.name}-$scale")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                click("Reload saved cart"); await("Saved on this phone")
                compose.onNodeWithText("Saved cart total: ₹92,23,37,20,36,85,47,75,807").performScrollTo().assertIsDisplayed()
                capture("carts-total-${mode.name}-$scale")
                click("Save and return to carts"); click("Open ${empty.name}")
                await("No products in this cart. Add a product to begin.")
                capture("carts-empty-${mode.name}-$scale")
            }
        } finally {
            scenario?.close(); shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { shop ->
                shop.setAppearance(appearance)
                shop.snapshot().carts.filter { it.id in setOf(cart.id, empty.id) }.forEach { shop.discardCart(it.id, it.editVersion) }
            } }
        }
    }
}
