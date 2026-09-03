package com.rf.inventario.scanner

import android.view.KeyEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Captures scans from any hardware reader configured in "keyboard wedge" mode:
 * it injects the barcode as a burst of key events terminated by Enter or Tab.
 *
 * A timing guard tells machine-gun wedge input apart from a human typing on a
 * physical keyboard: if two consecutive key events are more than [MAX_GAP_MS]
 * apart, the partial buffer is dropped.
 *
 * Feed every [KeyEvent] the Activity receives from `dispatchKeyEvent`.
 */
class KeyboardWedgeScanner {

    private val _scans = MutableSharedFlow<ScanResult>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val scans: SharedFlow<ScanResult> = _scans.asSharedFlow()

    private val buffer = StringBuilder()
    private var lastEventAt = 0L

    /** @return true if the event was consumed (only the terminating Enter/Tab of a real scan). */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val now = System.currentTimeMillis()
        if (now - lastEventAt > MAX_GAP_MS) buffer.setLength(0)
        lastEventAt = now

        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_TAB -> {
                val code = buffer.toString().trim()
                buffer.setLength(0)
                if (code.length >= MIN_LENGTH) {
                    _scans.tryEmit(
                        ScanResult(value = code, symbology = null, source = BarcodeSource.HARDWARE_WEDGE)
                    )
                    true
                } else {
                    false
                }
            }

            else -> {
                val ch = event.unicodeChar
                if (ch != 0) buffer.append(ch.toChar())
                false
            }
        }
    }

    private companion object {
        const val MAX_GAP_MS = 120L
        const val MIN_LENGTH = 3
    }
}
