package com.nadim.bikepart

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Test

class FluentCompatibilityTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun trialAcceptsTextAndRespondsToButton() {
        ActivityScenario.launch(FluentCompatibilityActivity::class.java).use {
            compose.onNodeWithContentDescription("Trial input").performTextInput("Brake pad")
            compose.onNodeWithText("Apply").performClick()
            compose.onNodeWithText("Applied: Brake pad").assertIsDisplayed()
        }
    }
}
