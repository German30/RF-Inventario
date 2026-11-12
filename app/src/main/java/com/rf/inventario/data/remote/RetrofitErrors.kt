package com.rf.inventario.data.remote

import com.google.gson.Gson
import com.rf.inventario.data.ApiException
import com.rf.inventario.data.NetworkException
import com.rf.inventario.data.RejectedException
import com.rf.inventario.data.remote.dto.ApiErrorDto
import retrofit2.HttpException
import java.io.IOException

private val gson = Gson()

/**
 * Ejecuta una llamada Retrofit y traduce sus errores:
 * sin respuesta → [NetworkException]; 4xx → [RejectedException]; 5xx → [ApiException].
 */
internal suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: HttpException) {
        throw traducir(e)
    } catch (e: IOException) {
        throw NetworkException(cause = e)
    }

internal fun traducir(e: HttpException, cuerpo: String? = cuerpoDe(e)): ApiException {
    val mensaje = mensajeDe(e, cuerpo)
    return if (e.code() in 400..499) RejectedException(mensaje, e.code(), e) else ApiException(mensaje, e)
}

/** Cuerpo de error del backend (`ErrorResponse` de Spring), si vino y se pudo leer. */
internal fun mensajeDe(e: HttpException, cuerpo: String? = cuerpoDe(e)): String {
    val delServidor = cuerpo
        ?.let { runCatching { gson.fromJson(it, ApiErrorDto::class.java) }.getOrNull() }
        ?.message
    return delServidor?.takeIf { it.isNotBlank() } ?: "Error del servidor (${e.code()})"
}

/**
 * Body crudo de la respuesta de error. OJO: el errorBody de OkHttp se puede
 * leer UNA sola vez; si se necesita para dos cosas, leerlo acá y pasarlo.
 */
internal fun cuerpoDe(e: HttpException): String? =
    runCatching { e.response()?.errorBody()?.string() }.getOrNull()
