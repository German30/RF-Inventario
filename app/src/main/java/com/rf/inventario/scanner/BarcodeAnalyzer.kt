package com.rf.inventario.scanner

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX [ImageAnalysis.Analyzer] that decodes barcodes with the bundled ML Kit
 * model (no Google Play Services required). Emits the first non-empty reading of
 * each frame through [onBarcode]; de-duplication is the caller's job.
 */
class BarcodeAnalyzer(
    private val onBarcode: (ScanResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build(),
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val hit = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                    ?: return@addOnSuccessListener
                onBarcode(
                    ScanResult(
                        value = hit.rawValue!!.trim(),
                        symbology = symbologyName(hit.format),
                        source = BarcodeSource.CAMERA,
                    ),
                )
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun symbologyName(format: Int): String = when (format) {
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_QR_CODE -> "QR"
        Barcode.FORMAT_DATA_MATRIX -> "DataMatrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        else -> "—"
    }
}
