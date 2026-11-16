package com.nadim.bikepart

import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.nadim.bikepart.shop.Appearance
import com.nadim.bikepart.shop.Shop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises the shipped MainActivity with its real Room store. */
class FluentAppearanceWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun appearanceSurvivesReopeningAndPrimaryActionsUseFluentBrand() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        fun await(label: String) = compose.waitUntil(15_000) {
            compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        try {
            for (mode in listOf(Appearance.Light, Appearance.Dark, Appearance.System)) {
                await("More"); compose.onNodeWithText("More").performClick()
                compose.onNodeWithText(mode.name).performScrollTo().performClick()
                compose.waitUntil(15_000) {
                    compose.onAllNodes(hasText(mode.name) and isSelected()).fetchSemanticsNodes().size == 1
                }
                compose.onNodeWithText(mode.name).assertIsSelected().assertHeightIsAtLeast(48.dp)
                scenario.recreate()
                await(mode.name); compose.onNodeWithText(mode.name).assertIsSelected()
                scenario.close()
                scenario = ActivityScenario.launch(MainActivity::class.java)
                await("More"); compose.onNodeWithText("More").performClick()
                compose.onNodeWithText(mode.name).performScrollTo().assertIsSelected()
            }
            compose.onNodeWithText("Light").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Light") and isSelected() and isEnabled()).fetchSemanticsNodes().size == 1 }
            compose.onNodeWithText("Stock").performClick(); await("Add product")
            compose.onNodeWithText("Add product").performClick(); await("Save product")
            val action = compose.onNodeWithText("Save product").performScrollTo()
            action.assertHasClickAction().assertIsEnabled().assertHeightIsAtLeast(48.dp)
            val pixels = action.captureToImage().toPixelMap()
            // Independent SDK Fluent Brand Color80 (#0F6CBD), not the previous purple.
            assertTrue("Primary action must render Fluent Brand", (0 until pixels.height).any { y ->
                (0 until pixels.width).any { x -> pixels[x, y] == Color(0xFF0F6CBD) }
            })
            action.performClick()
            await("Enter a product title")
        } finally {
            scenario.close()
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }
    @Test fun secondaryActionUsesFluentOutlineAndRetainsItsCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = runBlocking { Shop.open(context).use { shop -> shop.snapshot().appearance.also { shop.setAppearance(Appearance.Light) } } }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Stock").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Stock").performClick()
            compose.onNodeWithText("Add product").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Choose template").fetchSemanticsNodes().isNotEmpty() }
            val action = compose.onNodeWithText("Choose template").performScrollTo()
            action.assertHasClickAction().assertHeightIsAtLeast(48.dp)
            val pixels = action.captureToImage().toPixelMap()
            assertTrue("Secondary outline must render Fluent Brand", (0 until minOf(4, pixels.height)).any { y ->
                pixels[pixels.width / 2, y] == Color(0xFF0F6CBD)
            })
            action.performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Product templates").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            scenario.close()
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }

    @Test fun effectiveAppearanceAndButtonsRemainUsableAtNormalAndDoubleText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }
        val scale = shell("settings get system font_scale").ifBlank { "1.0" }
        val night = shell("settings get secure ui_night_mode")
        val original = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        fun await(label: String) = compose.waitUntil(20_000) {
            compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        fun capture(name: String) {
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
        }
        try {
            for (font in listOf("1.0", "2.0")) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                shell("settings put system font_scale $font")
                // Explicit appearance must override the opposite device brightness.
                shell("cmd uimode night ${if (mode == Appearance.Light) "yes" else "no"}")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    await("More"); compose.onNodeWithText("More").performClick()
                    compose.onNodeWithText(mode.name).performScrollTo().assertIsSelected().assertHeightIsAtLeast(48.dp)
                    scenario.onActivity { activity ->
                        val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                        assertEquals(mode == Appearance.Light, bars.isAppearanceLightStatusBars)
                        assertEquals(mode == Appearance.Light, bars.isAppearanceLightNavigationBars)
                    }
                    capture("pilot-more-${mode.name.lowercase()}-$font")
                    compose.onNodeWithText("Stock").performClick(); await("Add product")
                    compose.onNodeWithText("Add product").performClick(); await("Save product")
                    val action = compose.onNodeWithText("Save product").performScrollTo()
                    action.assertHeightIsAtLeast(48.dp).assertHasClickAction()
                    // Android enters keyboard mode after a physical navigation key.
                    shell("input keyevent 61")
                    action.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
                    action.assertIsFocused()
                    capture("pilot-button-focus-${mode.name.lowercase()}-$font")
                    action.performClick(); await("Enter a product title")
                    val field = compose.onNodeWithText("Title (required)").performScrollTo()
                    field.performTextInput("Workshop brake pad with a long product description")
                    field.assertIsFocused().assertIsDisplayed()
                    capture("pilot-keyboard-${mode.name.lowercase()}-$font")
                }
            }
            shell("settings put system font_scale 1.0")
            runBlocking { Shop.open(context).use { it.setAppearance(Appearance.System) } }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await("More"); compose.onNodeWithText("More").performClick()
                compose.onNodeWithText("System").performScrollTo().assertIsSelected()
                for (dark in listOf(false, true, false)) {
                    shell("cmd uimode night ${if (dark) "yes" else "no"}")
                    compose.waitUntil(20_000) {
                        var matches = false
                        scenario.onActivity { activity ->
                            val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                            matches = bars.isAppearanceLightStatusBars == !dark && bars.isAppearanceLightNavigationBars == !dark
                        }
                        matches
                    }
                    compose.onNodeWithText("System").assertIsSelected()
                }
            }
        } finally {
            shell("settings put system font_scale $scale")
            shell("cmd uimode night ${when (night) { "2" -> "yes"; "1" -> "no"; else -> "auto" }}")
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }

    @Test fun longBarcodeTargetKeepsSelectionColorsAndCallbackAtDoubleText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }
        val originalScale = shell("settings get system font_scale").ifBlank { "1.0" }
        val title = "Long barcode target workshop replacement front and rear brake pad with several fitment descriptions ${System.nanoTime()}"
        val original = runBlocking { Shop.open(context).use { shop ->
            val appearance = shop.snapshot().appearance
            shop.createProduct(title, shop.snapshot().categories.first().id, "", "4", "800000")
            appearance
        } }
        var frame: ((String) -> Unit)? = null
        fun await(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        try {
            shell("settings put system font_scale 2.0")
            for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity -> activity.setContent {
                        com.nadim.bikepart.ui.ShopApp(camera = { result, _ ->
                            androidx.compose.runtime.SideEffect { frame = result }
                            androidx.compose.material3.Text("Pilot camera transport")
                        })
                    } }
                    await("Add product"); compose.onNodeWithText("Add product").performClick()
                    compose.onNodeWithText("Scan catalog details").performScrollTo().performClick()
                    await("Pilot camera transport"); compose.runOnIdle { requireNotNull(frame)("pilot-${System.nanoTime()}") }
                    await("Link barcode"); compose.onNodeWithText("Link barcode").performScrollTo().performClick()
                    compose.onNodeWithText("Find barcode target").performTextInput(title)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    val target = compose.onNodeWithText("Product: $title").performScrollTo()
                    target.assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()
                    target.assertIsSelected().assertIsDisplayed()
                    val image = target.captureToImage().toPixelMap()
                    // SDK BrandBackgroundTint light #CFE4FA / dark #0A2E4A, independently pinned upstream values.
                    val expected = if (mode == Appearance.Light) Color(0xFFCFE4FA) else Color(0xFF0A2E4A)
                    assertTrue("Selected custom color is preserved", (0 until image.height).any { y ->
                        (0 until image.width).any { x -> image[x, y] == expected }
                    })
                    val file = java.io.File(context.getExternalFilesDir(null), "pilot-long-selected-${mode.name.lowercase()}-2.0.png")
                    instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                    }
                    shell("cp ${file.absolutePath} /data/local/tmp/${file.name}")
                    compose.onNodeWithText("Save barcode link").performScrollTo().assertIsEnabled()
                    compose.onNodeWithText("Cancel barcode link").performScrollTo().performClick()
                    await("Unknown barcode. Use manual search or explicitly link it.")
                }
            }
        } finally {
            shell("settings put system font_scale $originalScale")
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }

}
