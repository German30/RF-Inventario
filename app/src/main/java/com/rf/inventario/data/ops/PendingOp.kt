package com.rf.inventario.data.ops

import kotlinx.coroutines.flow.Flow

/**
 * Operación que el operario hizo sin conexión. [payload] es el JSON exacto
 * que se va a mandar (incluye [clientOpId], así el reintento es idempotente).
 */
data class PendingOp(
    val id: Long = 0,
    val clientOpId: String,
    val kind: Kind,
    /** Conteo o pedido destino; null en un movimiento suelto. */
    val targetId: Long?,
    val payload: String,
    /** Texto para la pantalla de pendientes, ej. "Entrada · RF2100 · +5". */
    val summary: String,
    val createdAt: Long,
    val status: Status = Status.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
) {
    enum class Kind { MOVEMENT, COUNT_LINE, ORDER_SCAN }

    /** PENDING = se reintenta solo; FAILED = el servidor la rechazó, la decide el operario. */
    enum class Status { PENDING, FAILED }
}

/** Persistencia de la cola. Room en la app; en memoria en los tests. */
interface PendingOpStore {
    suspend fun add(op: PendingOp): Long
    suspend fun pending(): List<PendingOp>
    suspend fun markSent(id: Long)
    suspend fun markFailed(id: Long, error: String)
    suspend fun markRetryLater(id: Long, error: String)
    suspend fun retry(id: Long)
    suspend fun discard(id: Long)
    fun observeAll(): Flow<List<PendingOp>>
    fun observePendingCount(): Flow<Int>
    fun observeFailedCount(): Flow<Int>
}
