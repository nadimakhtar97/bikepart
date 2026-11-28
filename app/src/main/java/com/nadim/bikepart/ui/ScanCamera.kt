package com.nadim.bikepart.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.CameraState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.nadim.bikepart.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.LiveData
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

typealias ScanCameraContent = @Composable (onPayload: (String) -> Unit, onFailure: (String) -> Unit) -> Unit

internal fun shopBarcodeScanner() = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build())

/** Bundled model: no Play services model installation or first-use download. */
@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
fun ScanCamera(onPayload: (String) -> Unit, onFailure: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val payload by rememberUpdatedState(onPayload)
    val failure by rememberUpdatedState(onFailure)
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) failure(context.getString(R.string.camera_permission_denied_use_manual_search))
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    if (!granted) return
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().clipToBounds())
        Canvas(Modifier.align(Alignment.Center).fillMaxWidth(.7f).aspectRatio(1f)) {
            val stroke = 4.dp.toPx()
            val length = size.width * .12f
            val right = size.width - stroke / 2
            val bottom = size.height - stroke / 2
            val left = stroke / 2
            val top = stroke / 2
            listOf(
                Offset(left, top) to Offset(left + length, top), Offset(left, top) to Offset(left, top + length),
                Offset(right, top) to Offset(right - length, top), Offset(right, top) to Offset(right, top + length),
                Offset(left, bottom) to Offset(left + length, bottom), Offset(left, bottom) to Offset(left, bottom - length),
                Offset(right, bottom) to Offset(right - length, bottom), Offset(right, bottom) to Offset(right, bottom - length),
            ).forEach { (start, end) -> drawLine(Color.White, start, end, stroke) }
        }
        boundCamera?.takeIf { it.cameraInfo.hasFlashUnit() }?.let { activeCamera ->
            IconButton(onClick = {
                val next = !torchEnabled
                activeCamera.cameraControl.enableTorch(next).addListener({
                    torchEnabled = activeCamera.cameraInfo.torchState.value == androidx.camera.core.TorchState.ON
                }, ContextCompat.getMainExecutor(context))
            }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                .background(Color.Black.copy(alpha = .6f), androidx.compose.foundation.shape.CircleShape)
                .semantics { stateDescription = if (torchEnabled) context.getString(R.string.camera_flash_on) else context.getString(R.string.camera_flash_off) }) {
                Icon(painterResource(R.drawable.ic_flash), contentDescription = stringResource(R.string.camera_flash), tint = Color.White)
            }
        }
        Text(stringResource(R.string.camera_position_barcode), modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
    DisposableEffect(owner, previewView) {
        val disposed = AtomicBoolean(false)
        val processing = AtomicBoolean(false)
        val scanner = shopBarcodeScanner()
        val executor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var provider: ProcessCameraProvider? = null
        var cameraState: LiveData<CameraState>? = null
        val observer = Observer<CameraState> { state ->
            if (state.error != null && !disposed.get()) failure(context.getString(R.string.camera_unavailable_use_manual_search_or))
        }
        analysis.setAnalyzer(executor) { proxy ->
            if (disposed.get() || !processing.compareAndSet(false, true)) { proxy.close(); return@setAnalyzer }
            // CameraX owns the media image until the asynchronous recognizer completes.
            val media = proxy.image
            if (media == null) { processing.set(false); proxy.close(); return@setAnalyzer }
            try {
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes ->
                        if (!disposed.get()) {
                            val values = codes.mapNotNull { it.rawValue }.distinct()
                            if (values.size > 1) failure(context.getString(R.string.more_than_one_code_is_visible))
                            else values.singleOrNull()?.let { payload(it) }
                        }
                    }
                    .addOnFailureListener { if (!disposed.get()) failure(context.getString(R.string.recognition_failed_use_manual_search_or)) }
                    .addOnCompleteListener { processing.set(false); proxy.close() }
            } catch (_: Exception) {
                processing.set(false); proxy.close()
                if (!disposed.get()) failure(context.getString(R.string.recognition_failed_use_manual_search_or))
            }
        }
        future.addListener({
            if (!disposed.get()) try {
                provider = future.get()
                val camera = requireNotNull(provider).bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                boundCamera = camera
                cameraState = camera.cameraInfo.cameraState
                cameraState?.observe(owner, observer)
            } catch (_: Exception) { if (!disposed.get()) failure(context.getString(R.string.camera_unavailable_use_manual_search_or)) }
        }, executor)
        onDispose {
            disposed.set(true)
            boundCamera = null
            torchEnabled = false
            cameraState?.removeObserver(observer)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            scanner.close()
        }
    }
}
