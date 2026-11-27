package com.nadim.bikepart

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import androidx.activity.compose.setContent
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.printing.*
import com.nadim.bikepart.ui.ShopApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LabelWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun snapshot() = runBlocking { Shop.open(context).use { it.snapshot() } }
    private fun await(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction() and !hasSetTextAction())
        if (text !in listOf("Stock", "Back")) node.performScrollTo()
        node.performClick()
    }
    private fun open(title: String) {
        await("Stock"); click("Stock")
        compose.onNodeWithContentDescription("Search products").performTextReplacement(title)
        click(title); click("Preview product labels"); await("Label copies")
    }

    @Test fun permissionCallbacksNeverSendAndUiTransportFailureRequiresExplicitRemainingCopies() {
        val journal = DevicePrintJournal(context)
        journal.clear()
        val title = "USB transport ${System.nanoTime()}"
        runBlocking { Shop.open(context).use { shop -> shop.createProduct(title, shop.snapshot().categories.first().id, "", "12", "80") } }
        val initial = snapshot()
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        var permitted = false
        var fail = true
        val usb = object : LabelUsbAccess {
            override val permissionAction = "${context.packageName}.TEST_USB_PERMISSION"
            override fun connect(): LabelConnection? = if (!permitted) null else object : LabelConnection {
                override fun send(bytes: ByteArray) {
                    attempts.incrementAndGet()
                    if (fail) throw java.io.IOException("Timed out after partial output")
                }
                override fun close() { }
            }
        }
        fun confirm() { compose.onNode(isToggleable()).performScrollTo().performClick(); click("Print confirmed labels") }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { ShopApp(labelUsb = { _, _ -> usb }) } }
                open(title)
                compose.onNodeWithText("Label copies").performScrollTo().performTextReplacement("3")
                click("Preview label"); confirm()
                await("USB permission requested. Nothing has been sent. Confirm printing again after allowing access.")
                context.sendBroadcast(Intent(usb.permissionAction).setPackage(context.packageName).putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                await("USB permission denied. No labels sent. Allow USB access to print.")
                assertEquals(0, attempts.get())
                permitted = true
                context.sendBroadcast(Intent(usb.permissionAction).setPackage(context.packageName).putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true))
                await("USB access granted. Confirm media and copies, then tap Print confirmed labels.")
                assertEquals(0, attempts.get())
                compose.onNodeWithText("Print confirmed labels").performScrollTo().assertIsNotEnabled()
                confirm()
                await("USB transport failed after 0 copies sent. Output may be partial. Inspect labels and power-cycle the printer before selecting remaining copies.")
                assertEquals(1, attempts.get())
                compose.onNodeWithText("Print confirmed labels").performScrollTo().assertIsNotEnabled()
                context.sendBroadcast(Intent(usb.permissionAction).setPackage(context.packageName).putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true))
                await("USB access granted. Confirm media and copies, then tap Print confirmed labels.")
                assertEquals(1, attempts.get())
                click("I inspected output and reset the printer")
                compose.onNodeWithText("Label copies").performScrollTo().performTextReplacement("2")
                click("Preview label")
                fail = false
                confirm()
                await("2 copies sent over USB; physical output is unconfirmed. Inspect labels before printing again.")
                assertEquals(3, attempts.get())
                assertEquals(initial, snapshot())
            }
        } finally { journal.clear() }
    }

    @Test fun uncertainUsbOutputSurvivesRecreationAndRequiresNewRemainingCopiesWithoutStockWrites() {
        val journal = DevicePrintJournal(context)
        journal.clear()
        val title = "USB label ${System.nanoTime()}"
        runBlocking { Shop.open(context).use { shop -> shop.createProduct(title, shop.snapshot().categories.first().id, "", "12", "80") } }
        val initial = snapshot()
        try {
            LabelPrinter(journal).send("Previous SKU · 3 copies", 3, byteArrayOf(1), CopyTransport { throw java.io.IOException("Disconnected") }) { false }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                open(title); click("Preview label")
                compose.onNodeWithText("Print confirmed labels").performScrollTo().assertIsNotEnabled()
                scenario.recreate(); await("Label copies")
                compose.onNodeWithText("Inspect previous output: Previous SKU · 3 copies").performScrollTo().assertIsDisplayed()
                click("I inspected output and reset the printer")
                compose.onNodeWithText("Label preview").assertDoesNotExist()
                compose.onNodeWithText("Label copies").performScrollTo().assert(
                    SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                        androidx.compose.ui.text.AnnotatedString("")))
                click("Preview label")
                compose.onNodeWithText("Label preview").assertDoesNotExist()
                compose.onNodeWithText("Label copies").performScrollTo().performTextReplacement("2")
                click("Preview label")
                compose.onNodeWithText("Print confirmed labels").performScrollTo().assertIsNotEnabled()
                compose.onNode(isToggleable()).performScrollTo().performClick()
                click("Print confirmed labels")
                compose.waitUntil(15_000) {
                    compose.onAllNodes(hasText("Connect exactly one TE244", substring = true) or
                        hasText("This phone does not support USB host/OTG.")).fetchSemanticsNodes().isNotEmpty()
                }
                assertNull(journal.pending()) // No USB device: nothing was submitted.
                assertEquals(initial, snapshot())
            }
        } finally { journal.clear() }
    }

    @Test fun staffChooseProfilesValidateCopiesAndRepreviewStableIdentityAfterCatalogEdits() {
        val title = "Label bolt ${System.nanoTime()}"
        val id = runBlocking { Shop.open(context).use { shop ->
            shop.createProduct(title, shop.snapshot().categories.first().id, "", "12", "80")
        } }
        val initial = snapshot()
        val payload = ItemQr.product(initial.datasetId, id)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(title)
            for (invalid in listOf("0", "-1", "1.5", "", "9223372036854775808")) {
                compose.onNodeWithText("Label copies").performScrollTo().performTextReplacement(invalid)
                click("Preview label")
                compose.onNodeWithText("Label preview").assertDoesNotExist()
                assertEquals(initial, snapshot())
            }
            compose.onNodeWithText("Label copies").performScrollTo().performTextReplacement("3")
            click("Package · 50 × 30 mm"); click("Preview label")
            compose.onNodeWithContentDescription("Product label QR: $payload").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("SKU: ${initial.products.single { it.id == id }.sku}").performScrollTo().assertIsDisplayed()
            assertEquals(initial, snapshot())
            click("Shared bin · 70 × 40 mm"); click("Preview label")
            compose.onNodeWithText("Shared bin · 70 × 40 mm · 3 copies").performScrollTo().assertIsDisplayed()
            click("Save label preferences")
            compose.waitUntil(15_000) { snapshot().labelPreferences == LabelPreferences(LabelMedia.SharedBin, 3) }
            val saved = snapshot()
            assertEquals(initial.copy(revision = initial.revision + if (initial.labelPreferences == saved.labelPreferences) 0 else 1, labelPreferences = saved.labelPreferences), saved)
            scenario.recreate(); await("Label copies")
            compose.onNodeWithText("Label copies").assertTextContains("3")
            compose.onNodeWithText("Shared bin · 70 × 40 mm").assertIsSelected()
            val renamed = "$title renamed with a much longer product title"
            click("Back"); click("Edit details")
            compose.onNodeWithText("Title (required)").performScrollTo().performTextReplacement(renamed)
            click("Save product"); await(renamed)
            click("Preview product labels"); click("Preview label")
            compose.onNodeWithContentDescription("Product label QR: $payload").performScrollTo().assertIsDisplayed()
            assertEquals(12L, snapshot().products.single { it.id == id }.quantity)
        }
        val renamed = snapshot().products.single { it.id == id }.title
        ActivityScenario.launch(MainActivity::class.java).use {
            open(renamed)
            compose.onNodeWithText("Label copies").assertTextContains("3")
            click("Preview label")
            compose.onNodeWithContentDescription("Product label QR: $payload").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun bothProfilesRemainReadableInLightDarkAndDoubleSystemText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalScale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val original = snapshot().appearance
        val title = "Stainless motorcycle flanged mounting bolt with an exceptionally long replacement product title ${System.nanoTime()}"
        val id = runBlocking { Shop.open(context).use { shop -> shop.createProduct(title, shop.snapshot().categories.first().id, "", "12", "80") } }
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        try {
            for (scale in listOf(1f, 2f)) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                ActivityScenario.launch(MainActivity::class.java).use {
                    open(title)
                    for (media in LabelMedia.entries) {
                        click(media.label); click("Preview label")
                        val payload = ItemQr.product(snapshot().datasetId, id)
                        compose.onNodeWithContentDescription("Product label QR: $payload").performScrollTo().assertIsDisplayed()
                        compose.waitForIdle(); android.os.SystemClock.sleep(300)
                        val name = "label-${media.name}-${mode.name}-$scale"
                        val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
                        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
                        shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
                        compose.onNodeWithText("Print confirmed labels").performScrollTo().assertIsNotEnabled()
                        val controls = java.io.File(context.getExternalFilesDir(null), "$name-usb.png")
                        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> controls.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
                        shell("cp ${controls.absolutePath} /data/local/tmp/$name-usb.png")
                        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
                        compose.onNodeWithText("SKU: ${snapshot().products.single { it.id == id }.sku}").performScrollTo().assertIsDisplayed()
                    }
                }
            }
        } finally {
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }
}
