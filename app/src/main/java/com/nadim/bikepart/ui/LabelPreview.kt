package com.nadim.bikepart.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

/** Label artwork uses printer dots, independent of app brightness and system text scaling. */
internal fun renderProductLabel(product: Product, payload: String, media: LabelMedia): Bitmap {
    val dotsPerMm = 8
    val width = media.widthMm * dotsPerMm
    val height = media.heightMm * dotsPerMm
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val qr = LabelQr.modules(payload)
    val module = (height - 16) / qr.height
    val qrSize = qr.width * module
    val top = (height - qrSize) / 2
    val ink = Paint().apply { color = Color.BLACK; isAntiAlias = false }
    for (y in 0 until qr.height) for (x in 0 until qr.width) if (qr[x, y]) {
        canvas.drawRect((8 + x * module).toFloat(), (top + y * module).toFloat(),
            (8 + (x + 1) * module).toFloat(), (top + (y + 1) * module).toFloat(), ink)
    }
    val left = 8 + qrSize + 8
    val textWidth = width - left - 8
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = 22f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    val title = StaticLayout.Builder.obtain(product.title, 0, product.title.length, titlePaint, textWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
        .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).build()
    val skuPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = 17f; typeface = Typeface.MONOSPACE
    }
    val sku = StaticLayout.Builder.obtain(product.sku, 0, product.sku.length, skuPaint, textWidth)
        .setIncludePad(false).build()
    canvas.withTranslation(left.toFloat(), 12f) {
        title.draw(this)
        translate(0f, title.height + 12f)
        sku.draw(this)
    }
    return bitmap
}

@Composable
internal fun LabelPreview(shop: ShopSnapshot, product: Product, busy: Boolean, error: ShopError?,
    onChange: () -> Unit,
    usbAccess: (android.content.Context, () -> Boolean) -> com.nadim.bikepart.printing.LabelUsbAccess,
    save: (LabelMedia, String) -> Unit) {
    var mediaName by rememberSaveable(product.id) { mutableStateOf(shop.labelPreferences.media.name) }
    var copies by rememberSaveable(product.id) { mutableStateOf(shop.labelPreferences.copies.toString()) }
    // Only primitives enter saved instance state.
    var previewMedia by rememberSaveable(product.id) { mutableStateOf<String?>(null) }
    var previewCopies by rememberSaveable(product.id) { mutableLongStateOf(1) }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    val copiesRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(validation, error) {
        if (validation != null || error != null) copiesRequester.bringIntoView()
    }
    val media = LabelMedia.valueOf(mediaName)
    fun changed() { previewMedia = null; validation = null; onChange() }
    Text("Labels identify this product, including a shared loose-item bin. They do not identify individual pieces or locations.")
    ShopSection("Media and copies") {
        Column(Modifier.selectableGroup()) { LabelMedia.entries.forEach { option ->
            Row(Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)
                .selectable(media == option, enabled = !busy, role = Role.RadioButton, onClick = { mediaName = option.name; changed() }),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = media == option, onClick = null)
                Text(option.label, Modifier.padding(start = ShopSpacing.small))
            }
        } }
        Text("Load the ${media.widthMm} × ${media.heightMm} mm roll. Changing profile requires changing the physical roll and calibrating the printer before printing.")
        OutlinedTextField(copies, { copies = it; changed() }, label = { Text("Label copies") },
            enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = validation != null || error?.field == ProductField.LabelCopies,
            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(copiesRequester))
        (validation ?: error?.message)?.let { FormError(it) }
        ShopPrimaryButton("Preview label", enabled = !busy) {
            try { previewCopies = parseLabelCopies(copies); previewMedia = media.name }
            catch (e: ShopValidation) { validation = e.message }
        }
        ShopSecondaryButton(onClick = { save(media, copies) }, enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Save label preferences") }
        Text("Saved default: ${shop.labelPreferences.media.label} · ${shop.labelPreferences.copies} copies",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    previewMedia?.let { name ->
        val selected = LabelMedia.valueOf(name)
        val payload = ItemQr.product(shop.datasetId, product.id)
        val bitmap = remember(product.title, product.sku, payload, selected) { renderProductLabel(product, payload, selected) }
        val artwork = remember(bitmap) { bitmap.asImageBitmap() }
        ShopSection("Label preview") {
            Text("${selected.label} · $previewCopies copies")
            val artworkShape = MaterialTheme.shapes.small
            Image(artwork, contentDescription = "Product label QR: $payload", filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxWidth().aspectRatio(selected.widthMm.toFloat() / selected.heightMm)
                    .clip(artworkShape).border(1.dp, MaterialTheme.colorScheme.outline, artworkShape))
            Text(product.title, style = MaterialTheme.typography.titleMedium)
            Text("SKU: ${product.sku}")
            Text("Artwork shows up to three title lines. Full product title is shown above. Price and stock are omitted.")
            key(selected, previewCopies, bitmap) {
                LabelPrintControls(selected, previewCopies, product.sku, bitmap, usbAccess) { copies = ""; changed() }
            }
        }
    }
}
