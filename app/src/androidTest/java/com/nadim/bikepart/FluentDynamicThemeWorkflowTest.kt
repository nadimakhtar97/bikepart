package com.nadim.bikepart

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.activity.compose.setContent
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The real MainActivity and Room-backed shop, operated through staff-visible controls. */
@OptIn(ExperimentalTestApi::class)
class FluentDynamicThemeWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun await(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun selectedBrandSurvivesActivityAndFreshLaunch() {
        val preferences = com.nadim.bikepart.ui.theme.ShopThemePreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = preferences.state.value
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            await("More"); compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Appearance").performClick()
            compose.onNodeWithText("Excel").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText("Excel").assertIsSelected()
            scenario.recreate()
            await("Excel"); compose.onNodeWithText("Excel").performScrollTo().assertIsSelected()
            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("More"); compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Appearance").performClick()
            compose.onNodeWithText("Excel").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Fluent Brand").performScrollTo().performClick()
        } finally { scenario.close(); preferences.setBrand(original.brand); preferences.setAccent(original.accent) }
    }
    @Test fun accentChangesVisibleNavigationAndNativeSwitchWithoutClearingBrandOrShopData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = com.nadim.bikepart.ui.theme.ShopThemePreferences(context)
        val originalCustomization = preferences.state.value
        preferences.setAccent(true)
        val original = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        runBlocking { Shop.open(context).use { it.setAppearance(Appearance.Light) } }
        val before = runBlocking { Shop.open(context).use { it.snapshot() } }
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        fun containsColor(node: SemanticsNodeInteraction, color: Color): Boolean {
            val image = node.captureToImage().toPixelMap()
            return (0 until image.height).any { y -> (0 until image.width).any { x -> image[x,y] == color } }
        }
        try {
            await("More"); compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Appearance").performClick()
            compose.onNodeWithText("Excel").performScrollTo().performClick()
            val accent = compose.onNodeWithText("Accent").performScrollTo()
            accent.assertIsOn().assertHeightIsAtLeast(48.dp)
            compose.onAllNodes(isToggleable()).assertCountEquals(1)
            assertTrue("Native switch immediately uses selected Excel", containsColor(accent, Color(0xFF107C41)))
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 61").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            accent.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
            accent.assertIsFocused().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Spacebar) }
            accent.assertIsOff().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Spacebar) }
            accent.assertIsOn()
            compose.onNodeWithContentDescription("Back").performClick()
            val branded = compose.onNode(hasText("More") and hasClickAction()).captureToImage()
            compose.onNodeWithText("Appearance").performClick()
            accent.performScrollTo()
            // Hit the native switch itself; its callback must toggle once without a nested accessible action.
            accent.performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 30f, center.y)) }
            accent.assertIsOff()
            compose.onNodeWithContentDescription("Back").performClick()
            val neutral = compose.onNode(hasText("More") and hasClickAction()).captureToImage()
            compose.onNodeWithText("Appearance").performClick()
            accent.performScrollTo()
            val brandedPixels = branded.toPixelMap(); val neutralPixels = neutral.toPixelMap()
            assertFalse("Accent visibly changes selected navigation chrome", (0 until branded.height).all { y -> (0 until branded.width).all { x -> brandedPixels[x,y] == neutralPixels[x,y] } })
            compose.onNodeWithText("Excel").performScrollTo().assertIsSelected()
            scenario.close(); scenario = ActivityScenario.launch(MainActivity::class.java)
            await("More"); compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Appearance").performClick()
            compose.onNodeWithText("Excel").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Accent").performScrollTo().assertIsOff().performClick()
            compose.onNodeWithText("OneNote").performScrollTo().performClick()
            assertTrue("Existing native switch updates immediately to OneNote", containsColor(compose.onNodeWithText("Accent").performScrollTo(), Color(0xFF7719AA)))
            compose.onNodeWithText("Light").performScrollTo().assertIsSelected()
            val after = runBlocking { Shop.open(context).use { it.snapshot() } }
            assertEquals("Customization never changes shop records or revision", before, after)
            compose.onNodeWithText("Fluent Brand").performScrollTo().performClick()
        } finally {
            scenario.close()
            preferences.setBrand(originalCustomization.brand); preferences.setAccent(originalCustomization.accent)
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }

    @Test fun explicitThemeReplacementPreservesUnfinishedInputAndNavigation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = com.nadim.bikepart.ui.theme.ShopThemePreferences(context)
        val original = preferences.state.value
        val before = runBlocking { Shop.open(context).use { it.snapshot() } }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            await("Stock"); compose.onNodeWithText("Stock").performClick()
            compose.onNodeWithText("Add product").performClick(); await("Title (required)")
            compose.onNodeWithText("Title (required)").performScrollTo().performTextInput("Unfinished workshop brake pad")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            // Public app-owned settings operation while an unrelated real form remains mounted.
            scenario.onActivity { activity ->
                val settings = androidx.lifecycle.ViewModelProvider(activity)[ShopViewModel::class.java].themePreferences
                settings.setBrand(com.nadim.bikepart.ui.theme.ShopBrand.OneNote); settings.setAccent(false)
            }
            compose.onNodeWithText("Title (required)").performScrollTo().assertTextContains("Unfinished workshop brake pad")
            compose.onNodeWithText("Save product").performScrollTo().assertIsEnabled()
            scenario.recreate(); await("Title (required)")
            compose.onNodeWithText("Title (required)").performScrollTo().assertTextContains("Unfinished workshop brake pad")
            compose.onNodeWithText("New product").assertIsDisplayed()
            assertEquals(before, runBlocking { Shop.open(context).use { it.snapshot() } })
        } finally {
            scenario.close(); preferences.setBrand(original.brand); preferences.setAccent(original.accent)
        }
    }

    @Test fun shopRecordsAndStoredAppearanceSurviveCustomizationAndReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = com.nadim.bikepart.ui.theme.ShopThemePreferences(context)
        val original = preferences.state.value
        val originalAppearance = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        val seededData = runBlocking { Shop.open(context).use { shop ->
            shop.setAppearance(Appearance.Dark)
            val product = shop.createProduct("Theme retained pad ${System.nanoTime()}", shop.snapshot().categories.first().id, "", "4", "80")
            val completed = shop.createCart(); shop.saveCart(completed, 0, "Theme completed customer", mapOf(product to "1"))
            shop.checkout(completed, 1, "100")
            val unfinished = shop.createCart(); shop.saveCart(unfinished, 0, "Theme unfinished customer", mapOf(product to "2"))
            product to shop.snapshot()
        } }
        val (productId, seeded) = seededData
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            await("More"); compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Appearance").performClick()
            compose.onNodeWithText("PowerPoint").performScrollTo().performClick()
            val accent = compose.onNodeWithText("Accent").performScrollTo()
            if (compose.onAllNodes(hasText("Accent") and isOn()).fetchSemanticsNodes().isNotEmpty()) accent.performClick()
            scenario.close(); scenario = ActivityScenario.launch(MainActivity::class.java)
            await("More"); compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Appearance").performClick()
            compose.onNodeWithText("PowerPoint").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Accent").performScrollTo().assertIsOff()
            compose.onNodeWithText("Dark").performScrollTo().assertIsSelected()
            val reopened = runBlocking { Shop.open(context).use { it.snapshot() } }
            assertEquals("Products, quantities, saved carts, immutable sales and revision retained", seeded, reopened)
            assertEquals(3L, reopened.products.single { it.id == productId }.quantity)
        } finally {
            scenario.close(); preferences.setBrand(original.brand); preferences.setAccent(original.accent)
            runBlocking { Shop.open(context).use { it.setAppearance(originalAppearance) } }
        }
    }

    @Test fun everyBrandRendersAtNormalAndDoubleTextWithReadableChrome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = com.nadim.bikepart.ui.theme.ShopThemePreferences(context)
        val original = preferences.state.value
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }
        val scale = shell("settings get system font_scale").ifBlank { "1.0" }
        val night = shell("settings get secure ui_night_mode")
        val originalAppearance = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        fun capture(name: String) {
            compose.waitForIdle(); instrumentation.waitForIdleSync()
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            // UiAutomation.takeScreenshot can return null during repeated API37 captures.
            // Capture the same actual display through the shell service and verify a real PNG.
            val descriptor = instrumentation.uiAutomation.executeShellCommand("screencap -p")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val bitmap = checkNotNull(android.graphics.BitmapFactory.decodeFile(file.absolutePath)) {
                "Actual display capture must be a valid PNG: $name"
            }
            assertTrue("Actual display capture must have content", bitmap.width > 0 && bitmap.height > 0)
            bitmap.recycle()
            shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
        }
        try {
            preferences.setAccent(true)
            for (font in listOf("1.0", "2.0")) for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                shell("settings put system font_scale $font")
                shell("cmd uimode night ${if (mode == Appearance.Light) "yes" else "no"}")
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    await("More"); compose.onNodeWithText("More").performClick()
                    compose.onNodeWithText("Appearance").performClick()
                    for (brand in com.nadim.bikepart.ui.theme.ShopBrand.entries) {
                        compose.onNodeWithText(brand.label).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                        compose.onNodeWithText(brand.label).assertIsSelected().assertIsDisplayed()
                        capture("dynamic-${brand.name.lowercase()}-${mode.name.lowercase()}-$font")
                    }
                    compose.onNodeWithText("Accent").performScrollTo().assertIsOn().assertHeightIsAtLeast(48.dp)
                    capture("dynamic-controls-brand-${mode.name.lowercase()}-$font")
                    compose.onNodeWithText("Accent").performClick(); compose.onNodeWithText("Accent").assertIsOff()
                    capture("dynamic-controls-neutral-${mode.name.lowercase()}-$font")
                    // Native Brand app bars become neutral in dark, and Neutral app bars stay neutral in both modes.
                    for (accent in listOf(false, true)) {
                        scenario.onActivity { activity -> androidx.lifecycle.ViewModelProvider(activity)[ShopViewModel::class.java]
                            .themePreferences.setAccent(accent) }
                        compose.onNodeWithText("Stock").performClick(); await("Add product")
                        compose.onNodeWithText("Add product").performClick(); await("New product")
                        scenario.onActivity { activity ->
                            val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                            assertEquals("Icons follow actual native header background", mode == Appearance.Light && !accent, bars.isAppearanceLightStatusBars)
                            assertEquals(mode == Appearance.Light, bars.isAppearanceLightNavigationBars)
                        }
                        capture("dynamic-header-${if (accent) "brand" else "neutral"}-${mode.name.lowercase()}-$font")
                        compose.onNodeWithContentDescription("Back").performClick(); await("More")
                        compose.onNodeWithText("More").performClick()
                    }
                }
                preferences.setAccent(true)
            }
        } finally {
            shell("settings put system font_scale $scale")
            shell("cmd uimode night ${when (night) { "2" -> "yes"; "1" -> "no"; else -> "auto" }}")
            preferences.setBrand(original.brand); preferences.setAccent(original.accent)
            runBlocking { Shop.open(context).use { it.setAppearance(originalAppearance) } }
        }
    }

    @Test fun selectedWordBarcodeBoundaryRemainsVisibleAndCancelRetainsItsCallbackAtDoubleText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = com.nadim.bikepart.ui.theme.ShopThemePreferences(context)
        val original = preferences.state.value
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }
        val scale = shell("settings get system font_scale").ifBlank { "1.0" }
        val appearance = runBlocking { Shop.open(context).use { it.snapshot().appearance } }
        val title = "Word selected workshop replacement front and rear brake pad with long fitment descriptions ${System.nanoTime()}"
        runBlocking { Shop.open(context).use { it.createProduct(title, it.snapshot().categories.first().id, "", "4", "800000") } }
        var frame: ((String) -> Unit)? = null
        try {
            preferences.setBrand(com.nadim.bikepart.ui.theme.ShopBrand.Word)
            shell("settings put system font_scale 2.0")
            for (mode in listOf(Appearance.Light, Appearance.Dark)) {
                runBlocking { Shop.open(context).use { it.setAppearance(mode) } }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    // Existing approved camera-transport substitution; the shop, scanner route and Room stay real.
                    scenario.onActivity { activity -> activity.setContent {
                        com.nadim.bikepart.ui.ShopApp(camera = { result, _ ->
                            androidx.compose.runtime.SideEffect { frame = result }
                            androidx.compose.material3.Text("Dynamic theme camera transport")
                        })
                    } }
                    await("Add product"); compose.onNodeWithText("Add product").performClick()
                    compose.onNodeWithText("Scan catalog details").performScrollTo().performClick()
                    await("Dynamic theme camera transport")
                    compose.runOnIdle { requireNotNull(frame)("dynamic-word-${System.nanoTime()}") }
                    await("Link barcode"); compose.onNodeWithText("Link barcode").performScrollTo().performClick()
                    compose.onNodeWithText("Find barcode target").performTextInput(title)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    val target = compose.onNodeWithText("Product: $title").performScrollTo()
                    target.assertHeightIsAtLeast(48.dp).performClick(); target.assertIsSelected().assertIsDisplayed()
                    val pixels = target.captureToImage().toPixelMap()
                    // Independent pinned demo Word Color140 / Color30 selected-container values.
                    val fill = if (mode == Appearance.Light) Color(0xFF82ABF1) else Color(0xFF0E336A)
                    val boundary = if (mode == Appearance.Light) Color.Black else Color.White
                    assertTrue("Actual selected Word container is retained", (0 until pixels.height).any { y ->
                        (0 until pixels.width).any { x -> pixels[x,y] == fill }
                    })
                    assertTrue("Actual selected boundary is visible", (0 until minOf(5,pixels.height)).any { y ->
                        pixels[pixels.width / 2,y] == boundary
                    })
                    val high = maxOf(fill.luminance(), boundary.luminance())
                    val low = minOf(fill.luminance(), boundary.luminance())
                    assertTrue("Actual adjacent selected boundary meets 3:1", (high + .05f) / (low + .05f) >= 3f)
                    val file = java.io.File(context.getExternalFilesDir(null), "dynamic-word-selected-${mode.name.lowercase()}-2.0.png")
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("screencap -p")).use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    shell("cp ${file.absolutePath} /data/local/tmp/${file.name}")
                    compose.onNodeWithText("Save barcode link").performScrollTo().assertIsEnabled()
                    compose.onNodeWithText("Cancel barcode link").performScrollTo().performClick()
                    await("Unknown barcode. Use manual search or explicitly link it.")
                }
            }
        } finally {
            shell("settings put system font_scale $scale")
            preferences.setBrand(original.brand); preferences.setAccent(original.accent)
            runBlocking { Shop.open(context).use { it.setAppearance(appearance) } }
        }
    }

}
