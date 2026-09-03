package com.rf.inventario.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Best-effort detection of how the current device can scan barcodes.
 *
 * There is no public Android API to ask "do you have a hardware barcode reader?",
 * so [hasHardwareScanner] is a heuristic based on the manufacturer, known system
 * features and whether Zebra DataWedge is installed. The UI always offers the
 * camera as an escape hatch when the heuristic is wrong.
 */
data class ScannerCapabilities(
    val hasCamera: Boolean,
    val hasDataWedge: Boolean,
    val hasHardwareScanner: Boolean,
    val vendor: String?,
) {
    companion object {
        private const val DATAWEDGE_PACKAGE = "com.symbol.datawedge"

        private val KNOWN_RF_VENDORS = listOf(
            "zebra", "symbol", "honeywell", "datalogic", "bluebird", "unitech",
            "point mobile", "pointmobile", "cipherlab", "urovo", "chainway",
            "seuic", "newland", "idata", "kaicom",
        )

        private val SCANNER_SYSTEM_FEATURES = listOf(
            "com.symbol.hardware.scanner",
            "com.honeywell.scanner",
            "device.common.SamModuleDevice",
        )

        fun detect(context: Context): ScannerCapabilities {
            val pm = context.packageManager

            val hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

            val hasDataWedge = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(DATAWEDGE_PACKAGE, 0)
            }.isSuccess

            val fingerprint = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL)
                .joinToString(" ") { it.orEmpty().lowercase() }
            val vendor = KNOWN_RF_VENDORS.firstOrNull { fingerprint.contains(it) }

            val hasScannerFeature = SCANNER_SYSTEM_FEATURES.any { pm.hasSystemFeature(it) }

            return ScannerCapabilities(
                hasCamera = hasCamera,
                hasDataWedge = hasDataWedge,
                hasHardwareScanner = hasDataWedge || hasScannerFeature || vendor != null,
                vendor = vendor?.replaceFirstChar { it.uppercase() },
            )
        }
    }
}
