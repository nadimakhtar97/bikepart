package com.nadim.bikepart

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.microsoft.fluentui.tokenized.notification.SNACK_BAR
import com.nadim.bikepart.shop.Shop
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class ErrorSnackbarWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun invalidCategoryRenameShowsSnackbarAndBackClearsIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = runBlocking { Shop.open(context).use { it.snapshot().categories.first().name } }
        val message = "Enter a category name"
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("More").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasText("More") and hasClickAction()).performClick()
            compose.onNodeWithText("Manage categories and fields").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Rename $name").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Category name").performTextClearance()
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Save $name").performClick()
            val snackbar = compose.onNodeWithTag(SNACK_BAR)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
            }
            snackbar.assertIsDisplayed()
            Espresso.pressBack()
            compose.onNodeWithText("Appearance").assertExists()
            snackbar.assertDoesNotExist()
            compose.onNodeWithText(message).assertDoesNotExist()
        }
    }
}
