package com.nadim.bikepart.ui

import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.PhotoReference
import com.nadim.bikepart.shop.MAX_PHOTO_BYTES
import com.nadim.bikepart.ui.theme.ShopSpacing
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal typealias PhotoLoader = suspend (PhotoReference) -> ByteArray?

@Composable
internal fun ProductPhoto(reference: PhotoReference?, load: PhotoLoader, modifier: Modifier = Modifier, selectedUri: String? = null, decodeSize: Int = 512) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<ImageBitmap?>(null, reference, selectedUri, decodeSize) {
        value = null
        try {
            value = withContext(Dispatchers.IO) {
                val bytes = if (selectedUri != null) resolver.openInputStream(selectedUri.toUri())?.use { input ->
                    input.readNBytes(MAX_PHOTO_BYTES.toInt() + 1).takeIf { it.size <= MAX_PHOTO_BYTES }
                } else reference?.let { load(it) }
                bytes?.let {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(it))) { decoder, info, _ ->
                        val largest = maxOf(info.size.width, info.size.height)
                        decoder.setTargetSampleSize(((largest.toLong() + decodeSize - 1) / decodeSize).toInt().coerceAtLeast(1))
                    }.asImageBitmap()
                }
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { value = null }
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        val image = bitmap
        if (image != null) Image(image, "Product photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_stock),
                contentDescription = if (reference == null && selectedUri == null) "No product photo" else "Photo unavailable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
internal fun PhotoField(reference: PhotoReference?, uri: String?, removed: Boolean, busy: Boolean,
    load: PhotoLoader, error: String?, select: (String) -> Unit, remove: () -> Unit) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { selected -> selected?.let { select(it.toString()) } }
    ShopSection("Product photo (optional)") {
        val hasPhoto = uri != null || (!removed && reference != null)
        ProductPhoto(if (removed) null else reference, load, Modifier.fillMaxWidth().height(160.dp), uri)
        Text("Choose a photo from your phone. Saved photos stay with the product. Maximum 20 MB.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) {
            Text(if (hasPhoto) "Replace photo" else "Choose photo")
        }
        if (hasPhoto) OutlinedButton(enabled = !busy, onClick = remove,
            modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Remove photo") }
        if (error != null) FormError(error)
    }
}
