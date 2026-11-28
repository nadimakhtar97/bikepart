package com.nadim.bikepart.shop


import com.nadim.bikepart.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

enum class LabelMedia(@param:androidx.annotation.StringRes val label: Int, val widthMm: Int, val heightMm: Int) {
    Package(R.string.package_50_30_mm, 50, 30),
    SharedBin(R.string.shared_bin_70_40_mm, 70, 40),
}

data class LabelPreferences(val media: LabelMedia = LabelMedia.Package, val copies: Long = 1)

fun parseLabelCopies(context: android.content.Context, text: String): Long = parseWhole(context, text, ProductField.LabelCopies).also {
    if (it == 0L) throw ShopValidation(ProductField.LabelCopies, context.getString(R.string.label_copies_must_be_a_positive))
}

object LabelQr {
    /** Four white modules on every edge; callers render whole modules without filtering. */
    fun modules(payload: String): BitMatrix {
        require(ItemQr.parse(payload).kind == "product") { "Labels require a stocked product." }
        return QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    }
}
