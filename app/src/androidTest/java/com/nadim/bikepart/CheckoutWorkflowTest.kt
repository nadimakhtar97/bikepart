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

class CheckoutWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun await(text: String) {
        try { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Throwable) { throw AssertionError("Waiting for $text\n" + compose.onRoot().printToString(), failure) }
    }
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (text in listOf("Carts", "More", "Back")) node.performClick() else node.performScrollTo().performClick()
    }
    private fun snapshot() = runBlocking { Shop.open(context).use { it.snapshot() } }

    @Test fun lostResponseAfterCommitRecoversReceiptBeforeCheckoutAcknowledgement() {
        val cart = runBlocking { Shop.open(context).use { shop ->
            val product = shop.createProduct("Interrupted checkout ${System.nanoTime()}", shop.snapshot().categories.first().id, "", "1", "80")
            val id = shop.createCart()
            shop.saveCart(id, 0, "Interrupted customer", mapOf(product to "1"))
            shop.snapshot().carts.single { it.id == id }
        } }
        val acknowledged = java.util.concurrent.atomic.AtomicBoolean(false)
        var scenario: ActivityScenario<MainActivity>? = ActivityScenario.launch(MainActivity::class.java)
        try {
            await("Carts"); click("Carts"); click("Open ${cart.name}"); click("Checkout"); await("Cash received (₹)")
            scenario!!.recreate(); await("Cash received (₹)")
            runBlocking { Shop.open(context).use { it.saveCart(cart.id, cart.editVersion, "Changed while reviewing", cart.lines.associate { line -> line.productId to line.quantity.toString() }) } }
            click("Exact amount"); click("Complete cash sale")
            await("This cart changed. Reload the saved cart before editing again.")
            assertFalse(snapshot().sales.any { it.cartId == cart.id })
            click("Edit cart"); await("Customer label (optional)"); click("Checkout"); await("Cash received (₹)")
            // Use the existing app write boundary to lose the response after real commit,
            // before write() refreshes state or invokes its success callback.
            scenario!!.onActivity { activity ->
                androidx.lifecycle.ViewModelProvider(activity)[ShopViewModel::class.java].write({
                    checkout(cart.id, cart.editVersion + 1, "100")
                    throw java.io.IOException("Lost checkout response after commit")
                }, { acknowledged.set(true) })
            }
            await("Sale saved on this phone")
            assertFalse(acknowledged.get())
            compose.onNodeWithText("Could not save. Try again.").assertDoesNotExist()
            val sale = snapshot().sales.single { it.cartId == cart.id }
            assertEquals(20L, sale.changeRupees)
            assertEquals(0L, snapshot().products.single { it.id == cart.lines.single().productId }.quantity)
            scenario!!.recreate(); await("Sale saved on this phone")
            scenario!!.close(); scenario = null
            runBlocking { Shop.open(context).use { reopened ->
                assertEquals(sale, reopened.checkout(cart.id, cart.editVersion, "100"))
                assertEquals(1, reopened.snapshot().sales.count { it.cartId == cart.id })
            } }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("More"); click("More"); click("Sales history"); click("Open receipt · ${cart.name}")
            await("Sale saved on this phone")
            assertEquals(sale, snapshot().sales.single { it.cartId == cart.id })
        } finally {
            scenario?.close()
            runBlocking { Shop.open(context).use { shop -> shop.snapshot().carts.find { it.id == cart.id }?.let { shop.discardCart(it.id, it.editVersion) } } }
        }
    }

    @Test fun cashierFlushesPendingEditsHandlesShortageAndReopensTheSameReceipt() {
        val title = "Checkout bolt ${System.nanoTime()}"
        val pair = runBlocking { Shop.open(context).use { shop ->
            val product = shop.createProduct(title, shop.snapshot().categories.first().id, "Historical detail", "1", "80")
            val id = shop.createCart()
            shop.saveCart(id, 0, "", mapOf(product to "2"))
            product to shop.snapshot().carts.single { it.id == id }
        } }
        val (product, cart) = pair
        var scenario: ActivityScenario<MainActivity>? = ActivityScenario.launch(MainActivity::class.java)
        try {
            await("Carts"); click("Carts"); click("Open ${cart.name}")
            await("Saved on this phone")
            compose.onNodeWithText("Customer label (optional)").performScrollTo().performTextReplacement("Ravi at checkout")
            // Entry must flush this draft, even before the autosave debounce fires.
            click("Checkout"); await("Cash received (₹)")
            assertEquals("Ravi at checkout", snapshot().carts.single { it.id == cart.id }.customer)
            click("Complete cash sale"); await("Cash received must be a nonnegative whole number")
            compose.onNodeWithText("Cash received (₹)").performTextReplacement("159")
            click("Complete cash sale"); await("Cash received must cover the whole cart total")
            click("Exact amount"); await("Change: ₹0")
            click("Complete cash sale")
            await("$title: requested 2, available 1\nNo sale saved. Edit the cart quantities explicitly before retrying.")
            assertNull(runBlocking { Shop.open(context).use { it.receipt(cart.id) } })
            click("Edit cart"); await("Customer label (optional)")
            compose.onNodeWithContentDescription("Pieces · $title").performScrollTo().performTextReplacement("1")
            click("Checkout"); await("Cart total: ₹80")
            compose.onNodeWithText("Cash received (₹)").performScrollTo().performTextReplacement("100")
            await("Change: ₹20"); click("Complete cash sale"); await("Sale saved on this phone")
            val sale = snapshot().sales.single { it.cartId == cart.id }
            assertEquals(0L, snapshot().products.single { it.id == product }.quantity)
            compose.onNodeWithText("Cash received: ₹100").performScrollTo().assertIsDisplayed()
            scenario!!.recreate(); await("Sale saved on this phone")
            scenario!!.close(); scenario = null
            runBlocking { Shop.open(context).use { it.editProduct(product, "Renamed $title", it.snapshot().categories.first().id, "Changed detail") } }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("More"); click("More"); click("Sales history"); click("Open receipt · ${cart.name}")
            await("Sale saved on this phone")
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Historical detail").performScrollTo().assertIsDisplayed()
            assertEquals(sale, snapshot().sales.single { it.cartId == cart.id })
        } finally {
            scenario?.close()
            runBlocking { Shop.open(context).use { shop -> shop.snapshot().carts.find { it.id == cart.id }?.let { shop.discardCart(it.id, it.editVersion) } } }
        }
    }

    @Test fun checkoutAndReceiptsRemainUsableAcrossThemesAndLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalScale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val originalAppearance = snapshot().appearance
        val title = "Stainless motorcycle flanged bolt with an exceptionally long title ${System.nanoTime()}"
        val cart = runBlocking { Shop.open(context).use { shop ->
            val product = shop.createProduct(title, shop.snapshot().categories.first().id, "Retained historical description", "1", Long.MAX_VALUE.toString())
            val id = shop.createCart()
            shop.saveCart(id, 0, "A customer with a very long workshop label", mapOf(product to "1"))
            shop.snapshot().carts.single { it.id == id }
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
            for (scale in listOf(1f, 2f)) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                scenario?.close(); shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                scenario = ActivityScenario.launch(MainActivity::class.java)
                await("Carts"); click("Carts"); click("Open ${cart.name}"); click("Checkout")
                await("Cash received (₹)")
                compose.onNodeWithText("Cart total: ₹92,23,37,20,36,85,47,75,807").performScrollTo().assertIsDisplayed()
                capture("checkout-total-${mode.name}-$scale")
                compose.onNodeWithText("Cash received (₹)").performScrollTo().performClick().performTextReplacement("0")
                click("Complete cash sale"); await("Cash received must cover the whole cart total")
                capture("checkout-error-${mode.name}-$scale")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                compose.onNodeWithText("Exact amount").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                await("Change: ₹0"); capture("checkout-exact-${mode.name}-$scale")
                click("Back"); click("Back")
            }
            click("Open ${cart.name}"); click("Checkout"); click("Exact amount"); click("Complete cash sale"); await("Sale saved on this phone")
            for (scale in listOf(1f, 2f)) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                scenario?.close(); shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                scenario = ActivityScenario.launch(MainActivity::class.java)
                await("More"); click("More"); click("Sales history"); click("Open receipt · ${cart.name}"); await("Sale saved on this phone")
                capture("receipt-top-${mode.name}-$scale")
                compose.onNodeWithText("Total: ₹92,23,37,20,36,85,47,75,807").performScrollTo().assertIsDisplayed()
                capture("receipt-total-${mode.name}-$scale")
                compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
                capture("receipt-line-${mode.name}-$scale")
            }
        } finally {
            scenario?.close(); shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { shop ->
                shop.setAppearance(originalAppearance)
                shop.snapshot().carts.find { it.id == cart.id }?.let { shop.discardCart(it.id, it.editVersion) }
            } }
        }
    }
}
