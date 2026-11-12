package com.rf.inventario

import com.rf.inventario.data.remote.IncompatibleServerException
import com.rf.inventario.data.remote.ServerCheck
import com.rf.inventario.data.remote.ServerGate
import com.rf.inventario.data.remote.ServerStatus
import com.rf.inventario.data.remote.ServerUnavailableException
import com.rf.inventario.data.remote.dto.PingDto
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ServerCheckTest {

    private val sano = PingDto(status = "UP", service = "rf-inventario-panel-api", apiVersion = 6)

    @Test
    fun `servidor sano y compatible pasa`() = runTest {
        ServerCheck(ping = { sano }).verify()
    }

    @Test(expected = ServerUnavailableException::class)
    fun `sin conexion lanza ServerUnavailableException`() = runTest {
        ServerCheck(ping = { throw IOException("timeout") }).verify()
    }

    @Test(expected = ServerUnavailableException::class)
    fun `HTTP de error lanza ServerUnavailableException`() = runTest {
        val error = HttpException(Response.error<PingDto>(503, "".toResponseBody()))
        ServerCheck(ping = { throw error }).verify()
    }

    @Test(expected = ServerUnavailableException::class)
    fun `estado distinto de UP lanza ServerUnavailableException`() = runTest {
        ServerCheck(ping = { sano.copy(status = "DOWN") }).verify()
    }

    @Test
    fun `backend viejo sin apiVersion es incompatible`() = runTest {
        try {
            ServerCheck(ping = { sano.copy(apiVersion = null) }).verify()
            throw AssertionError("debió fallar")
        } catch (e: IncompatibleServerException) {
            assertEquals(null, e.serverVersion)
            assertEquals(ServerCheck.REQUIRED_API_VERSION, e.requiredVersion)
        }
    }

    @Test(expected = IncompatibleServerException::class)
    fun `version menor a la requerida es incompatible`() = runTest {
        ServerCheck(ping = { sano.copy(apiVersion = 5) }).verify()
    }

    @Test
    fun `gate queda en Failed y pasa a Ready al reintentar`() {
        val scope = TestScope(StandardTestDispatcher())
        var arriba = false
        val fallas = mutableListOf<Exception>()
        val gate = ServerGate(
            check = ServerCheck(ping = { if (arriba) sano else throw IOException("caído") }),
            scope = scope,
            onFailure = { fallas += it },
        )

        gate.verify()
        assertEquals(ServerStatus.Checking, gate.status.value)
        scope.advanceUntilIdle()
        val estado = gate.status.value
        assertTrue(estado is ServerStatus.Failed && estado.error is ServerUnavailableException)
        assertEquals(1, fallas.size)

        arriba = true
        gate.verify()
        scope.advanceUntilIdle()
        assertEquals(ServerStatus.Ready, gate.status.value)
    }
}
