package com.nadim.bikepart

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.ShopApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScanWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var frame: ((String) -> Unit)? = null
    private var failure: ((String) -> Unit)? = null
    private fun await(text: String) {
        try { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch (e: androidx.compose.ui.test.ComposeTimeoutException) { throw AssertionError("Waiting for $text\n${compose.onRoot().printToString()}", e) }
    }
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (text !in listOf("Back", "Stock", "Home", "More", "Carts")) node.performScrollTo()
        node.performClick()
    }
    private fun snapshot() = runBlocking { Shop.open(context).use { it.snapshot() } }
    private fun launch() = ActivityScenario.launch(MainActivity::class.java).also { scenario ->
        scenario.onActivity { activity -> activity.setContent { ShopApp(camera = { result, failed ->
            SideEffect { frame = result; failure = failed }
            Text("Substituted camera transport")
        }) } }
    }
    private fun scanCode(id: String, template: Boolean, external: Boolean): String {
        if (!external) return if (template) ItemQr.template(snapshot().datasetId, id) else ItemQr.product(snapshot().datasetId, id)
        return "000$id".also { code -> runBlocking { Shop.open(context).use {
            it.linkBarcode(code, BarcodeTarget(if (template) BarcodeTargetKind.Template else BarcodeTargetKind.Product, id))
        } } }
    }
    private fun emit(payload: String) { compose.runOnIdle { requireNotNull(frame)(payload) } }

    @Test fun staffLinkExactExternalBarcodeThenCancelAndConfirmReassignment() {
        val token = System.nanoTime()
        val title = "Alias bolt $token"
        val templateTitle = "Alias template $token"
        val code = "000$token"
        val id = runBlocking { Shop.open(context).use { shop ->
            val category = shop.snapshot().categories.first().id
            shop.saveTemplate(CatalogDraft(templateTitle, category))
            shop.createProduct(title, category, "", "4", "80")
        } }
        launch().use {
            await("Add product"); click("Add product"); click("Scan catalog details")
            await("Substituted camera transport"); emit(code)
            val before = snapshot()
            await("Unknown barcode. Use manual search or explicitly link it.")
            click("Link barcode"); compose.onNodeWithText("Find barcode target").performTextInput(title)
            click("Product: $title"); click("Save barcode link")
            await("Scanned product")
            val linked = snapshot()
            assertEquals(before.revision + 1, linked.revision)
            assertEquals(id, linked.barcodeAliases.single { it.code == code }.target.id)
            click("Link barcode"); compose.onNodeWithText("Find barcode target").performTextReplacement(templateTitle)
            click("Template: $templateTitle"); click("Cancel barcode link")
            assertEquals(linked, snapshot())
            click("Link barcode"); compose.onNodeWithText("Find barcode target").performTextReplacement(templateTitle)
            click("Template: $templateTitle"); click("Confirm reassignment")
            await("Use template details")
            val reassigned = snapshot()
            assertEquals(linked.revision + 1, reassigned.revision)
            assertEquals(id, reassigned.barcodeHistory.last { it.code == code }.previousTarget!!.id)
            click("Use template details")
            compose.onNodeWithText("Title (required)").assertTextContains(templateTitle)
            assertEquals(reassigned, snapshot())
        }
        val reopened = snapshot()
        launch().use {
            await("Add product"); click("Add product"); click("Scan catalog details")
            await("Substituted camera transport"); emit(code); await("Use template details")
            compose.onAllNodesWithText("Barcode history · revision", substring = true).assertCountEquals(2)
            assertEquals(reopened, snapshot())
        }
    }

    @Test fun inactiveExternalAliasesKeepManualFallbackWithoutWrites() {
        val token = System.nanoTime()
        val codes = runBlocking { Shop.open(context).use { shop ->
            val category = shop.snapshot().categories.first().id
            val product = shop.createProduct("Archived alias $token", category, "", "0", "80")
            val template = shop.saveTemplate(CatalogDraft("Retired alias $token", category))
            val productCode = "000$product"
            val templateCode = "000$template"
            shop.linkBarcode(productCode, BarcodeTarget(BarcodeTargetKind.Product, product))
            shop.linkBarcode(templateCode, BarcodeTarget(BarcodeTargetKind.Template, template))
            shop.archiveProduct(product); shop.retireTemplate(template)
            listOf(productCode, templateCode)
        } }
        val before = snapshot()
        launch().use {
            await("Receive stock"); click("Receive stock"); click("Scan for receiving")
            await("Substituted camera transport"); emit(codes.first())
            await("Product is unknown or archived. Use manual search.")
            compose.onAllNodesWithText("Enter quantity and cost").assertCountEquals(0)
            click("Resume scanning"); await("Substituted camera transport"); emit(codes.last())
            await("Template is unknown or retired. Use manual search.")
            click("Manual search"); await("Search products manually")
            assertEquals(before, snapshot())
        }
    }

    @Test fun realCameraPermissionDenialKeepsManualSearchAvailable() {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) { automation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        // Revoking a granted permission kills the instrumented process. Prepare denial
        // before starting the runner; an already-granted installation skips this check.
        org.junit.Assume.assumeFalse("Revoke camera permission before starting instrumentation to test denial", granted)
        shell("pm clear-permission-flags com.nadim.bikepart android.permission.CAMERA user-set user-fixed")
        val before = snapshot()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                await("Add product"); click("Add product"); click("Scan catalog details")
                compose.waitUntil(15_000) { automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button")?.isNotEmpty() == true }
                automation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button").first().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                await("Camera permission denied. Use manual search or resume to try again.")
                click("Manual search"); await("Search products manually")
                assertEquals(before, snapshot())
            }
        } finally {
            shell("pm clear-permission-flags com.nadim.bikepart android.permission.CAMERA user-set user-fixed")
        }
    }

    @Test fun manualFallbackCanReachTemplatesBeyondTheFirstPage() {
        val prefix = "Paged scan template ${System.nanoTime()}"
        runBlocking { Shop.open(context).use { shop ->
            val category = shop.snapshot().categories.first().id
            repeat(31) { shop.saveTemplate(CatalogDraft("$prefix $it", category)) }
        } }
        val before = snapshot()
        val last = before.templates.filter { it.catalog.title.startsWith(prefix) }.last()
        launch().use {
            await("Add product"); click("Add product"); click("Scan catalog details"); click("Manual search")
            compose.onNodeWithText("Search products manually").performTextInput(prefix)
            compose.onAllNodesWithText("Use template: ${last.catalog.title}").assertCountEquals(0)
            click("Show more results")
            click("Use template: ${last.catalog.title}")
            compose.onNodeWithText("Title (required)").assertTextContains(last.catalog.title)
            assertEquals(before, snapshot())
        }
    }

    @Test fun saleRequiresQuantityAndIgnoresRepeatedAndStaleCameraFrames() = saleScan(false)
    @Test fun externalAliasesRequireSaleQuantityAndRestrictTemplates() = saleScan(true)
    private fun saleScan(external: Boolean) {
        val token = System.nanoTime()
        val id = runBlocking { Shop.open(context).use { it.createProduct("Scan bolt $token", it.snapshot().categories.first().id, "", "4", "80") } }
        val payload = scanCode(id, false, external)
        val templateId = runBlocking { Shop.open(context).use { it.saveTemplate(CatalogDraft("Sale-restricted template $token", it.snapshot().categories.first().id)) } }
        val templatePayload = scanCode(templateId, true, external)
        val priorCartIds = snapshot().carts.map { it.id }.toSet()
        var cartId: String? = null
        val scenario = launch()
        try {
            await("New sale"); click("New sale"); await("Add products")
            cartId = snapshot().carts.single { it.id !in priorCartIds }.id
            click("Add products")
            click("Scan for sale"); await("Scan purpose: Sale")
            await("Substituted camera transport")
            val before = snapshot()
            val staleFrame = frame!!
            emit(payload); emit(payload)
            await("Recognition paused")
            assertEquals(before, snapshot())
            click("Add to cart"); await("Cart quantity must be a positive whole number within the supported limit")
            assertEquals(before, snapshot())
            compose.onNodeWithText("Pieces to add").performScrollTo().performTextReplacement("3")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            click("Add to cart"); await("Saved on this phone")
            val after = snapshot()
            val cart = after.carts.single { it.lines.any { line -> line.productId == id } }; cartId = cart.id
            assertEquals(3L, cart.lines.single().quantity)
            assertEquals(4L, after.products.single { it.id == id }.quantity)
            compose.runOnIdle { staleFrame(payload) }
            assertEquals(after, snapshot())
            click("Add products"); click("Scan for sale")
            await("Substituted camera transport")
            emit("bikepart:2:bad"); await("Unsupported or malformed shop QR. Use manual search.")
            emit(payload)
            compose.onAllNodesWithText("Pieces to add").assertCountEquals(0)
            click("Resume scanning"); await("Substituted camera transport"); emit(templatePayload)
            await("Template codes only prefill new products. They cannot be sold or received.")
            compose.onAllNodesWithText("Add to cart").assertCountEquals(0)
            click("Resume scanning"); await("Substituted camera transport"); emit(payload)
            await("Pieces to add")
            click("Manual search"); await("Search products for cart")
        } finally {
            scenario.close()
            runBlocking { Shop.open(context).use { shop -> shop.snapshot().carts.filter { it.id == cartId }.forEach { shop.discardCart(it.id, it.editVersion) } } }
        }
    }

    @Test fun receivingScanSelectsProductAndOnlySaveCommitsArrival() = receivingScan(false)
    @Test fun externalAliasReceivingRequiresExplicitArrivalSave() = receivingScan(true)
    private fun receivingScan(external: Boolean) {
        val token = System.nanoTime()
        val title = "Received washer $token"
        val id = runBlocking { Shop.open(context).use { it.createProduct(title, it.snapshot().categories.first().id, "", "2", "80") } }
        val payload = scanCode(id, false, external)
        val scenario = launch()
        try {
            await("Receive stock"); click("Receive stock"); click("Scan for receiving")
            await("Substituted camera transport")
            val before = snapshot()
            emit(payload); emit(payload)
            await("Enter quantity and cost"); assertEquals(before, snapshot())
            click("Enter quantity and cost"); await("Received pieces")
            assertEquals(before, snapshot())
            compose.onNodeWithText("Received pieces").performScrollTo().performTextReplacement("3")
            compose.onNodeWithText("Purchase unit cost (₹)").performScrollTo().performTextReplacement("25")
            click("Save arrival"); await("Stock arrival history")
            val after = snapshot()
            assertEquals(before.revision + 1, after.revision)
            assertEquals(5L, after.products.single { it.id == id }.quantity)
            assertEquals(25L, after.arrivals.single { it.productId == id }.unitCostRupees)
            scenario.close()
            ActivityScenario.launch(MainActivity::class.java).use { reopened ->
                await("Stock")
                compose.onNode(hasText("Stock") and hasClickAction()).performClick()
                compose.onNodeWithText("Search products").performScrollTo().performTextInput(title)
                compose.onNode(hasText(title) and hasClickAction() and !hasSetTextAction()).performScrollTo().performClick()
                await("Stock arrival history")
                reopened.recreate(); await("Stock arrival history")
            }
            assertEquals(after, snapshot())
        } finally { scenario.close() }
    }

    @Test fun creationOffersExplicitExistingCopyAndTemplateChoicesWithManualFallbacks() = catalogScan(false)
    @Test fun externalAliasesRouteCatalogCopiesAndRejectTemplateArrivals() = catalogScan(true)
    private fun catalogScan(external: Boolean) {
        val token = System.nanoTime()
        val title = "Catalog nut $token"
        lateinit var id: String
        lateinit var templateId: String
        runBlocking { Shop.open(context).use { shop ->
            val category = shop.snapshot().categories.first().id
            id = shop.createProduct(title, category, "Shop description", "2", "80")
            templateId = shop.saveTemplate(CatalogDraft("Template nut $token", category, "Template description"))
        } }
        val productQr = scanCode(id, false, external)
        val templateQr = scanCode(templateId, true, external)
        val before = snapshot()
        val scenario = launch()
        try {
            await("Add product"); click("Add product")
            compose.onNodeWithText("Title (required)").performScrollTo().performTextReplacement("Unsaved draft $token")
            click("Scan catalog details"); await("Substituted camera transport")
            compose.runOnIdle { failure!!("Camera permission denied. Use manual search or resume to try again.") }
            await("Recognition paused"); click("Manual search"); await("Search products manually")
            click("Continue new product")
            compose.onNodeWithText("Title (required)").assertTextContains("Unsaved draft $token")
            assertEquals(before, snapshot())
            click("Scan catalog details"); await("Substituted camera transport")
            emit(productQr); await("Open existing"); click("Open existing"); await("Shop description")
            assertEquals(before, snapshot())
            click("Back"); await("Add product"); click("Add product"); click("Scan catalog details")
            await("Substituted camera transport"); emit(productQr)
            click("Copy catalog details"); await("Catalog details only. Enter stock, selling price and locations separately for each new product.")
            compose.onNodeWithText("Title (required)").assertTextContains(title)
            compose.onNodeWithText("Opening stock (pieces)").performScrollTo().assertTextEquals("Opening stock (pieces)", "")
            assertEquals(before, snapshot())
            click("Scan catalog details"); await("Substituted camera transport"); emit(templateQr)
            click("Use template details"); compose.onNodeWithText("Title (required)").assertTextContains("Template nut $token")
            assertEquals(before, snapshot())
            click("Back"); await("Receive stock"); click("Receive stock"); click("Scan for receiving")
            await("Substituted camera transport"); emit(templateQr)
            await("Template codes only prefill new products. They cannot be sold or received.")
            compose.onAllNodesWithText("Enter quantity and cost").assertCountEquals(0)
            click("Resume scanning"); await("Substituted camera transport"); click("Cancel camera"); await("Recognition paused")
            click("Resume scanning"); await("Substituted camera transport")
            compose.runOnIdle { failure!!("Recognition failed. Use manual search or resume to try again.") }
            click("Manual search"); await("Search products manually")
            compose.onNodeWithText("Search products manually").performTextInput(title)
            compose.onNode(hasText(title) and hasClickAction() and !hasSetTextAction()).performScrollTo().performClick()
            await("Received pieces"); assertEquals(before, snapshot())
        } finally { scenario.close() }
    }

    @Test fun scannerAndReceivingRemainUsableAcrossThemesAndLargeText() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val originalScale = android.provider.Settings.System.getFloat(context.contentResolver, android.provider.Settings.System.FONT_SCALE, 1f)
        val originalAppearance = snapshot().appearance
        val token = System.nanoTime()
        val id = runBlocking { Shop.open(context).use { it.createProduct("Stainless motorcycle flanged bolt with an exceptionally long shared bin label $token", it.snapshot().categories.first().id, "", "0", Long.MAX_VALUE.toString()) } }
        val payload = scanCode(id, false, true)
        val templateTitle = "Long replacement template for stainless motorcycle flanged bolts $token"
        runBlocking { Shop.open(context).use { it.saveTemplate(CatalogDraft(templateTitle, it.snapshot().categories.first().id)) } }
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun capture(name: String) {
            compose.waitForIdle()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
        }
        try {
            for (scale in listOf(1f, 2f)) for (appearance in listOf(Appearance.Light, Appearance.Dark)) {
                shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(appearance) } }
                val before = snapshot()
                launch().use {
                    await("Add product"); click("Add product"); click("Scan catalog details")
                    await("Substituted camera transport"); emit(payload)
                    compose.onNodeWithText("Open existing").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    capture("scan-catalog-${appearance.name}-$scale")
                    compose.onNodeWithText("Copy catalog details").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    click("Link barcode")
                    compose.onNodeWithText("Find barcode target").performScrollTo().performTextInput("No barcode target $token")
                    compose.onNodeWithText("Find barcode target").performScrollTo().assertIsDisplayed()
                    capture("barcode-keyboard-${appearance.name}-$scale")
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    compose.onNodeWithText("No active targets found. Use manual search to create a product or template first.").performScrollTo().assertIsDisplayed()
                    capture("barcode-empty-${appearance.name}-$scale")
                    compose.onNodeWithText("Find barcode target").performScrollTo().performTextReplacement("Stainless motorcycle flanged bolt with an exceptionally long shared bin label $token")
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    compose.onNodeWithText("Product: Stainless motorcycle flanged bolt with an exceptionally long shared bin label $token").performScrollTo().performClick()
                    compose.onNodeWithText("Product: Stainless motorcycle flanged bolt with an exceptionally long shared bin label $token").assertIsSelected()
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    compose.onNodeWithText("Save barcode link").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    capture("barcode-link-${appearance.name}-$scale")
                    compose.onNodeWithText("Find barcode target").performScrollTo().performTextReplacement(templateTitle)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    compose.onNodeWithText("Template: $templateTitle").performScrollTo().performClick()
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    compose.onNodeWithText("Confirm reassignment").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    capture("barcode-conflict-${appearance.name}-$scale")
                    click("Cancel barcode link")
                    click("Manual search"); await("Search products manually")
                    compose.onNodeWithText("Search products manually").performTextInput("No matching scan $token")
                    compose.onNodeWithText("No matching products").assertExists()
                    click("Back"); await("Receive stock"); click("Receive stock"); click("Scan for receiving")
                    await("Substituted camera transport"); emit(payload); click("Enter quantity and cost")
                    compose.onNodeWithText("Received pieces").performScrollTo().performClick().performTextInput("0")
                    compose.onNodeWithText("Purchase unit cost (₹)").performScrollTo().performTextInput("2")
                    click("Save arrival"); await("Enter a positive whole-piece quantity")
                    compose.onNodeWithText("Save arrival").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    capture("scan-arrival-${appearance.name}-$scale")
                    assertEquals(before, snapshot())
                }
            }
        } finally {
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(originalAppearance) } }
        }
    }
}
