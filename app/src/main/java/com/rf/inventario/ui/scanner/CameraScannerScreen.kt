package com.rf.inventario.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rf.inventario.R
import com.rf.inventario.scanner.BarcodeAnalyzer
import com.rf.inventario.scanner.ScanResult
import java.util.concurrent.Executors

/**
 * Full-screen camera barcode scanner (the fallback when the device has no
 * hardware reader). Returns the first stable reading through [onResult].
 */
@Composable
fun CameraScannerScreen(
    onResult: (ScanResult) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        if (hasPermission) {
            CameraPreview(onResult = onResult, onClose = onClose)
        } else {
            PermissionDenied(
                onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun CameraPreview(
    onResult: (ScanResult) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var handled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }
    LaunchedEffect(torchOn) { camera?.cameraControl?.enableTorch(torchOn) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysisUseCase ->
                            analysisUseCase.setAnalyzer(
                                analysisExecutor,
                                BarcodeAnalyzer { result ->
                                    if (handled) return@BarcodeAnalyzer
                                    handled = true
                                    ContextCompat.getMainExecutor(ctx).execute { onResult(result) }
                                },
                            )
                        }
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        ViewfinderOverlay(modifier = Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            FilledTonalIconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
            }
            FilledTonalIconButton(
                onClick = { torchOn = !torchOn },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    imageVector = if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = stringResource(R.string.torch),
                )
            }
        }

        Text(
            text = stringResource(R.string.scan_point_camera),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
        )
    }
}

@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val boxWidth = size.width * 0.74f
        val boxHeight = boxWidth * 0.6f
        val left = (size.width - boxWidth) / 2f
        val top = (size.height - boxHeight) / 2f
        val radius = CornerRadius(28f, 28f)

        val scrim = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(left, top, left + boxWidth, top + boxHeight, radius))
            fillType = PathFillType.EvenOdd
        }
        drawPath(scrim, Color.Black.copy(alpha = 0.55f))
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = radius,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun PermissionDenied(
    onGrant: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.camera_permission_denied),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.camera_permission_grant))
        }
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.action_close), color = Color.White)
        }
    }
}
