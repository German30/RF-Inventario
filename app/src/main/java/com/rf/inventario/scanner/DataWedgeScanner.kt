package com.rf.inventario.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Receives scans from Zebra DataWedge through an implicit broadcast.
 *
 * The matching DataWedge profile is created automatically by [DataWedgeConfig]
 * when [start] runs; manual setup is documented in `docs/DATAWEDGE.md`. On
 * non-Zebra devices this class is inert: the broadcast simply never arrives.
 */
class DataWedgeScanner(private val appContext: Context) {

    private val _scans = MutableSharedFlow<ScanResult>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val scans: SharedFlow<ScanResult> = _scans.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_SCAN) return
            val data = intent.getStringExtra(EXTRA_DATA_STRING)?.trim().orEmpty()
            if (data.isEmpty()) return
            val labelType = intent.getStringExtra(EXTRA_LABEL_TYPE)?.removePrefix("LABEL-TYPE-")
            _scans.tryEmit(
                ScanResult(value = data, symbology = labelType, source = BarcodeSource.DATAWEDGE)
            )
        }
    }

    private var registered = false

    fun start() {
        if (registered) return
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(ACTION_SCAN),
            ContextCompat.RECEIVER_EXPORTED,
        )
        registered = true
        DataWedgeConfig.createProfile(appContext)
    }

    fun stop() {
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        registered = false
    }

    companion object {
        const val ACTION_SCAN = "com.rf.inventario.ACTION_DATAWEDGE_SCAN"
        private const val EXTRA_DATA_STRING = "com.symbol.datawedge.data_string"
        private const val EXTRA_LABEL_TYPE = "com.symbol.datawedge.label_type"
    }
}
