package com.rf.inventario.data

/** El backend no pudo atender el pedido (red caída, error del servidor, etc.). */
open class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * No hubo respuesta del servidor (sin red, timeout, DNS...). Es el único caso
 * en que una operación se encola para reintentar: el servidor quizás ni la vio.
 */
class NetworkException(message: String = "Sin conexión con el servidor", cause: Throwable? = null) :
    ApiException(message, cause)

/**
 * El servidor respondió y RECHAZÓ el pedido (4xx: stock insuficiente, SKU
 * repetido, sin permiso...). Reintentar no sirve: hay que mostrárselo al operario.
 */
class RejectedException(message: String, val status: Int, cause: Throwable? = null) :
    ApiException(message, cause)

/**
 * El backend rechazó el guardado porque otro operario modificó el producto
 * primero (optimistic locking sobre `actualizadoEn`). [serverProduct] es el
 * estado actual en el servidor, si el backend lo mandó.
 */
class ConflictException(message: String, val serverProduct: Product?) : ApiException(message)
