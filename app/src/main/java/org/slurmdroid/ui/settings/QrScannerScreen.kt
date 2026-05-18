package org.slurmdroid.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors

/**
 * Full-screen camera overlay that scans a QR code and returns the raw text.
 * Intended for reading `otpauth://totp/…` URIs.
 */
@Composable
fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
    var permissionGranted by remember { mutableStateOf(hasPermission) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (!granted) onDismiss()
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (permissionGranted) {
            val previewView = remember { PreviewView(context) }
            val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
            var scanned by remember { mutableStateOf(false) }

            DisposableEffect(previewView) {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    val cameraProvider = future.get()
                    val preview = Preview.Builder().build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { ia ->
                            ia.setAnalyzer(analysisExecutor) { proxy ->
                                if (!scanned) {
                                    val text = decodeQr(proxy)
                                    if (text != null) {
                                        scanned = true
                                        onScanned(text)
                                    }
                                }
                                proxy.close()
                            }
                        }

                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                }, ContextCompat.getMainExecutor(context))

                onDispose {
                    analysisExecutor.shutdown()
                    runCatching { future.get().unbindAll() }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Text(
                    "Point camera at the TOTP QR code",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            }
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Camera permission required to scan QR codes.")
            }
        }
    }
}

private fun decodeQr(proxy: ImageProxy): String? {
    val plane = proxy.planes[0]
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val source = PlanarYUVLuminanceSource(
        data, proxy.width, proxy.height,
        0, 0, proxy.width, proxy.height, false,
    )
    return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: NotFoundException) {
        null
    }
}
