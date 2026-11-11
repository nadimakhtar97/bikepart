package com.nadim.bikepart

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nadim.bikepart.shop.ItemQr
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class OfflineQrRecognitionTest {
    @Test fun bundledRecognizerReadsReviewedPayloadWithAllNetworksDisabled() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = automation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
        val airplane = shell("settings get global airplane_mode_on").trim()
        val wifi = shell("settings get global wifi_on").trim()
        val data = shell("settings get global mobile_data").trim()
        val payload = "bikepart:1:12345678-1234-1234-1234-123456789abc:product:abcdefab-1234-1234-1234-123456789abc"
        val qr = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        for (y in 0 until 512) for (x in 0 until 512) bitmap.setPixel(x, y, if (qr[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        try {
            shell("cmd connectivity airplane-mode enable")
            shell("svc wifi disable")
            shell("svc data disable")
            BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()).use { scanner ->
                val result = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
                assertEquals(payload, result.single().rawValue)
                assertEquals("abcdefab-1234-1234-1234-123456789abc", ItemQr.parse(result.single().rawValue!!).id)
            }
        } finally {
            bitmap.recycle()
            shell("cmd connectivity airplane-mode ${if (airplane == "1") "enable" else "disable"}")
            shell("svc wifi ${if (wifi == "1") "enable" else "disable"}")
            shell("svc data ${if (data == "1") "enable" else "disable"}")
        }
    }
}
