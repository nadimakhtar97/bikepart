package com.nadim.bikepart.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.CameraState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
        if (!it) failure("Camera permission denied. Use manual search or resume to try again.")
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    if (!granted) return
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(240.dp).clipToBounds())
    DisposableEffect(owner, previewView) {
        val disposed = AtomicBoolean(false)
        val processing = AtomicBoolean(false)
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val executor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var provider: ProcessCameraProvider? = null
        var cameraState: LiveData<CameraState>? = null
        val observer = Observer<CameraState> { state ->
            if (state.error != null && !disposed.get()) failure("Camera unavailable. Use manual search or resume to try again.")
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
                            if (values.size > 1) failure("More than one QR is visible. Show one label and resume, or use manual search.")
                            else values.singleOrNull()?.let { payload(it) }
                        }
                    }
                    .addOnFailureListener { if (!disposed.get()) failure("Recognition failed. Use manual search or resume to try again.") }
                    .addOnCompleteListener { processing.set(false); proxy.close() }
            } catch (_: Exception) {
                processing.set(false); proxy.close()
                if (!disposed.get()) failure("Recognition failed. Use manual search or resume to try again.")
            }
        }
        future.addListener({
            if (!disposed.get()) try {
                provider = future.get()
                val camera = requireNotNull(provider).bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                cameraState = camera.cameraInfo.cameraState
                cameraState?.observe(owner, observer)
            } catch (_: Exception) { if (!disposed.get()) failure("Camera unavailable. Use manual search or resume to try again.") }
        }, executor)
        onDispose {
            disposed.set(true)
            cameraState?.removeObserver(observer)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            scanner.close()
        }
    }
}
