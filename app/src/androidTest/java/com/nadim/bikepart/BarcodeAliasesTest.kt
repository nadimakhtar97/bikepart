package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BarcodeAliasesTest {
    @Test fun scopesExactTextInactiveTargetsAndStaleConfirmationsAreSafe() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "alias-scopes-${UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val a = BarcodeTarget(BarcodeTargetKind.Product, shop.createProduct("First", category, "", "0", "80"))
                val b = BarcodeTarget(BarcodeTargetKind.Product, shop.createProduct("Second", category, "", "0", "80"))
                val t = BarcodeTarget(BarcodeTargetKind.Template, shop.saveTemplate(CatalogDraft("Template", category)))
                shop.linkBarcode("00123", a)
                val old = shop.snapshot().barcodeAliases.single().revision
                shop.linkBarcode("00123", b, "supplier")
                shop.linkBarcode(" ００123 ", t)
                val scoped = shop.snapshot()
                assertEquals(a.id, (scoped.resolveScan("00123") as ScanTarget.StockedProduct).product.id)
                assertEquals(b.id, (scoped.resolveScan("00123", "supplier") as ScanTarget.StockedProduct).product.id)
                assertEquals(t.id, (scoped.resolveScan(" ００123 ") as ScanTarget.Template).template.id)
                shop.linkBarcode("00123", b, expectedAssignmentRevision = old)
                shop.linkBarcode("00123", a, expectedAssignmentRevision = shop.snapshot().barcodeAliases.single { it.namespace == "external" && it.code == "00123" }.revision)
                val before = shop.snapshot()
                try { shop.linkBarcode("00123", t, expectedAssignmentRevision = old); fail("Stale ABA confirmation") } catch (_: BarcodeConflict) { }
                for ((namespace, code) in listOf("bikepart" to "other", "external" to "BIKEPART:1:bad", "external" to "")) {
                    try { shop.linkBarcode(code, a, namespace); fail("Reserved/empty barcode") } catch (_: IllegalArgumentException) { }
                }
                assertEquals(before, shop.snapshot())
                shop.archiveProduct(a.id)
                shop.retireTemplate(t.id)
                val inactive = shop.snapshot()
                for (code in listOf("00123", " ００123 ", "unknown")) {
                    try { inactive.resolveScan(code); fail("Inactive or unknown code") } catch (_: IllegalArgumentException) { }
                }
                for (target in listOf(a, t, BarcodeTarget(BarcodeTargetKind.Product, "missing"))) {
                    try { shop.linkBarcode("new", target); fail("Inactive/missing target") } catch (_: IllegalArgumentException) { }
                }
                assertEquals(inactive, shop.snapshot())
                Shop.open(context, name).use { assertEquals(inactive, it.snapshot()) }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun interruptedAssignmentsRollBackOwnershipAuditAndRevision() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (fault in listOf("alias", "audit", "revision", "overflow")) {
            val name = "alias-fault-${UUID.randomUUID()}.db"
            try {
                Shop.open(context, name).use { shop ->
                    val category = shop.snapshot().categories.first().id
                    val first = BarcodeTarget(BarcodeTargetKind.Product, shop.createProduct("First", category, "", "0", "80"))
                    val second = BarcodeTarget(BarcodeTargetKind.Template, shop.saveTemplate(CatalogDraft("Second", category)))
                    shop.linkBarcode("0001", first)
                    val expected = shop.snapshot().barcodeAliases.single().revision
                    android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { db ->
                        when (fault) {
                            "alias" -> db.execSQL("CREATE TRIGGER fail_write BEFORE UPDATE ON barcode_aliases BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                            "audit" -> db.execSQL("CREATE TRIGGER fail_write BEFORE INSERT ON barcode_assignments BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                            "revision" -> db.execSQL("CREATE TRIGGER fail_write BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                            else -> db.execSQL("UPDATE shop_state SET revision = 9223372036854775807")
                        }
                    }
                    val before = shop.snapshot()
                    try { shop.linkBarcode("0001", second, expectedAssignmentRevision = expected); fail("Partial assignment") }
                    catch (_: android.database.sqlite.SQLiteException) { } catch (_: ArithmeticException) { }
                    assertEquals(before, shop.snapshot())
                    Shop.open(context, name).use { assertEquals(before, it.snapshot()) }
                    if (fault != "overflow") {
                        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { it.execSQL("DROP TRIGGER fail_write") }
                        shop.linkBarcode("0001", second, expectedAssignmentRevision = expected)
                        val committed = shop.snapshot()
                        assertEquals(second.id, (committed.resolveScan("0001") as ScanTarget.Template).template.id)
                        assertEquals(before.revision + 1, committed.revision)
                        assertEquals(2, committed.barcodeHistory.size)
                    }
                }
            } finally { context.deleteDatabase(name) }
        }
    }

    @Test fun exactAliasesConflictAndConfirmedReassignmentSurviveReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "aliases-${UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val first = BarcodeTarget(BarcodeTargetKind.Product, shop.createProduct("Bolt", category, "", "4", "80"))
                val second = BarcodeTarget(BarcodeTargetKind.Template, shop.saveTemplate(CatalogDraft("Template", category)))
                val before = shop.snapshot()
                shop.linkBarcode("00123", first)
                val linked = shop.snapshot()
                assertEquals(before.revision + 1, linked.revision)
                assertEquals("00123", linked.barcodeAliases.single().code)
                assertEquals(first.id, (linked.resolveScan("00123") as ScanTarget.StockedProduct).product.id)
                shop.linkBarcode("00123", first)
                assertEquals(linked, shop.snapshot())
                try { shop.linkBarcode("00123", second); fail("Silent reassignment") } catch (_: BarcodeConflict) { }
                assertEquals(linked, shop.snapshot())
                for (bad in listOf("123", "００１２３", " 00123", "bikepart:2:bad")) {
                    try { linked.resolveScan(bad); fail("Normalized or unknown code") } catch (_: IllegalArgumentException) { }
                }
                try { shop.linkBarcode("bikepart:2:bad", first); fail("Reserved namespace") } catch (_: IllegalArgumentException) { }
                shop.linkBarcode("00123", second, expectedAssignmentRevision = linked.barcodeAliases.single().revision)
                val reassigned = shop.snapshot()
                assertEquals(linked.revision + 1, reassigned.revision)
                assertEquals(second.id, (reassigned.resolveScan("00123") as ScanTarget.Template).template.id)
                assertEquals(first, reassigned.barcodeHistory.last().previousTarget)
                assertEquals(second, reassigned.barcodeHistory.last().target)
                assertEquals(before.products, reassigned.products)
                Shop.open(context, name).use { assertEquals(reassigned, it.snapshot()) }
            }
        } finally { context.deleteDatabase(name) }
    }
}
