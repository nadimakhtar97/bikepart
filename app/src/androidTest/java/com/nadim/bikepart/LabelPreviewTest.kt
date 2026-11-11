package com.nadim.bikepart

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.renderProductLabel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LabelPreviewTest {
    @Test fun renderedProfilesDecodeAndPreferencesSurviveCatalogChangesAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "labels-${UUID.randomUUID()}.db"
        try {
            var payload = ""
            Shop.open(context, name).use { shop ->
                val id = shop.createProduct("Very long stainless steel motorcycle replacement mounting bolt for rear brake assembly", shop.snapshot().categories.first().id, "", "12", "80")
                val before = shop.snapshot()
                val product = before.products.single()
                payload = ItemQr.product(before.datasetId, id)
                for (media in LabelMedia.entries) {
                    val bitmap = renderProductLabel(product, payload, media)
                    assertEquals(media.widthMm * 8, bitmap.width)
                    assertEquals(media.heightMm * 8, bitmap.height)
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertEquals(payload, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text)
                    bitmap.recycle()
                }
                assertEquals(before, shop.snapshot())
                for (invalid in listOf("", "0", "-1", "1.5", "１", "9223372036854775808")) {
                    try { shop.setLabelPreferences(LabelMedia.SharedBin, invalid); fail("Invalid copies: $invalid") } catch (_: ShopValidation) { }
                    assertEquals(before, shop.snapshot())
                }
                shop.setLabelPreferences(LabelMedia.SharedBin, " 3 ")
                val saved = shop.snapshot()
                assertEquals(before.copy(revision = before.revision + 1, labelPreferences = LabelPreferences(LabelMedia.SharedBin, 3)), saved)
                shop.setLabelPreferences(LabelMedia.SharedBin, "03")
                assertEquals(saved, shop.snapshot())
                shop.editProduct(id, "Renamed bolt", product.categoryId, "Updated")
                shop.setSellingPrice(id, "90")
                val edited = shop.snapshot()
                assertEquals(payload, ItemQr.product(edited.datasetId, edited.products.single().id))
                assertEquals(product.quantity, edited.products.single().quantity)
                assertEquals(product.sku, edited.products.single().sku)
            }
            Shop.open(context, name).use { shop ->
                val state = shop.snapshot()
                assertEquals(LabelPreferences(LabelMedia.SharedBin, 3), state.labelPreferences)
                assertEquals(payload, ItemQr.product(state.datasetId, state.products.single().id))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun interruptedSettingsCommitAndRevisionOverflowPreserveAllShopState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (overflow in listOf(false, true)) {
            val name = "labels-fault-${UUID.randomUUID()}.db"
            try {
                Shop.open(context, name).use { shop ->
                    shop.setLabelPreferences(LabelMedia.SharedBin, "2")
                    SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { db ->
                        if (overflow) db.execSQL("UPDATE shop_state SET revision = 9223372036854775807")
                        else db.execSQL("CREATE TRIGGER fail_write BEFORE INSERT ON shop_state BEGIN SELECT RAISE(ABORT, 'interruption'); END")
                    }
                    val before = shop.snapshot()
                    try { shop.setLabelPreferences(LabelMedia.Package, "4"); fail("Interrupted write") }
                    catch (_: android.database.sqlite.SQLiteException) { } catch (_: ArithmeticException) { }
                    assertEquals(before, shop.snapshot())
                    Shop.open(context, name).use { assertEquals(before, it.snapshot()) }
                }
            } finally { context.deleteDatabase(name) }
        }
    }
}
