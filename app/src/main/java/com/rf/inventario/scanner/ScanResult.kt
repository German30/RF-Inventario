package com.rf.inventario.scanner

/** Where a barcode reading came from. */
enum class BarcodeSource {
    /** Hardware reader in keyboard-wedge mode (generic). */
    HARDWARE_WEDGE,

    /** Zebra DataWedge intent output. */
    DATAWEDGE,

    /** Phone camera + ML Kit (fallback). */
    CAMERA,
}

/** A single decoded barcode, regardless of the capture method. */
data class ScanResult(
    val value: String,
    val symbology: String?,
    val source: BarcodeSource,
)
