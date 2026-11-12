package com.rf.inventario

import com.rf.inventario.auth.LogoutReason
import com.rf.inventario.auth.SessionManager
import com.rf.inventario.auth.SessionStore
import com.rf.inventario.data.AppUser
import com.rf.inventario.data.Location
import com.rf.inventario.data.Product
import com.rf.inventario.export.CsvExporter
import com.rf.inventario.labels.Zpl
import com.rf.inventario.scanner.ScanRouter
import com.rf.inventario.scanner.ScanTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanAndLabelsTest {

    private val cable = Product(id = 1, name = "Cable", sku = "C1", quantity = 3, location = "A-01", altCodes = listOf("7790001"))

    private val router = ScanRouter(
        findLocation = { if (it.equals("A-01", true)) Location("A-01") else null },
        findProduct = { code -> cable.takeIf { it.matchesCode(code) } },
    )

    @Test
    fun clasificaUbicacionProductoYDesconocido() = runTest {
        assertTrue(router.route("a-01") is ScanTarget.IsLocation)
        assertTrue(router.route("7790001") is ScanTarget.IsProduct) // código alternativo
        assertTrue(router.route("ZZZ") is ScanTarget.Unknown)
    }

    @Test
    fun limpiaPrefijoAimYEspacios() {
        assertEquals("7790001", ScanRouter.clean("]E07790001\n"))
        assertEquals("ABC-1", ScanRouter.clean("  ABC-1\r"))
    }

    @Test
    fun zplDeProductoTieneCodigoDeBarrasYNoSeRompeConCaracteresDeControl() {
        val zpl = Zpl.product(cable.copy(name = "Cable ^raro~"), copies = 2)
        assertTrue(zpl.startsWith("^XA") && zpl.endsWith("^XZ"))
        assertTrue(zpl.contains("^BCN") && zpl.contains("^FDC1^FS"))
        assertTrue(zpl.contains("^PQ2"))
        assertFalse(zpl.contains("^raro"))
    }

    @Test
    fun csvNeutralizaFormulas() {
        assertEquals("'=1+1", CsvExporter.cell("=1+1"))
        assertEquals("\"a;b\"", CsvExporter.cell("a;b"))
        assertEquals("-3", CsvExporter.cell(-3))
        val csv = CsvExporter.inventoryCsv(listOf(cable))
        assertTrue(csv.lines()[1].startsWith("C1;Cable;"))
    }

    @Test
    fun bajoMinimo() {
        assertTrue(cable.copy(minStock = 3).lowStock)
        assertFalse(cable.copy(minStock = 0).lowStock)
    }

    private class Store : SessionStore {
        var user: AppUser? = null
        var token: String? = null
        override fun persist(user: AppUser?, token: String?) { this.user = user; this.token = token }
        override fun restoreUser() = user
        override fun restoreToken() = token
    }

    @Test
    fun cierraSesionPorInactividad() {
        var now = 0L
        val session = SessionManager(Store(), clock = { now })
        session.login(AppUser("1001", "Op"), "t")

        now = 9 * 60_000
        assertFalse(session.logoutIfIdle(10 * 60_000))
        session.touch()
        now += 10 * 60_000
        assertTrue(session.logoutIfIdle(10 * 60_000))

        assertNull(session.current.value)
        assertNull(session.token)
        assertEquals(LogoutReason.IDLE, session.lastLogoutReason.value)
    }
}
