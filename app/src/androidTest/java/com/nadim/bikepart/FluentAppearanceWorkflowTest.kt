package com.nadim.bikepart

import androidx.compose.ui.test.*
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

}
