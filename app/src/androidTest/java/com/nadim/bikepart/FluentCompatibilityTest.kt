package com.nadim.bikepart

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Test

class FluentCompatibilityTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun trialAcceptsTextAndRespondsToButton() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { FluentCompatibilityTrial() } }
            compose.onNodeWithContentDescription("Trial input").performTextInput("Brake pad")
            compose.onNodeWithText("Apply").performClick()
            compose.onNodeWithText("Applied: Brake pad").assertIsDisplayed()
        }
    }
}
