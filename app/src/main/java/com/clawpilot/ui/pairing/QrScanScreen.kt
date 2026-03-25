package com.clawpilot.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.clawpilot.domain.model.PairingPayload
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.serialization.json.Json

/**
 * Pantalla de escáner QR con CameraX + ML Kit.
 * Detecta códigos QR que contengan un PairingPayload JSON válido.
 */
@Composable
fun QrScanScreen(
    onQrDetected: (PairingPayload) -> Unit,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    if (hasCameraPermission) {
        CameraPreviewWithScanner(onQrDetected = onQrDetected, onManualEntry = onManualEntry)
    } else {
        PermissionRequest(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onManualEntry = onManualEntry
        )
    }
}

@Composable
private fun PermissionRequest(
    onRequestPermission: () -> Unit,
    onManualEntry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Camera access needed to scan\nthe gateway QR code",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onManualEntry) {
                Text("Enter URL manually")
            }
        }
    }
}

@Composable
private fun CameraPreviewWithScanner(
    onQrDetected: (PairingPayload) -> Unit,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val json = remember { Json { ignoreUnknownKeys = true } }

    // Variable para evitar callbacks duplicados una vez detectado
    var detected by remember { mutableStateOf(false) }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                QrAnalyzer(scanner) { raw ->
                    if (detected) return@QrAnalyzer
                    runCatching { json.decodeFromString<PairingPayload>(raw) }
                        .getOrNull()?.let { payload ->
                            detected = true
                            onQrDetected(payload)
                        }
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        cameraController.bindToLifecycle(lifecycleOwner)
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Vista de cámara
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay: título superior
        Text(
            text = "Scan Gateway QR Code",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        )

        // Viewfinder cuadrado
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.Center)
                .border(2.dp, Color.White)
        )

        // Botón inferior
        TextButton(
            onClick = onManualEntry,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Text("Enter URL manually", color = Color.White)
        }
    }
}

/**
 * Analyzer que procesa frames de cámara con ML Kit BarcodeScanner.
 * Usa la API @ExperimentalGetImage de CameraX para acceder al Image subyacente.
 */
private class QrAnalyzer(
    private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    private val onDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let(onDetected)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
