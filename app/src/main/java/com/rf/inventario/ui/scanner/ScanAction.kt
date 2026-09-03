package com.rf.inventario.ui.scanner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.rf.inventario.InventarioApplication
import com.rf.inventario.R
import com.rf.inventario.scanner.ScanResult
import com.rf.inventario.scanner.ScannerCapabilities
import kotlinx.coroutines.flow.merge

private enum class CaptureMode { WAITING_HARDWARE, CAMERA }

/**
 * Toolbar entry point for scanning. Picks the capture method automatically:
 *
 * - device looks like an RF terminal -> wait for the hardware trigger
 *   (keyboard-wedge or DataWedge), with a "use camera" escape hatch;
 * - otherwise -> open the camera scanner straight away.
 *
 * The decoded value is delivered through [onScanned].
 */
@Composable
fun ScanAction(onScanned: (ScanResult) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as InventarioApplication
    val capabilities = remember { ScannerCapabilities.detect(context) }
    var mode by remember { mutableStateOf<CaptureMode?>(null) }

    IconButton(
        onClick = {
            mode = if (capabilities.hasHardwareScanner) {
                CaptureMode.WAITING_HARDWARE
            } else {
                CaptureMode.CAMERA
            }
        },
    ) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan))
    }

    when (mode) {
        CaptureMode.WAITING_HARDWARE -> {
            LaunchedEffect(Unit) {
                merge(app.keyboardWedgeScanner.scans, app.dataWedgeScanner.scans).collect { result ->
                    mode = null
                    onScanned(result)
                }
            }
            HardwareScanSheet(
                vendor = capabilities.vendor,
                onUseCamera = { mode = CaptureMode.CAMERA },
                onDismiss = { mode = null },
            )
        }

        CaptureMode.CAMERA -> {
            CameraScannerScreen(
                onResult = { result ->
                    mode = null
                    onScanned(result)
                },
                onClose = { mode = null },
            )
        }

        null -> Unit
    }
}
