package com.nadim.bikepart

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FindingLabelWorkflowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    @Before fun launchApp() { scenario = ActivityScenario.launch(MainActivity::class.java) }
    @After fun closeApp() { scenario.close() }

    @Test fun findingLabelsKeepSpacesAndRetiredLocationsRemainReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString()
        val title = "Finding label product $suffix"
        val make = "Finding label make $suffix"
        val model = "Finding label model $suffix"
        val location = "Shelf A $suffix"
        scenario.close()
        kotlinx.coroutines.runBlocking {
            com.nadim.bikepart.shop.Shop.open(context).use { shop ->
                val makeId = shop.createMake(make)
                val modelId = shop.createModel(makeId, model)
                val locationId = shop.createLocation(location)
                shop.createProduct(title, shop.snapshot().categories.first().id, "", "12", "80",
                    modelIds = setOf(modelId), locationIds = setOf(locationId))
            }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        fun await(text: String) = compose.waitUntil(15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        fun click(text: String, scroll: Boolean = true) {
            await(text)
            val node = compose.onNodeWithText(text)
            if (scroll) node.performScrollTo()
            node.performClick()
        }
        fun openProduct() {
            click("Stock", scroll = false)
            click(title)
            await("12 pieces")
        }
        fun capture(name: String) {
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        openProduct()
        compose.onNodeWithText("Location: $location").performScrollTo().assertIsDisplayed()
        capture("finding-label-active")
        click("Back", scroll = false)
        click("More", scroll = false)
        click("Manage fitment and locations")
        compose.onNodeWithText("Find make, model or location").performTextInput(suffix)
        for (name in listOf(make, "$make · $model", location)) {
            click("Retire: $name")
            await("$name (retired)")
            compose.onNodeWithText("$name (retired)").performScrollTo().assertIsDisplayed()
        }
        capture("finding-label-retirement")
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        openProduct()
        compose.onNodeWithText("Location: $location (retired)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("$make · $model (retired)").performScrollTo().assertIsDisplayed()
        capture("finding-label-retired")
        compose.onNodeWithText("12 pieces").performScrollTo().assertIsDisplayed()
    }
}
