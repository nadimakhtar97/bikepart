package com.nadim.bikepart

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
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
}
