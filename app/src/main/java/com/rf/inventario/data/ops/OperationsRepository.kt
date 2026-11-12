package com.rf.inventario.data.ops

import com.google.gson.Gson
import com.rf.inventario.data.ApiException
import com.rf.inventario.data.NetworkException
import com.rf.inventario.data.Product
import com.rf.inventario.data.ProductCache
import com.rf.inventario.data.RejectedException
import com.rf.inventario.data.remote.dto.ConteoLineaBodyDto
import com.rf.inventario.data.remote.dto.OperacionBodyDto
import com.rf.inventario.data.remote.dto.PedidoEscaneoDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Lo que el envío al backend necesita: una llamada por tipo de operación. */
interface OperationSender {
    /** @return el producto como quedó en el servidor (si la respuesta lo trae). */
    suspend fun movement(body: OperacionBodyDto): Product?
    suspend fun countLine(countId: Long, body: ConteoLineaBodyDto)
    suspend fun orderScan(orderId: Long, body: PedidoEscaneoDto)
}

/** Resultado de una operación desde el punto de vista del operario. */
sealed interface SubmitResult {
    /** Aplicada en el servidor. */
    data class Done(val product: Product?) : SubmitResult

    /** Sin red: quedó en la cola y se reflejó localmente; se envía sola al volver la conexión. */
    data object Queued : SubmitResult

    /** El servidor la rechazó (stock insuficiente, etc.). No se encola: reintentar no cambia nada. */
    data class Rejected(val message: String) : SubmitResult
}

data class DrainResult(val sent: Int, val failed: Int, val remaining: Int)

/**
 * Cola de operaciones con envío inmediato y respaldo offline.
 *
 *  1. Cada operación lleva un `clientOpId` (UUID) desde que nace.
 *  2. Se intenta mandar ya. Si el servidor responde, listo (o rechazo visible).
 *  3. Si NO hay respuesta (sin red, timeout), se encola con ese mismo id y se
 *     ajusta el caché local para que el operario vea el stock movido.
 *  4. [drain] reenvía la cola en orden. El backend descarta los id repetidos,
 *     así que un timeout que en realidad sí llegó no duplica el movimiento.
 */
class OperationsRepository(
    private val sender: OperationSender,
    private val store: PendingOpStore,
    private val cache: ProductCache,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Se llama al encolar: la app programa un envío para cuando vuelva la red. */
    private val onQueued: () -> Unit = {},
) {

    private val drainLock = Mutex()

    val pendingCount: Flow<Int> get() = store.observePendingCount()
    val failedCount: Flow<Int> get() = store.observeFailedCount()
    fun observeAll(): Flow<List<PendingOp>> = store.observeAll()

    /**
     * @param localDelta cuánto mover el stock total en el caché si queda encolada
     *   (ENTRADA +n, SALIDA −n, TRANSFERENCIA 0).
     */
    suspend fun movement(body: OperacionBodyDto, summary: String, productId: Long?, localDelta: Int): SubmitResult {
        val withId = body.copy(clientOpId = body.clientOpId ?: newId())
        return submit(PendingOp.Kind.MOVEMENT, null, withId.clientOpId!!, gson.toJson(withId), summary,
            productId, localDelta) { sender.movement(withId) }
    }

    suspend fun countLine(countId: Long, body: ConteoLineaBodyDto, summary: String): SubmitResult {
        val withId = body.copy(clientOpId = body.clientOpId ?: newId())
        return submit(PendingOp.Kind.COUNT_LINE, countId, withId.clientOpId!!, gson.toJson(withId), summary,
            null, 0) { sender.countLine(countId, withId); null }
    }

    suspend fun orderScan(orderId: Long, body: PedidoEscaneoDto, summary: String, productId: Long?, localDelta: Int): SubmitResult {
        val withId = body.copy(clientOpId = body.clientOpId ?: newId())
        return submit(PendingOp.Kind.ORDER_SCAN, orderId, withId.clientOpId!!, gson.toJson(withId), summary,
            productId, localDelta) { sender.orderScan(orderId, withId); null }
    }

    private suspend fun submit(
        kind: PendingOp.Kind,
        targetId: Long?,
        clientOpId: String,
        payload: String,
        summary: String,
        productId: Long?,
        localDelta: Int,
        send: suspend () -> Product?,
    ): SubmitResult {
        suspend fun enqueue(error: String?): SubmitResult {
            store.add(PendingOp(clientOpId = clientOpId, kind = kind, targetId = targetId, payload = payload,
                summary = summary, createdAt = clock(), lastError = error))
            if (productId != null && localDelta != 0) cache.adjustQuantity(productId, localDelta)
            onQueued()
            return SubmitResult.Queued
        }

        // El orden importa (una salida puede depender de una entrada encolada
        // antes): si hay cola, primero se vacía; si no se pudo, esta va al final.
        if (store.pending().isNotEmpty() && drain().remaining > 0) {
            return enqueue("En cola detrás de operaciones anteriores")
        }
        return try {
            val product = send()
            product?.let { cache.upsert(it) }
            SubmitResult.Done(product)
        } catch (e: RejectedException) {
            SubmitResult.Rejected(e.message ?: "Operación rechazada")
        } catch (e: ApiException) {
            // Sin respuesta o 5xx: puede o no haberse aplicado. Se encola con el
            // mismo clientOpId; el servidor deduplica.
            enqueue(e.message)
        }
    }

    /** Reenvía la cola en orden. Se corta en el primer error de red (no tiene sentido seguir). */
    suspend fun drain(): DrainResult = drainLock.withLock {
        var sent = 0
        var failed = 0
        val ops = store.pending()
        for ((index, op) in ops.withIndex()) {
            try {
                when (op.kind) {
                    PendingOp.Kind.MOVEMENT ->
                        sender.movement(gson.fromJson(op.payload, OperacionBodyDto::class.java))?.let { cache.upsert(it) }
                    PendingOp.Kind.COUNT_LINE ->
                        sender.countLine(op.targetId!!, gson.fromJson(op.payload, ConteoLineaBodyDto::class.java))
                    PendingOp.Kind.ORDER_SCAN ->
                        sender.orderScan(op.targetId!!, gson.fromJson(op.payload, PedidoEscaneoDto::class.java))
                }
                store.markSent(op.id)
                sent++
            } catch (e: RejectedException) {
                store.markFailed(op.id, e.message ?: "Rechazada por el servidor")
                failed++
            } catch (e: NetworkException) {
                store.markRetryLater(op.id, e.message ?: "Sin conexión")
                return@withLock DrainResult(sent, failed, ops.size - index)
            } catch (e: ApiException) {
                // 5xx: problema del servidor, se reintenta en la próxima pasada.
                store.markRetryLater(op.id, e.message ?: "Error del servidor")
                return@withLock DrainResult(sent, failed, ops.size - index)
            }
        }
        DrainResult(sent, failed, 0)
    }

    suspend fun retry(id: Long) = store.retry(id)

    suspend fun discard(id: Long) = store.discard(id)
}
