package com.nadim.bikepart

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.Shop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeveloperOptionWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun addsDemoRecordsThroughMore() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val before = runBlocking { Shop.open(context).use { it.snapshot() } }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("More").performClick()
            val entry = compose.onNode(hasText("Developer Option") and hasClickAction())
            entry.performScrollTo().performClick()
            compose.onNodeWithText("Fill app with dummy data").performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(before, runBlocking { Shop.open(context).use { it.snapshot() } })

            compose.onNodeWithText("Fill app with dummy data").performClick()
            compose.onNode(hasText("Add dummy data") and hasClickAction()).performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("Dummy data added.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Back").performClick()
            entry.performScrollTo().assertIsDisplayed()
        }

        val after = runBlocking { Shop.open(context).use { it.snapshot() } }
        val demoProducts = after.products.filter { product -> before.products.none { it.id == product.id } }
        assertEquals(4, demoProducts.size)
        assertTrue(demoProducts.all { it.title.startsWith("Demo ") })
        assertEquals(1, demoProducts.count { it.archived })
        assertEquals(16L, demoProducts.single { it.title.endsWith("brake pads") }.quantity)
        assertEquals(before.templates.size + 1, after.templates.size)
        assertEquals(before.carts.size + 1, after.carts.size)
        assertEquals(before.sales.size + 1, after.sales.size)
        assertTrue(after.products.containsAll(before.products))
    }
}
