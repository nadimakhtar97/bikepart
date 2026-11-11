package com.nadim.bikepart.shop

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

enum class LabelMedia(val label: String, val widthMm: Int, val heightMm: Int) {
    Package("Package · 50 × 30 mm", 50, 30),
    SharedBin("Shared bin · 70 × 40 mm", 70, 40),
}

data class LabelPreferences(val media: LabelMedia = LabelMedia.Package, val copies: Long = 1)

fun parseLabelCopies(text: String): Long = parseWhole(text, ProductField.LabelCopies).also {
    if (it == 0L) throw ShopValidation(ProductField.LabelCopies, "Label copies must be a positive whole number")
}

object LabelQr {
    /** Four white modules on every edge; callers render whole modules without filtering. */
    fun modules(payload: String): BitMatrix {
        require(ItemQr.parse(payload).kind == "product") { "Labels require a stocked product." }
        return QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    }
}
