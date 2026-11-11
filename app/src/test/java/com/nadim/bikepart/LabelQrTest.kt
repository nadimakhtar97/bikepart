package com.nadim.bikepart

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.nadim.bikepart.shop.*
import org.junit.Assert.*
import org.junit.Test

class LabelQrTest {
    @Test fun finalLabelPayloadDecodesWithQuietZoneAndStableIdentity() {
        val payload = "bikepart:1:11111111-1111-1111-1111-111111111111:product:22222222-2222-2222-2222-222222222222"
        val qr = LabelQr.modules(payload)
        val scale = 8
        val width = qr.width * scale
        val pixels = IntArray(width * width) { index ->
            if (qr[index % width / scale, index / width / scale]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        assertEquals(payload, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, width, pixels)))).text)
        for (y in 0 until qr.height) for (x in 0 until qr.width) {
            if (x < 4 || y < 4 || x >= qr.width - 4 || y >= qr.height - 4) assertFalse(qr[x, y])
        }
        assertEquals(ItemQr.Identity("11111111-1111-1111-1111-111111111111", "product", "22222222-2222-2222-2222-222222222222"), ItemQr.parse(payload))
        try { LabelQr.modules("invalid"); fail("Malformed label identity") } catch (_: IllegalArgumentException) { }
    }
}
