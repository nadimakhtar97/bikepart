package com.nadim.bikepart

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlin.system.measureNanoTime

/** Reproducible diagnostic measurement, without device-speed assertions. */
class SearchMeasurementTest {
    @Test fun measurePreparedSubstringSearchOnFiveThousandPersistedCatalogProducts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "search-measurement-${UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val categories = (0..3).map { shop.createCategory("Measurement category $it") }
                val diameters = categories.map { shop.createAttribute(it, "Diameter", AttributeType.Decimal, unit = "mm") }
                val finishes = categories.map { shop.createAttribute(it, "Finish", AttributeType.SingleChoice) }
                val options = finishes.map { shop.createOption(it, "Zinc plated") }
                val makes = (0..3).map { shop.createMake("Workshop make $it") }
                val models = (0..19).map { shop.createModel(makes[it % 4], "Touring $it") }
                val locations = (0..9).map { shop.createLocation("Shelf $it") }
                repeat(5_000) { i ->
                    shop.createProduct("Workshop flanged bolt $i", categories[i % 4], "Stainless fastener for engine mounting, front and rear workshop repairs", (i % 17).toString(), "999999",
                        allowSimilar = true, attributes = mapOf(diameters[i % 4] to AttributeValue.Decimal(if (i % 2 == 0) "008.00" else "10.000"), finishes[i % 4] to AttributeValue.Choices(setOf(options[i % 4]))),
                        modelIds = setOf(models[i % 20]), locationIds = setOf(locations[i % 10]))
                }
            }
            Shop.open(context, name).use { shop ->
                lateinit var snapshot: ShopSnapshot
                val snapshotMs = measureNanoTime { snapshot = shop.snapshot() } / 1_000_000.0
                lateinit var search: CatalogSearch
                val buildMs = measureNanoTime { search = CatalogSearch(snapshot) } / 1_000_000.0
                assertEquals(5_000, search.find().size)
                assertEquals(2_500, search.find(ProductFilter(query = "8.000 zinc")).size)
                val filters = listOf(
                    ProductFilter(query = "flanged"), ProductFilter(query = "stainless zinc rear"),
                    ProductFilter(query = "8.000"), ProductFilter(query = "no-such-part"),
                    ProductFilter(query = "touring", categoryId = snapshot.categories.last().id, health = StockHealth.InStock),
                    ProductFilter(query = "shelf 8", health = StockHealth.OutOfStock),
                    ProductFilter(query = snapshot.products.last().sku),
                )
                repeat(3) { filters.forEach { search.find(it) } }
                val samples = (0 until 70).map { i -> measureNanoTime { search.find(filters[i % filters.size]) } / 1_000_000.0 }.sorted()
                val rebuildMs = (0 until 7).map { i -> measureNanoTime { CatalogSearch(snapshot).find(filters[i]) } / 1_000_000.0 }.sorted()
                val report = "5000 persisted products; snapshotMs=$snapshotMs; preparationMs=$buildMs; prepared query p50Ms=${samples[35]}, p95Ms=${samples[66]}, maxMs=${samples.last()}; rebuild-per-query medianMs=${rebuildMs[3]}"
                Log.i("SearchMeasurement", report)
                println(report)
                java.io.File(context.getExternalFilesDir(null), "search-measurement.txt").writeText(report)
            }
        } finally { context.deleteDatabase(name) }
    }
}
