package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TemplatesTest {
    @Test fun changingTemplateCategoryRetainsHistoricalValuesWithoutAssigningThemToCopies() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "template-category-${java.util.UUID.randomUUID()}.db"
        try {
            lateinit var saved: ShopSnapshot
            Shop.open(context, name).use { shop ->
                val source = shop.createCategory("Original category")
                val target = shop.createCategory("New category")
                val field = shop.createAttribute(source, "Length", AttributeType.Decimal, unit = "mm")
                val template = shop.saveTemplate(CatalogDraft("Reusable assembly", source, attributes = mapOf(field to AttributeValue.Decimal("008.00"))))
                val draft = shop.snapshot().templates.single().catalog.copy(categoryId = target, attributes = emptyMap())
                shop.saveTemplate(draft, template)
                saved = shop.snapshot()
                assertEquals(1, saved.templates.single().catalog.historicalAttributes.size)
                assertEquals(field, saved.templates.single().catalog.historicalAttributes.single().definitionId)
                assertEquals(AttributeValue.Decimal("008.00"), saved.templates.single().catalog.historicalAttributes.single().value)
                assertTrue(saved.prefillTemplate(template).historicalAttributes.isEmpty())
                shop.createFromCatalog(saved.prefillTemplate(template), "0", "90")
                assertTrue(shop.snapshot().products.single().attributes.isEmpty())
                assertTrue(shop.snapshot().products.single().historicalAttributes.isEmpty())
                saved = shop.snapshot()
            }
            Shop.open(context, name).use { assertEquals(saved, it.snapshot()) }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun templateAndCopyWritesRollBackWhenRevisionCommitIsInterrupted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "template-interruption-${java.util.UUID.randomUUID()}.db"
        try {
            lateinit var before: ShopSnapshot
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val shared = shop.createAttribute(category, "Material", AttributeType.Text)
                val draft = CatalogDraft("Manual template", category, attributes = mapOf(shared to AttributeValue.Text("Steel")),
                    customFields = listOf(DraftField("draft-field", "Maker code", AttributeType.Text, false, "")))
                val template = shop.saveTemplate(draft)
                before = shop.snapshot()
                android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                    it.execSQL("CREATE TRIGGER fail_template_revision BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interrupted revision'); END")
                }
                val writes: List<suspend () -> Unit> = listOf(
                    { shop.saveTemplate(draft.copy(title = "Another")) },
                    { shop.saveTemplate(draft.copy(title = "Changed"), template) },
                    { shop.retireTemplate(template) },
                    { shop.createFromCatalog(before.prefillTemplate(template), "7", "90") },
                )
                for (write in writes) {
                    try { write(); fail("Revision interruption must reject the whole write") }
                    catch (_: android.database.sqlite.SQLiteException) { }
                    assertEquals(before, shop.snapshot())
                }
            }
            Shop.open(context, name).use { assertEquals(before, it.snapshot()) }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun incompleteTemplatesAndRetiredMeaningsCannotInventNewProductValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "template-validation-${java.util.UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val required = shop.createAttribute(category, "Required code", AttributeType.Text, true)
                val template = shop.saveTemplate(CatalogDraft("Incomplete", category))
                val before = shop.snapshot()
                try { shop.createFromCatalog(before.prefillTemplate(template), "0", "50"); fail("Required value must be entered") } catch (_: ShopValidation) { }
                assertEquals(before, shop.snapshot())
                val complete = before.prefillTemplate(template).copy(attributes = mapOf(required to AttributeValue.Text("A")))
                val product = shop.createFromCatalog(complete, "0", "50")
                assertEquals(mapOf(required to AttributeValue.Text("A")), shop.snapshot().products.single().attributes)
                assertTrue(shop.snapshot().attributes.single { it.id == required }.productId == null)
                shop.saveTemplate(complete, template)
                shop.retireAttribute(required)
                assertTrue(shop.snapshot().copyCatalog(product).attributes.isEmpty())
                shop.saveTemplate(complete, template)
                Unit
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun allCopyRoutesOwnCustomChoicesAndExcludeInventory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "templates-${java.util.UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val source = shop.createProduct("Pad", category, "Original", "12", "80", photo = PhotoChange.Import { "abc".byteInputStream() })
                val custom = shop.createCustomAttribute(source, "Finish", AttributeType.SingleChoice)
                val option = shop.createOption(custom, "Zinc")
                shop.createOption(custom, "Black")
                val shared = shop.snapshot().attributes.single { it.categoryId == category && it.productId == null }.id
                val sharedOption = shop.snapshot().options.first { it.definitionId == shared }.id
                val make = shop.createMake("Honda")
                val model = shop.createModel(make, "Shine")
                val location = shop.createLocation("Shelf")
                shop.editProduct(source, "Pad", category, "Original", mapOf(custom to AttributeValue.Choices(setOf(option)), shared to AttributeValue.Choices(setOf(sharedOption))), modelIds = setOf(model), locationIds = setOf(location))
                val before = shop.snapshot()
                val draft = before.copyCatalog(source)
                try {
                    shop.saveTemplate(draft, photo = PhotoChange.Import { throw java.io.IOException("Disconnected photo picker") })
                    fail("Incomplete photo transport must not create a template")
                } catch (_: java.io.IOException) { }
                assertEquals(before, shop.snapshot())
                assertEquals(before, shop.snapshot()) // Opening/cancelling a draft writes nothing.
                val template = shop.saveTemplate(draft)
                assertEquals(before.revision + 1, shop.snapshot().revision)
                assertEquals(1, shop.snapshot().products.size)
                val direct = shop.createFromCatalog(draft.copy(title = "Direct"), "0", "99")
                val savedTemplate = shop.snapshot().templates.single()
                val derived = shop.createFromCatalog(shop.snapshot().prefillTemplate(template).copy(title = "Derived"), "3", "100")
                for (id in listOf(direct, derived)) {
                    val snapshot = shop.snapshot()
                    val product = snapshot.products.single { it.id == id }
                    assertTrue(product.locationIds.isEmpty())
                    assertEquals(setOf(model), product.modelIds)
                    assertNotEquals(before.products.single().sku, product.sku)
                    assertArrayEquals("abc".toByteArray(), shop.readPhoto(product.photo!!))
                    val field = snapshot.attributes.single { it.productId == id }
                    assertNotEquals(custom, field.id)
                    assertEquals(setOf("Black", "Zinc"), snapshot.options.filter { it.definitionId == field.id }.map { it.name }.toSet())
                    assertEquals(AttributeValue.Choices(setOf(sharedOption)), product.attributes[shared])
                    val selected = (product.attributes.getValue(field.id) as AttributeValue.Choices).optionIds.single()
                    assertNotEquals(option, selected)
                    assertEquals("Zinc", snapshot.options.single { it.id == selected }.name)
                }
                assertEquals(emptyList<OpeningStock>(), shop.openingHistory(direct))
                assertEquals(3L, shop.openingHistory(derived).single().quantity)
                assertEquals(before.products.single(), shop.snapshot().products.single { it.id == source })
                assertEquals(99L, shop.snapshot().products.single { it.id == direct }.priceRupees)
                assertEquals(before.revision + 3, shop.snapshot().revision)
                val products = shop.snapshot().products
                shop.saveTemplate(savedTemplate.catalog.copy(title = "Changed template"), template)
                shop.retireOption(option)
                shop.editProduct(source, "Changed source", category, "", emptyMap(), photo = PhotoChange.Remove)
                assertEquals(products.filter { it.id != source }, shop.snapshot().products.filter { it.id != source })
                shop.retireTemplate(template)
                val retired = shop.snapshot()
                assertTrue(retired.templates.single().retired)
                assertEquals("Changed template", retired.templates.single().catalog.title)
                assertArrayEquals("abc".toByteArray(), shop.readPhoto(retired.templates.single().catalog.photo!!))
                try { retired.prefillTemplate(template); fail("Retired templates cannot be selected") } catch (_: IllegalArgumentException) { }
                shop.retireTemplate(template)
                assertEquals(retired, shop.snapshot())
            }
            Shop.open(context, name).use { shop ->
                assertEquals(3, shop.snapshot().products.size)
                assertTrue(shop.snapshot().templates.single().retired)
            }
        } finally { context.deleteDatabase(name) }
    }
}
