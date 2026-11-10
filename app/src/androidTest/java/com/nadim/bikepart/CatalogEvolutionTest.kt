package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CatalogEvolutionTest {
    @Test fun customDefinitionsStayProductOwnedUntilExplicitConflictCheckedPromotion() = runBlocking {
        withShop { shop ->
            val category = shop.snapshot().categories.first().id
            val first = shop.createProduct("First", category, "", "12", "80")
            val second = shop.createProduct("Second", category, "", "0", "0")
            val field = shop.createCustomAttribute(first, " Finish ", AttributeType.SingleChoice)
            assertEquals(field, shop.createCustomAttribute(first, "ＦＩＮＩＳＨ", AttributeType.SingleChoice))
            val other = shop.createCustomAttribute(second, "Finish", AttributeType.Text)
            assertNotEquals(field, other)
            val option = shop.createOption(field, "Straße")
            assertEquals(option, shop.createOption(field, "STRASSE"))
            val value = mapOf(field to AttributeValue.Choices(setOf(option)))
            shop.editProduct(first, "First", category, "", value)
            rejects { shop.editProduct(second, "Second", category, "", value) }
            shop.createAttribute(category, "Finish", AttributeType.Text)
            val before = shop.snapshot()
            rejects { shop.promoteCustomAttribute(field, "FINISH") }
            assertEquals(before, shop.snapshot())
            shop.promoteCustomAttribute(field, "Special finish")
            val after = shop.snapshot()
            assertEquals(before.revision + 1, after.revision)
            assertNull(after.attributes.single { it.id == field }.productId)
            assertEquals(value, after.products.single { it.id == first }.attributes)
            assertEquals(12L, shop.openingHistory(first).single().quantity)
        }
    }

    @Test fun requiredAndRetiredMeaningsSurviveReplacementAndReopen() = runBlocking {
        withShop { shop ->
            val category = shop.snapshot().categories.first().id
            val id = shop.createProduct("Pad", category, "", "12", "80")
            val field = shop.createAttribute(category, "Grade", AttributeType.SingleChoice, required = true)
            assertEquals(setOf(field), shop.snapshot().missingRequired(id))
            assertEquals(12L, shop.snapshot().products.single().quantity)
            val before = shop.snapshot()
            rejects { shop.editProduct(id, "Changed", category, "") }
            assertEquals(before, shop.snapshot())
            val option = shop.createOption(field, "HH")
            val values = mapOf(field to AttributeValue.Choices(setOf(option)))
            shop.editProduct(id, "Pad", category, "", values)
            shop.retireOption(option)
            shop.editProduct(id, "Pad retained", category, "", values)
            rejects { shop.createProduct("Other", category, "", "0", "0", attributes = values) }
            val revision = shop.snapshot().revision
            val replacement = shop.replaceAttribute(field, "Grade number", AttributeType.Decimal)
            assertEquals(revision + 1, shop.snapshot().revision)
            assertTrue(shop.snapshot().attributes.single { it.id == field }.retired)
            assertEquals(values, shop.snapshot().products.single().attributes)
            shop.editProduct(id, "Pad", category, "", values + (replacement to AttributeValue.Decimal("1.00")))
            shop.retireAttribute(replacement)
            val after = shop.snapshot()
            rejects { shop.editProduct(id, "Pad", category, "", values + (replacement to AttributeValue.Decimal("2"))) }
            assertEquals(after, shop.snapshot())
        }
    }

    @Test fun categoryChangesPreserveHistoryAndOnlyMapExplicitCompatibleValues() = runBlocking {
        withShop { shop ->
            val first = shop.createCategory("Source")
            val second = shop.createCategory("Target")
            val old = shop.createAttribute(first, "Length", AttributeType.Decimal, unit = "mm")
            val target = shop.createAttribute(second, "Length", AttributeType.Decimal, unit = "mm")
            val wrong = shop.createAttribute(second, "Inches", AttributeType.Decimal, unit = "in")
            val id = shop.createProduct("Bolt", first, "Original", "12", "80", attributes = mapOf(old to AttributeValue.Decimal("8.00")))
            val custom = shop.createCustomAttribute(id, "Note", AttributeType.Text)
            shop.editProduct(id, "Bolt", first, "Original", mapOf(old to AttributeValue.Decimal("8.00"), custom to AttributeValue.Text("Special")))
            val before = shop.snapshot()
            rejects { shop.editProduct(id, "Bolt", second, "", categoryMappings = mapOf(old to wrong)) }
            assertEquals(before, shop.snapshot())
            shop.editProduct(id, "Bolt", second, "", categoryMappings = mapOf(old to target))
            val moved = shop.snapshot().products.single()
            assertEquals(mapOf(old to AttributeValue.Decimal("8.00")), moved.historicalAttributes.associate { it.definitionId to it.value })
            assertEquals(mapOf(target to AttributeValue.Decimal("8.00"), custom to AttributeValue.Text("Special")), moved.attributes)
            assertEquals(before.revision + 1, shop.snapshot().revision)
            shop.editProduct(id, "Bolt", first, "Back")
            val back = shop.snapshot().products.single()
            assertEquals(mapOf(old to AttributeValue.Decimal("8.00"), target to AttributeValue.Decimal("8.00")), back.historicalAttributes.associate { it.definitionId to it.value })
            assertEquals(mapOf(custom to AttributeValue.Text("Special")), back.attributes)
            assertEquals(before.products.single().sku, back.sku)
            assertEquals(12L, back.quantity)
            assertEquals(80L, back.priceRupees)
            shop.editProduct(id, "Bolt", first, "", back.attributes + (old to AttributeValue.Decimal("9.00")))
            shop.editProduct(id, "Bolt", second, "Again")
            assertEquals(setOf("8.00", "9.00"), shop.snapshot().products.single().historicalAttributes.filter { it.definitionId == old }.map { (it.value as AttributeValue.Decimal).exact }.toSet())
        }
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Must reject invalid change") } catch (_: IllegalArgumentException) { }
    }
    private suspend fun withShop(action: suspend (Shop) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "evolution-${java.util.UUID.randomUUID()}.db"
        try {
            val committed = Shop.open(context, name).use { shop -> action(shop); shop.snapshot() }
            Shop.open(context, name).use { assertEquals(committed, it.snapshot()) }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun interruptedRevisionCommitRollsBackEveryEvolutionWrite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "evolution-failure-${java.util.UUID.randomUUID()}.db"
        try {
            val before = Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val target = shop.createCategory("Target")
                val product = shop.createProduct("Pad", category, "", "12", "80")
                val custom = shop.createCustomAttribute(product, "Custom", AttributeType.Text)
                val choice = shop.createAttribute(category, "Finish", AttributeType.SingleChoice)
                val option = shop.createOption(choice, "HH")
                shop.editProduct(product, "Pad", category, "", mapOf(custom to AttributeValue.Text("Exact"), choice to AttributeValue.Choices(setOf(option))))
                val original = shop.snapshot()
                android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                    it.execSQL("CREATE TRIGGER fail_revision BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interrupted revision'); END")
                }
                for (write in listOf<suspend () -> Unit>(
                    { shop.createCustomAttribute(product, "New custom", AttributeType.Text) },
                    { shop.promoteCustomAttribute(custom, "Shared") },
                    { shop.retireAttribute(custom) },
                    { shop.retireOption(option) },
                    { shop.setAttributeRequired(custom, true) },
                    { shop.replaceAttribute(custom, "Replacement", AttributeType.Decimal) },
                    { shop.editProduct(product, "Moved", target, "") },
                )) {
                    try { write(); fail("Interrupted write must fail") } catch (_: android.database.sqlite.SQLiteException) { }
                    assertEquals(original, shop.snapshot())
                }
                original
            }
            Shop.open(context, name).use {
                assertEquals(before, it.snapshot())
                assertEquals(12L, it.openingHistory(before.products.single().id).single().quantity)
            }
        } finally { context.deleteDatabase(name) }
    }
}
