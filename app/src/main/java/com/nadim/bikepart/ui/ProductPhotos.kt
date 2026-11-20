package com.nadim.bikepart.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.MAX_PHOTO_BYTES
import com.nadim.bikepart.shop.PhotoReference
import com.nadim.bikepart.ui.theme.ShopSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource

internal typealias PhotoLoader = suspend (PhotoReference) -> ByteArray?

@Composable
internal fun ProductPhoto(
    reference: PhotoReference?, load: PhotoLoader, modifier: Modifier = Modifier,
    selectedUri: String? = null, decodeSize: Int = 512, onImageSize: ((Int, Int) -> Unit)? = null
) {
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
    val reportSize by rememberUpdatedState(onImageSize)
    LaunchedEffect(bitmap) { bitmap?.let { reportSize?.invoke(it.width, it.height) } }
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
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { selected -> selected?.let { select(it.toString()) } }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showSourceDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val path = pendingCameraPath
        pendingCameraPath = null
        val file = path?.let { File(it) }
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK) {
            file?.delete()
            return@rememberLauncherForActivityResult
        }

        val selectedUri = try {
            when {
                file != null && file.isFile && file.length() > 200 ->
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

                data?.data != null -> data.data
                else -> {
                    @Suppress("DEPRECATION")
                    val bitmap = data?.extras?.get("data") as? Bitmap
                    if (bitmap != null && file != null) {
                        val saved = file.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        if (saved && file.isFile && file.length() > 200) {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                        } else null
                    } else null
                }
            }
        } catch (_: Exception) {
            null
        }

        if (selectedUri != null) {
            cameraError = null
            select(selectedUri.toString())
        } else {
            file?.delete()
            cameraError = "The camera didn't return a photo. Try again or choose from gallery."
        }
    }

    fun launchCamera() {
        try {
            cameraError = null
            val directory = File(context.cacheDir, "camera_photos").apply { mkdirs() }
            val file = File(directory, "photo_${System.currentTimeMillis()}.jpg")
            pendingCameraPath = file.absolutePath

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, contentUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            cameraError = "Could not open camera: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            cameraError = "Camera permission is required to take photos."
        }
    }

    ShopSection(stringResource(R.string.photo_section)) {
        val hasPhoto = uri != null || (!removed && reference != null)
        val description = stringResource(R.string.photo_description)
        if (hasPhoto) {
            val replaceLabel = stringResource(R.string.photo_replace)
            val photoShape = MaterialTheme.shapes.small
            // Shrink the frame to the photo's aspect ratio so the surface never shows beside it.
            var photoAspect by remember(uri, reference, removed) { mutableStateOf<Float?>(null) }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val ratio = photoAspect
                val previewHeight = if (ratio == null) 160.dp else minOf(160.dp, maxWidth / ratio)
                val previewWidth =
                    if (ratio == null) maxWidth else minOf(maxWidth, previewHeight * ratio)
                Box(Modifier
                    .size(previewWidth, previewHeight)
                    .align(Alignment.Center)) {
                    ProductPhoto(
                        if (removed) null else reference, load,
                        Modifier
                            .fillMaxSize()
                            .clip(photoShape)
                            .clickable(
                                enabled = !busy,
                                onClickLabel = replaceLabel
                            ) { showSourceDialog = true },
                        uri, onImageSize = { width, height ->
                            if (width > 0 && height > 0) photoAspect = width.toFloat() / height
                        })
                    IconButton(
                        onClick = remove, enabled = !busy,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(ShopSpacing.small)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = .6f), CircleShape)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.photo_remove),
                            tint = Color.White
                        )
                    }
                }
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShopSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductPhoto(null, load, Modifier.size(48.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            ShopSecondaryButton(
                enabled = !busy, onClick = { showSourceDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ShopSpacing.touchTarget)
            ) {
                Text(stringResource(R.string.photo_choose))
            }
        }
        val displayError = error ?: cameraError
        if (displayError != null) FormError(displayError)
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text(if (uri != null || (!removed && reference != null)) stringResource(R.string.photo_replace) else stringResource(R.string.photo_choose)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                    ShopSecondaryButton(
                        enabled = !busy,
                        onClick = {
                            showSourceDialog = false
                            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                launchCamera()
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ShopSpacing.touchTarget)
                    ) {
                        Text(stringResource(R.string.photo_take))
                    }
                    ShopSecondaryButton(
                        enabled = !busy,
                        onClick = {
                            showSourceDialog = false
                            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ShopSpacing.touchTarget)
                    ) {
                        Text(stringResource(R.string.photo_choose_gallery))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                ShopSecondaryButton(
                    onClick = { showSourceDialog = false },
                    modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
