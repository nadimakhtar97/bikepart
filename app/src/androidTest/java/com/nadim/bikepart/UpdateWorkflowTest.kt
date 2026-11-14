package com.nadim.bikepart

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.ShopApp
import com.nadim.bikepart.updates.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun snapshot() = runBlocking { Shop.open(context).use { it.snapshot() } }
    private fun await(text: String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String) { val node = compose.onNodeWithText(text); if (text !in listOf("More", "Stock")) node.performScrollTo(); node.performClick() }

    @Test fun manualChecksShowCheckingFailureRetryAndNoUpdateWithoutChangingShop() {
        var seededCartId = ""
        runBlocking { Shop.open(context).use { shop ->
            val id = shop.createProduct("Updater bolt ${System.nanoTime()}", shop.snapshot().categories.first().id, "", "12", "80")
            val sold = shop.createCart()
            shop.addCartProduct(sold, shop.snapshot().carts.single { it.id == sold }.editVersion, id, "2")
            shop.checkout(sold, shop.snapshot().carts.single { it.id == sold }.editVersion, "160")
            val saved = shop.createCart()
            seededCartId = saved
            shop.addCartProduct(saved, shop.snapshot().carts.single { it.id == saved }.editVersion, id, "1")
        } }
        val before = snapshot()
        val finish = CompletableDeferred<Unit>()
        var attempts = 0
        val updater = GitHubUpdater(InstalledApp(context.packageName, 1, "1.0", 33)) {
            attempts++; if (attempts == 1) { finish.await(); throw java.io.IOException("offline") }
            UpdateHttpResponse(200, "[]")
        }
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { ShopApp(updater = { updater }) } }
            await("Stock"); click("More")
            compose.onNodeWithText("Installed version: 1.0 (1)").performScrollTo().assertIsDisplayed()
            click("Check for updates"); await("Checking for updates…")
            compose.onNodeWithText("Checking for updates…").performScrollTo().assertIsNotEnabled()
            finish.complete(Unit)
            await("Couldn't check for updates. Check your connection and try again.")
            click("Retry update check"); await("No compatible updates available.")
            scenario.recreate(); await("Stock")
            assertEquals(before, snapshot())
        }
        } finally {
            // The preserved cart was seeded by this test; remove only that fixture
            // after the preservation assertion so other workflow tests retain their preconditions.
            runBlocking { Shop.open(context).use { shop ->
                shop.snapshot().carts.find { it.id == seededCartId }?.let { shop.discardCart(it.id, it.editVersion) }
            } }
        }
    }
    @Test fun availableReleaseIsReadableInBothAppearancesAtNormalAndDoubleFontScale() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        } }
        val originalScale = android.provider.Settings.System.getFloat(context.contentResolver, android.provider.Settings.System.FONT_SCALE, 1f)
        val originalAppearance = snapshot().appearance
        val visibleState = kotlinx.coroutines.flow.MutableStateFlow<UpdateState>(UpdateState.Idle)
        val updater = object : AppUpdater {
            override val installed = InstalledApp(context.packageName, 1, "1.0", 33)
            override val state: kotlinx.coroutines.flow.StateFlow<UpdateState> = visibleState
            override suspend fun check() {
                visibleState.value = UpdateState.Available(AvailableUpdate(2, 2, "1.1 Workshop improvements", 15_728_640,
                    "Faster stock searches and clearer saved cart details. Your stock, sales and unfinished bills stay available while you check for updates.",
                    "https://github.com/nadimakhtar97/bikepart/releases/download/v2/bikepart.apk", "a".repeat(64), 33))
            }
        }
        fun capture(name: String) {
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
            shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
        }
        try {
            for (scale in listOf(1f, 2f)) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                shell("settings put system font_scale $scale")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                val before = snapshot()
                visibleState.value = UpdateState.Idle
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { it.setContent { ShopApp(updater = { updater }) } }
                    await("Stock"); click("More"); click("Check for updates")
                    await("Version 1.1 Workshop improvements available")
                    compose.onNodeWithText("Version 1.1 Workshop improvements available").performScrollTo().assertIsDisplayed()
                    capture("updates-${mode.name.lowercase()}-$scale-summary")
                    compose.onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()
                        .assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
                    capture("updates-${mode.name.lowercase()}-$scale-action")
                    click("Stock"); click("More")
                    compose.onNodeWithText("Version 1.1 Workshop improvements available").performScrollTo().assertIsDisplayed()
                    scenario.recreate()
                    scenario.onActivity { it.setContent { ShopApp(updater = { updater }) } }
                    await("Stock"); click("More")
                    compose.onNodeWithText("Version 1.1 Workshop improvements available").performScrollTo().assertIsDisplayed()
                    assertEquals(before, snapshot())
                }
            }
        } finally {
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(originalAppearance) } }
        }
    }

    @Test fun leavingForegroundCancelsManualCheckAndNeverSchedulesAnother() {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val updater = GitHubUpdater(InstalledApp(context.packageName, 1, "1.0", 33)) {
            attempts.incrementAndGet(); entered.complete(Unit)
            try { kotlinx.coroutines.awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        val before = snapshot()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { ShopApp(updater = { updater }) } }
            await("Stock"); click("More"); click("Check for updates")
            runBlocking { kotlinx.coroutines.withTimeout(5_000) { entered.await() } }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            runBlocking { kotlinx.coroutines.withTimeout(5_000) { cancelled.await() } }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            await("Check for updates")
            assertEquals(1, attempts.get())
            assertEquals(before, snapshot())
        }
    }

}
