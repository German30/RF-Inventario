package com.rf.inventario

import android.view.KeyEvent
import com.rf.inventario.scanner.KeyboardWedgeScanner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Usa las constantes de KeyEvent (son `static final`, el compilador las inlinea: no hace falta Android). */
class KeyboardWedgeScannerTest {

    private var now = 1_000L
    private val scanner = KeyboardWedgeScanner(clock = { now })

    private fun type(text: String) = text.forEach { c ->
        now += 5
        scanner.onKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, c.code)
        scanner.onKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, c.code)
    }

    @Test
    fun consumeElDownYElUpDelEnterDeUnaLectura() {
        type("7790001")
        now += 5
        assertTrue(scanner.onKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
        // Si el UP se escapara, Compose "clickearía" el botón con foco.
        assertTrue(scanner.onKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
    }

    @Test
    fun consumeUnSegundoTerminadorPegado() {
        type("ABC123")
        now += 5
        scanner.onKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0)
        scanner.onKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0)
        now += 5
        assertTrue(scanner.onKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0))
        assertTrue(scanner.onKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0))
    }

    @Test
    fun unEnterTecleadoAManoNoSeConsume() {
        now += 5_000
        assertFalse(scanner.onKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
        assertFalse(scanner.onKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
    }

    @Test
    fun tecleoLentoNoEsUnaLectura() {
        "1234".forEach { c ->
            now += 500
            scanner.onKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, c.code)
        }
        now += 500
        assertFalse(scanner.onKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
    }
}
