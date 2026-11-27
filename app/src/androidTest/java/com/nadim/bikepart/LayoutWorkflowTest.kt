package com.nadim.bikepart

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nadim.bikepart.shop.Appearance
import com.nadim.bikepart.shop.Shop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises the actual app and Room state across the compact/rail breakpoint. */
class LayoutWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "layout-$name.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun navigationReflowsAndPreservesStockSearchAcrossWindowSizesAndAppearances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        val originalScale = shell("settings get system font_scale").ifBlank { "1.0" }
        val density = context.resources.displayMetrics.density
        fun resize(widthDp: Int, heightDp: Int) {
            shell("wm size ${(widthDp * density).toInt()}x${(heightDp * density).toInt()}")
        }
        val original = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        val title = "Layout precision workshop assembly ${System.nanoTime()}"
        runBlocking { Shop.open(context).use {
            it.createProduct(title, it.snapshot().categories.first().id, "", "3", "999999999")
        } }
        try {
            for (mode in listOf(Appearance.Light, Appearance.Dark)) for (scale in listOf("1.0", "2.0")) {
                shell("settings put system font_scale $scale")
                resize(360, 762)
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    compose.waitUntil(30_000) { compose.onAllNodesWithText("Stock").fetchSemanticsNodes().isNotEmpty() }
                    capture("home-${mode.name}-$scale")
                    compose.onNodeWithText("Stock").performClick()
                    compose.onNodeWithText("Search products").performTextInput(title)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    // Test resizing a settled search, without a text cursor competing
                    // with scroll-to actions while Android changes the window bounds.
                    scenario.onActivity { activity -> activity.currentFocus?.clearFocus() }
                    val result = hasText(title) and hasClickAction() and !hasSetTextAction()
                    compose.waitUntil(15_000) { compose.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNode(result).performScrollTo().assertIsDisplayed()
                    capture("stock-compact-${mode.name}-$scale")
                    val compactHome = compose.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot
                    val compactMore = compose.onNodeWithText("More").fetchSemanticsNode().boundsInRoot
                    assertEquals(compactHome.top, compactMore.top, 2f)

                    resize(976, 686)
                    compose.waitUntil(15_000) {
                        val home = compose.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot
                        val more = compose.onNodeWithText("More").fetchSemanticsNode().boundsInRoot
                        more.top > home.bottom
                    }
                    compose.onNodeWithText("Search products").performScrollTo().assertTextContains(title)
                    compose.onNode(result).performScrollTo().assertIsDisplayed()
                    capture("stock-wide-${mode.name}-$scale")
                    val home = compose.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot
                    val more = compose.onNodeWithText("More").fetchSemanticsNode().boundsInRoot
                    assertTrue(more.left < home.right)
                    compose.onNodeWithText("More").performClick()
                    compose.onNodeWithText("Shop backups").performScrollTo().performClick()
                    compose.onNodeWithText("Connect or repair Drive").performScrollTo()
                        .assertHeightIsAtLeast(48.dp).assertIsDisplayed()
                    capture("backups-${mode.name}-$scale")
                    compose.onNodeWithContentDescription("Back").performClick()
                    compose.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
                }
            }
        } finally {
            shell("wm size $originalSize")
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }
}
