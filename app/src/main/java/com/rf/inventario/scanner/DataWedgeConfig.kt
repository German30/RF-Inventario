package com.rf.inventario.scanner

import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Creates/updates a DataWedge profile so scanning works out of the box on Zebra
 * devices: barcode plugin on, intent output as a broadcast to
 * [DataWedgeScanner.ACTION_SCAN], keystroke output off.
 *
 * Every intent is addressed to `com.symbol.datawedge`; on any other device the
 * broadcast is dropped and this is a silent no-op.
 */
object DataWedgeConfig {

    private const val DATAWEDGE_PACKAGE = "com.symbol.datawedge"
    private const val API_ACTION = "com.symbol.datawedge.api.ACTION"
    private const val PROFILE_NAME = "RFInventario"

    fun createProfile(context: Context) {
        send(context, Intent(API_ACTION).putExtra("com.symbol.datawedge.api.CREATE_PROFILE", PROFILE_NAME))

        val config = Bundle().apply {
            putString("PROFILE_NAME", PROFILE_NAME)
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "UPDATE")

            putParcelableArray(
                "APP_LIST",
                arrayOf(
                    Bundle().apply {
                        putString("PACKAGE_NAME", context.packageName)
                        putStringArray("ACTIVITY_LIST", arrayOf("*"))
                    },
                ),
            )

            putParcelableArrayList(
                "PLUGIN_CONFIG",
                arrayListOf(
                    plugin("BARCODE") {
                        putString("scanner_selection", "auto")
                        putString("scanner_input_enabled", "true")
                    },
                    plugin("INTENT") {
                        putString("intent_output_enabled", "true")
                        putString("intent_action", DataWedgeScanner.ACTION_SCAN)
                        putString("intent_delivery", "2") // 2 = broadcast
                    },
                    plugin("KEYSTROKE") {
                        putString("keystroke_output_enabled", "false")
                    },
                ),
            )
        }
        send(context, Intent(API_ACTION).putExtra("com.symbol.datawedge.api.SET_CONFIG", config))
    }

    private inline fun plugin(name: String, params: Bundle.() -> Unit) = Bundle().apply {
        putString("PLUGIN_NAME", name)
        putString("RESET_CONFIG", "true")
        putBundle("PARAM_LIST", Bundle().apply(params))
    }

    private fun send(context: Context, intent: Intent) {
        intent.setPackage(DATAWEDGE_PACKAGE)
        runCatching { context.sendBroadcast(intent) }
    }
}
