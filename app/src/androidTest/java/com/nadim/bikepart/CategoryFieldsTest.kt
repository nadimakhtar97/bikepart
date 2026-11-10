package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CategoryFieldsTest {
    @Test fun independentlyCommittedLookupsReuseUnicodeNamesWithinTheirOwner() = runBlocking {
        withShop { shop ->
            val initial = shop.snapshot()
            assertEquals(8, initial.attributes.size)
            assertTrue(initial.attributes.none { it.required })
            assertEquals(setOf("Front", "Rear"), initial.options.map { it.name }.toSet())
            assertEquals(0L, initial.revision)
            val category = shop.createCategory("  Ｆasteners\u00a0 hardware  ")
            val revision = shop.snapshot().revision
            assertEquals(category, shop.createCategory("fasteners hardware"))
            assertEquals(revision, shop.snapshot().revision)
            val field = shop.createAttribute(category, " Finish ", AttributeType.SingleChoice)
            assertEquals(field, shop.createAttribute(category, "FINISH", AttributeType.SingleChoice))
            val option = shop.createOption(field, " Straße ")
            assertEquals(option, shop.createOption(field, "STRASSE"))
            val other = shop.createAttribute(category, "Other finish", AttributeType.MultipleChoice)
            assertNotEquals(option, shop.createOption(other, "strasse"))
            val otherCategory = shop.createCategory("Other category")
            assertNotEquals(field, shop.createAttribute(otherCategory, "Finish", AttributeType.SingleChoice))
            assertEquals("Fasteners hardware", shop.snapshot().categories.single { it.id == category }.name)
            assertEquals(7L, shop.snapshot().revision)
            assertTrue(shop.snapshot().products.isEmpty())
        }
    }
    @Test fun allFourTypesRetainExactValuesAndDecimalOrderingAfterReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "typed-" + java.util.UUID.randomUUID() + ".db"
        var shop = Shop.open(context, name)
        try {
            val category = shop.createCategory("Test parts")
            val text = shop.createAttribute(category, "Grade", AttributeType.Text)
            val decimal = shop.createAttribute(category, "Length", AttributeType.Decimal, unit = "mm")
            val single = shop.createAttribute(category, "Position", AttributeType.SingleChoice, required = true)
            val multiple = shop.createAttribute(category, "Finish", AttributeType.MultipleChoice)
            val front = shop.createOption(single, "Front")
            val zinc = shop.createOption(multiple, "Zinc")
            val steel = shop.createOption(multiple, "Steel")
            val before = shop.snapshot()
            rejects { shop.createProduct("Missing position", category, "", "0", "0") }
            assertEquals(before, shop.snapshot())
            val values = mapOf(text to AttributeValue.Text(""), decimal to AttributeValue.Decimal("0.123456789012"), single to AttributeValue.Choices(setOf(front)), multiple to AttributeValue.Choices(setOf(zinc, steel)))
            val id = shop.createProduct("Precision", category, "", "12", "80", attributes = values)
            val two = shop.createProduct("Two", category, "", "0", "0", attributes = values + (decimal to AttributeValue.Decimal("2.00")))
            val ten = shop.createProduct("Ten", category, "", "0", "0", attributes = values + (decimal to AttributeValue.Decimal("10")))
            val missing = shop.createProduct("Missing length", category, "", "0", "0", attributes = values - decimal)
            val product = shop.snapshot().products.single { it.id == id }
            shop.close()
            shop = Shop.open(context, name)
            val reopened = shop.snapshot()
            assertEquals(values, reopened.products.single { it.id == id }.attributes)
            assertEquals(listOf(id, two, ten, missing), productsByDecimal(reopened.products, decimal).map { it.id })
            assertEquals(product, reopened.products.single { it.id == id })
            assertEquals(12L, shop.openingHistory(id).single().quantity)
            assertEquals(12L, reopened.revision)
            for (invalid in listOf("NaN", "Infinity", "1e3", "1,5", ".5", "1.", "1.1234567890123", "1000000000000000000")) {
                rejects { shop.editProduct(id, "Precision", category, "", values + (decimal to AttributeValue.Decimal(invalid))) }
                assertEquals(reopened, shop.snapshot())
            }
            rejects { shop.editProduct(id, "Precision", category, "", values + (single to AttributeValue.Choices(setOf(zinc)))) }
            rejects { shop.editProduct(id, "Precision", category, "", values + (decimal to AttributeValue.Text("wrong"))) }
            assertEquals(reopened, shop.snapshot())
            shop.editProduct(id, "Precision", category, "", values + (decimal to AttributeValue.Decimal("999999999999999999.999999999999")))
            assertEquals(AttributeValue.Decimal("999999999999999999.999999999999"), shop.snapshot().products.single { it.id == id }.attributes[decimal])
        } finally { shop.close(); context.deleteDatabase(name) }
    }

    @Test fun interruptedAttributeAndLookupWritesNeverAdvanceRevisionOrPartiallySave() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "typed-failure-" + java.util.UUID.randomUUID() + ".db"
        var shop = Shop.open(context, name)
        try {
            val category = shop.snapshot().categories.first().id
            val field = shop.createAttribute(category, "Grade", AttributeType.Text)
            val before = shop.snapshot()
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                it.execSQL("CREATE TRIGGER fail_attribute BEFORE INSERT ON attribute_values BEGIN SELECT RAISE(ABORT, 'interruption'); END")
            }
            try {
                shop.createProduct("Pad", category, "", "12", "80", attributes = mapOf(field to AttributeValue.Text("HH")))
                fail("Storage failure must reject the save")
            } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(before, shop.snapshot())
            shop.close()
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                it.execSQL("UPDATE shop_state SET revision = 9223372036854775807")
            }
            shop = Shop.open(context, name)
            val overflow = shop.snapshot()
            for (write in listOf<suspend () -> Unit>(
                { shop.createCategory("New category") },
                { shop.createAttribute(category, "New field", AttributeType.MultipleChoice) },
                { shop.createOption(overflow.attributes.single { it.name == "Position" }.id, "Side") },
            )) {
                try { write(); fail("Revision overflow must reject the write") } catch (_: ArithmeticException) { }
                assertEquals(overflow, shop.snapshot())
            }
        } finally { shop.close(); context.deleteDatabase(name) }
    }

    @Test fun unicodeNormalizerHasExplicitWhitespaceNormalizationAndDefaultCaseFolding() {
        assertEquals(1, LookupNames.VERSION)
        assertEquals("café parts", LookupNames.key("  CAFE\u0301\u0085\u00a0Parts  "))
        assertEquals("οσ", LookupNames.key("ΟΣ"))
        assertEquals("οσ", LookupNames.key("ος"))
        assertEquals("strasse", LookupNames.key("Straße"))
        assertEquals("Fasteners hardware", LookupNames.display("  Ｆasteners\u00a0 hardware  "))
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Input must be rejected") } catch (_: IllegalArgumentException) { }
    }

    private suspend fun withShop(action: suspend (Shop) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "attributes-${java.util.UUID.randomUUID()}.db"
        val shop = Shop.open(context, name)
        try { action(shop) } finally { shop.close(); context.deleteDatabase(name) }
    }
}

