package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProductPhotosTest {
    @Test fun interruptedImportsAndRevisionFailuresLeaveTheCommittedPhotoAndStockIntact() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "photo-interruption-${java.util.UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val id = shop.createProduct("Pad", category, "", "12", "80", photo = PhotoChange.Import { "original".byteInputStream() })
                val before = shop.snapshot()
                val broken = PhotoChange.Import { object : java.io.InputStream() {
                    var remaining = 100_000
                    override fun read(): Int {
                        if (--remaining < 0) throw java.io.IOException("Disconnected source")
                        return 1
                    }
                } }
                try { shop.editProduct(id, "Changed", category, "", photo = broken); fail("Import must fail") }
                catch (_: java.io.IOException) { }
                assertEquals(before, shop.snapshot())
                android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                    it.execSQL("CREATE TRIGGER fail_photo_revision BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interrupted revision'); END")
                }
                for (change in listOf(PhotoChange.Remove, PhotoChange.Import { "replacement".byteInputStream() })) {
                    try { shop.editProduct(id, "Changed", category, "", photo = change); fail("Revision must fail") }
                    catch (_: android.database.sqlite.SQLiteException) { }
                    assertEquals(before, shop.snapshot())
                }
                try { shop.createProduct("New", category, "", "5", "90", photo = PhotoChange.Import { "new".byteInputStream() }); fail("Revision must fail") }
                catch (_: android.database.sqlite.SQLiteException) { }
                assertEquals(before, shop.snapshot())
                assertArrayEquals("original".toByteArray(), shop.readPhoto(before.products.single().photo!!))
            }
            Shop.open(context, name).use { shop ->
                assertEquals("Pad", shop.snapshot().products.single().title)
                assertEquals(1L, shop.snapshot().revision)
                assertEquals(12L, shop.openingHistory(shop.snapshot().products.single().id).single().quantity)
                assertArrayEquals("original".toByteArray(), shop.readPhoto(shop.snapshot().products.single().photo!!))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun emptyOversizedAndCancelledImportsCannotCreateAProduct() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "photo-limits-${java.util.UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                val before = shop.snapshot()
                for (source in listOf<() -> java.io.InputStream>(
                    { byteArrayOf().inputStream() },
                    { object : java.io.InputStream() {
                        override fun read() = 1
                        override fun read(bytes: ByteArray, offset: Int, length: Int): Int { bytes.fill(1, offset, offset + length); return length }
                    } },
                    { throw kotlinx.coroutines.CancellationException("Import cancelled") },
                )) {
                    try { shop.createProduct("Pad", before.categories.first().id, "", "12", "80", photo = PhotoChange.Import(source)); fail("Must reject incomplete input") }
                    catch (_: ShopValidation) { }
                    catch (_: kotlinx.coroutines.CancellationException) { }
                    assertEquals(before, shop.snapshot())
                }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun missingAndCorruptBytesReturnNoImageWithoutChangingProductIdentityOrRevision() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "photo-corrupt-${java.util.UUID.randomUUID()}.db"
        try {
            Shop.open(context, name).use { shop ->
                shop.createProduct("Pad", shop.snapshot().categories.first().id, "", "12", "80", photo = PhotoChange.Import { "abc".byteInputStream() })
                val before = shop.snapshot()
                val reference = before.products.single().photo!!
                val directory = File(context.filesDir, "product-photos/" + java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) })
                val blob = File(directory, reference.sha256)
                blob.writeText("xyz")
                assertNull(shop.readPhoto(reference))
                blob.delete()
                assertNull(shop.readPhoto(reference))
                assertEquals(before, shop.snapshot())
                shop.editProduct(before.products.single().id, "Still editable", before.categories.first().id, "")
                assertEquals(12L, shop.snapshot().products.single().quantity)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun ownedPhotosSurviveSourceDeletionReplacementRemovalAndReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "photos-${java.util.UUID.randomUUID()}.db"
        val source = File(context.cacheDir, "$name.source").apply { writeText("abc") }
        try {
            lateinit var first: PhotoReference
            lateinit var original: Product
            Shop.open(context, name).use { shop ->
                val category = shop.snapshot().categories.first().id
                val id = shop.createProduct("Pad", category, "", "12", "80", photo = PhotoChange.Import { source.inputStream() })
                original = shop.snapshot().products.single()
                first = requireNotNull(original.photo)
                assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", first.sha256)
                assertEquals(3L, first.sizeBytes)
                assertEquals(1L, shop.snapshot().revision)
                source.delete()
                assertArrayEquals("abc".toByteArray(), shop.readPhoto(first))
                shop.editProduct(id, "Pad", category, "", photo = PhotoChange.Import { "replacement".byteInputStream() })
                assertNotEquals(first, shop.snapshot().products.single().photo)
                assertArrayEquals("abc".toByteArray(), shop.readPhoto(first))
                assertEquals(2L, shop.snapshot().revision)
            }
            Shop.open(context, name).use { shop ->
                val product = shop.snapshot().products.single()
                assertArrayEquals("replacement".toByteArray(), shop.readPhoto(requireNotNull(product.photo)))
                assertEquals(original.copy(photo = product.photo), product)
                shop.editProduct(product.id, product.title, product.categoryId, "", photo = PhotoChange.Remove)
                assertEquals(3L, shop.snapshot().revision)
            }
            Shop.open(context, name).use { shop ->
                assertEquals(original.copy(photo = null), shop.snapshot().products.single())
                assertArrayEquals("abc".toByteArray(), shop.readPhoto(first))
                assertEquals(listOf(OpeningStock(12, shop.openingHistory(original.id).single().recordedAt)), shop.openingHistory(original.id))
                assertEquals(3L, shop.snapshot().revision)
            }
        } finally { source.delete(); context.deleteDatabase(name) }
    }
}
