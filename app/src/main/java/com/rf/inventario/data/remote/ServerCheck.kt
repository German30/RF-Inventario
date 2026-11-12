package com.rf.inventario.data.remote

import com.google.gson.JsonParseException
import com.rf.inventario.data.ApiException
import com.rf.inventario.data.remote.dto.PingDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** El backend no respondió al ping, o respondió algo que no es un backend sano. */
class ServerUnavailableException(message: String, cause: Throwable? = null) :
    ApiException(message, cause)

/**
 * El backend responde, pero es una versión anterior a la que esta app necesita:
 * le faltarían endpoints (404) y la app fallaría a mitad de camino.
 */
class IncompatibleServerException(val serverVersion: Int?, val requiredVersion: Int) :
    ApiException(
        "El servidor tiene la versión de API ${serverVersion ?: "desconocida"} y esta app " +
            "necesita la $requiredVersion o superior. Actualizá el backend.",
    )

/**
 * Verifica al arrancar que el backend esté arriba y sea compatible.
 * [verify] lanza la excepción del problema; no devuelve nada si todo está bien.
 */
class ServerCheck(
    private val ping: suspend () -> PingDto,
    private val requiredVersion: Int = REQUIRED_API_VERSION,
) {

    /**
     * @throws ServerUnavailableException sin respuesta, HTTP de error o estado distinto de UP
     * @throws IncompatibleServerException si el backend es más viejo que [requiredVersion]
     */
    suspend fun verify() {
        val dto = try {
            ping()
        } catch (e: IOException) {
            throw ServerUnavailableException("No se pudo conectar con el servidor", e)
        } catch (e: HttpException) {
            throw ServerUnavailableException("El servidor respondió HTTP ${e.code()} al verificarlo", e)
        } catch (e: JsonParseException) {
            throw ServerUnavailableException("La dirección configurada no es el backend de RF-Inventario", e)
        }

        if (dto.status != "UP") {
            throw ServerUnavailableException("El servidor reporta estado ${dto.status ?: "desconocido"}")
        }
        val version = dto.apiVersion
        if (version == null || version < requiredVersion) {
            throw IncompatibleServerException(version, requiredVersion)
        }
    }

    companion object {
        /** Debe coincidir con `HealthController.API_VERSION` del backend. */
        const val REQUIRED_API_VERSION = 6
    }
}

/** Estado de la verificación de arranque. La app no muestra nada más hasta [Ready]. */
sealed interface ServerStatus {
    data object Checking : ServerStatus
    data object Ready : ServerStatus
    data class Failed(val error: ApiException) : ServerStatus
}

/**
 * Bloquea la app hasta que [ServerCheck] pase. Si falla, la ejecución queda
 * detenida en [ServerStatus.Failed] con la excepción, hasta que se reintente.
 */
class ServerGate(
    private val check: ServerCheck,
    private val scope: CoroutineScope,
    private val onFailure: (ApiException) -> Unit = {},
) {
    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Checking)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private var job: Job? = null

    /** Lanza (o relanza) la verificación. Ignora llamadas mientras una está en curso. */
    @Synchronized
    fun verify() {
        if (job?.isActive == true) return
        _status.value = ServerStatus.Checking
        job = scope.launch {
            _status.value = try {
                check.verify()
                ServerStatus.Ready
            } catch (e: ApiException) {
                onFailure(e)
                ServerStatus.Failed(e)
            }
        }
    }
}
