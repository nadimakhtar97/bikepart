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
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
        if (image != null) Image(image, stringResource(R.string.product_photo), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_stock),
                contentDescription = if (reference == null && selectedUri == null) stringResource(R.string.no_product_photo) else stringResource(R.string.photo_unavailable),
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
internal fun PhotoField(reference: PhotoReference?, uri: String?, removed: Boolean, busy: Boolean,
    load: PhotoLoader, select: (List<String>) -> Unit, remove: () -> Unit,
    multipleUris: List<String>?, removeSelected: (String) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { selected -> selected?.let { select(listOf(it.toString())) } }
    val multiplePicker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { selected ->
        if (selected.isNotEmpty()) select(selected.map(Uri::toString))
    }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showSourceDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val path = pendingCameraPath
            val file = path?.let { File(it) }
            if (file != null && file.exists() && file.length() > 200) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString().let { select(listOf(it)) }
            } else if (data?.data != null) {
                select(listOf(data.data.toString()))
            } else {
                @Suppress("DEPRECATION")
                val bitmap = data?.extras?.get("data") as? Bitmap
                if (bitmap != null && file != null) {
                    try {
                        file.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString().let { select(listOf(it)) }
                    } catch (_: Exception) {
                        path?.let { select(listOf(Uri.fromFile(File(it)).toString())) }
                    }
                } else {
                    path?.let { select(listOf(Uri.fromFile(File(it)).toString())) }
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
            cameraError = context.getString(R.string.could_not_open_camera, e.localizedMessage ?: context.getString(R.string.unknown_error))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            cameraError = context.getString(R.string.camera_permission_is_required_to_take)
        }
    }

    val multiple = multipleUris != null
    val addLabel = stringResource(R.string.photos_add)
    ShopSection(stringResource(if (multiple) R.string.photos_section else R.string.photo_section)) {
        val hasPhoto = uri != null || (!removed && reference != null)
        val description = stringResource(R.string.photo_description)
        if (multipleUris != null) {
            val inheritedPhoto = reference.takeUnless { removed }
            if (inheritedPhoto != null || multipleUris.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                    if (inheritedPhoto != null) {
                        item { DraftPhotoPreview(inheritedPhoto, null, 1, load, busy, remove) }
                    }
                    itemsIndexed(multipleUris, key = { _, selected -> selected }) { index, selected ->
                        val number = index + if (inheritedPhoto == null) 1 else 2
                        DraftPhotoPreview(null, selected, number, load, busy) { removeSelected(selected) }
                    }
                }
            }
            Text(stringResource(R.string.photos_description), style = MaterialTheme.typography.bodyMedium)
            ShopSecondaryButton(
                enabled = !busy,
                onClick = { showSourceDialog = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget),
            ) {
                Text(addLabel)
            }
        } else if (hasPhoto) {
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
        ErrorSnackbar(cameraError)
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text(if (multiple) addLabel else stringResource(if (uri != null || (!removed && reference != null)) R.string.photo_replace else R.string.photo_choose)) },
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
                            val request = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
                            if (multiple) multiplePicker.launch(request) else picker.launch(request)
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

@Composable
private fun DraftPhotoPreview(
    reference: PhotoReference?,
    uri: String?,
    number: Int,
    load: PhotoLoader,
    busy: Boolean,
    remove: () -> Unit,
) {
    Box(Modifier.size(104.dp)) {
        ProductPhoto(reference, load, Modifier.fillMaxSize(), selectedUri = uri)
        IconButton(
            onClick = remove,
            enabled = !busy,
            modifier = Modifier.align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = .6f), CircleShape),
        ) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.photos_remove, number),
                tint = Color.White,
            )
        }
    }
}
