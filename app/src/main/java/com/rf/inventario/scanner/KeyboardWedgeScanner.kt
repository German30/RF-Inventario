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
 *
 * Importante: cuando el terminador cierra una lectura se consumen TANTO su
 * ACTION_DOWN como su ACTION_UP (y un segundo terminador pegado, p. ej. CR+LF).
 * Compose dispara el click de un botón con foco al SOLTAR Enter: si el UP se
 * escapara, cada escaneo "tocaría" el botón enfocado (cerrar sesión, borrar...).
 */
class KeyboardWedgeScanner(private val clock: () -> Long = System::currentTimeMillis) {

    private val _scans = MutableSharedFlow<ScanResult>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val scans: SharedFlow<ScanResult> = _scans.asSharedFlow()

    private val buffer = StringBuilder()
    private var lastEventAt = 0L

    /** Terminadores cuyo UP todavía hay que tragar. */
    private val pendingUps = mutableSetOf<Int>()

    /** Momento de la última lectura completa: un terminador extra justo después también es del lector. */
    private var lastScanAt = Long.MIN_VALUE / 2

    /** @return true if the event was consumed (the terminator of a real scan, down and up). */
    fun onKeyEvent(event: KeyEvent): Boolean = onKey(event.action, event.keyCode, event.unicodeChar)

    internal fun onKey(action: Int, keyCode: Int, unicodeChar: Int): Boolean {
        val terminator = keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_TAB

        if (action == KeyEvent.ACTION_UP) {
            return terminator && pendingUps.remove(keyCode)
        }
        if (action != KeyEvent.ACTION_DOWN) return false

        val now = clock()
        if (now - lastEventAt > MAX_GAP_MS) buffer.setLength(0)
        lastEventAt = now

        if (!terminator) {
            if (unicodeChar != 0) buffer.append(unicodeChar.toChar())
            return false
        }

        val code = buffer.toString().trim()
        buffer.setLength(0)
        return when {
            code.length >= MIN_LENGTH -> {
                _scans.tryEmit(ScanResult(value = code, symbology = null, source = BarcodeSource.HARDWARE_WEDGE))
                lastScanAt = now
                pendingUps += keyCode
                true
            }
            // Segundo terminador del mismo escaneo (sufijo CR+LF o Enter+Tab).
            now - lastScanAt <= MAX_GAP_MS -> {
                pendingUps += keyCode
                true
            }
            else -> false
        }
    }

    private companion object {
        const val MAX_GAP_MS = 120L
        const val MIN_LENGTH = 3
    }
}
