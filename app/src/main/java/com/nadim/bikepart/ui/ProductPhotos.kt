package com.nadim.bikepart.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.PhotoReference
import com.nadim.bikepart.shop.MAX_PHOTO_BYTES
import com.nadim.bikepart.ui.theme.ShopSpacing
import java.io.File
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
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { selected -> selected?.let { select(it.toString()) } }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showSourceDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val path = pendingCameraPath
            val file = path?.let { File(it) }
            if (file != null && file.exists() && file.length() > 200) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString().let { select(it) }
            } else if (data?.data != null) {
                select(data.data.toString())
            } else {
                @Suppress("DEPRECATION")
                val bitmap = data?.extras?.get("data") as? Bitmap
                if (bitmap != null && file != null) {
                    try {
                        file.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString().let { select(it) }
                    } catch (_: Exception) {
                        path?.let { select(Uri.fromFile(File(it)).toString()) }
                    }
                } else {
                    path?.let { select(Uri.fromFile(File(it)).toString()) }
                }
            }
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
            ProductPhoto(if (removed) null else reference, load, Modifier.fillMaxWidth().height(160.dp), uri)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        } else Row(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            ProductPhoto(null, load, Modifier.size(48.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        ShopSecondaryButton(enabled = !busy, onClick = { showSourceDialog = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) {
            Text(if (hasPhoto) stringResource(R.string.photo_replace) else stringResource(R.string.photo_choose))
        }
        if (hasPhoto) ShopSecondaryButton(enabled = !busy, onClick = remove,
            modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.photo_remove)) }
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)
                    ) {
                        Text(stringResource(R.string.photo_take))
                    }
                    ShopSecondaryButton(
                        enabled = !busy,
                        onClick = {
                            showSourceDialog = false
                            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)
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
