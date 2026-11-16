package com.nadim.bikepart

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The real MainActivity and Room-backed shop, operated through staff-visible controls. */
class FluentDynamicThemeWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun await(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun selectedBrandSurvivesActivityAndFreshLaunch() {
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            await("More"); compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Excel").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText("Excel").assertIsSelected()
            scenario.recreate()
            await("Excel"); compose.onNodeWithText("Excel").performScrollTo().assertIsSelected()
            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("More"); compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Excel").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Fluent Brand").performScrollTo().performClick()
        } finally { scenario.close() }
    }
    @Test fun accentChangesVisibleNavigationAndNativeSwitchWithoutClearingBrandOrShopData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
            compose.onNodeWithText("Excel").performScrollTo().performClick()
            val accent = compose.onNodeWithText("Accent").performScrollTo()
            accent.assertIsOn().assertHeightIsAtLeast(48.dp)
            assertTrue("Native switch immediately uses selected Excel", containsColor(accent, Color(0xFF107C41)))
            val branded = compose.onNodeWithText("More").captureToImage()
            accent.performClick(); accent.assertIsOff()
            val neutral = compose.onNodeWithText("More").captureToImage()
            val brandedPixels = branded.toPixelMap(); val neutralPixels = neutral.toPixelMap()
            assertFalse("Accent visibly changes selected navigation chrome", (0 until branded.height).all { y -> (0 until branded.width).all { x -> brandedPixels[x,y] == neutralPixels[x,y] } })
            compose.onNodeWithText("Excel").performScrollTo().assertIsSelected()
            scenario.close(); scenario = ActivityScenario.launch(MainActivity::class.java)
            await("More"); compose.onNodeWithText("More").performClick()
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
            runBlocking { Shop.open(context).use { it.setAppearance(original) } }
        }
    }

}
