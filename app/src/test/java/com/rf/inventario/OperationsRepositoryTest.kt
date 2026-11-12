package com.rf.inventario

import com.rf.inventario.data.NetworkException
import com.rf.inventario.data.Product
import com.rf.inventario.data.ProductCache
import com.rf.inventario.data.RejectedException
import com.rf.inventario.data.ops.OperationSender
import com.rf.inventario.data.ops.OperationsRepository
import com.rf.inventario.data.ops.PendingOp
import com.rf.inventario.data.ops.PendingOpStore
import com.rf.inventario.data.ops.SubmitResult
import com.rf.inventario.data.remote.dto.ConteoLineaBodyDto
import com.rf.inventario.data.remote.dto.OperacionBodyDto
import com.rf.inventario.data.remote.dto.PedidoEscaneoDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationsRepositoryTest {

    /** Servidor simulado: aplica ENTRADA/SALIDA sobre un stock y deduplica por clientOpId. */
    private class FakeServer : OperationSender {
        var online = true
        var stock = 10
        val applied = mutableListOf<String>()

        override suspend fun movement(body: OperacionBodyDto): Product? {
            if (!online) throw NetworkException()
            if (body.clientOpId in applied) return product()
            val delta = if (body.tipo == "SALIDA") -body.cantidad else body.cantidad
            if (stock + delta < 0) throw RejectedException("Stock insuficiente", 409)
            stock += delta
            applied += body.clientOpId!!
            return product()
        }

        override suspend fun countLine(countId: Long, body: ConteoLineaBodyDto) {
            if (!online) throw NetworkException()
            applied += body.clientOpId!!
        }

        override suspend fun orderScan(orderId: Long, body: PedidoEscaneoDto) {
            if (!online) throw NetworkException()
            applied += body.clientOpId!!
        }

        fun product() = Product(id = 1, name = "Cable", sku = "C1", quantity = stock, location = "A1")
    }

    private class MemoryStore : PendingOpStore {
        val ops = MutableStateFlow<List<PendingOp>>(emptyList())
        private var seq = 0L
        override suspend fun add(op: PendingOp): Long = (++seq).also { ops.value = ops.value + op.copy(id = it) }
        override suspend fun pending() = ops.value.filter { it.status == PendingOp.Status.PENDING }
        override suspend fun markSent(id: Long) { ops.value = ops.value.filterNot { it.id == id } }
        override suspend fun markFailed(id: Long, error: String) = update(id) { it.copy(status = PendingOp.Status.FAILED, lastError = error) }
        override suspend fun markRetryLater(id: Long, error: String) = update(id) { it.copy(lastError = error, attempts = it.attempts + 1) }
        override suspend fun retry(id: Long) = update(id) { it.copy(status = PendingOp.Status.PENDING) }
        override suspend fun discard(id: Long) = markSent(id)
        override fun observeAll(): Flow<List<PendingOp>> = ops
        override fun observePendingCount(): Flow<Int> = ops.map { l -> l.count { it.status == PendingOp.Status.PENDING } }
        override fun observeFailedCount(): Flow<Int> = ops.map { l -> l.count { it.status == PendingOp.Status.FAILED } }
        private fun update(id: Long, f: (PendingOp) -> PendingOp) { ops.value = ops.value.map { if (it.id == id) f(it) else it } }
    }

    private class MemoryCache : ProductCache {
        val items = mutableMapOf(1L to Product(id = 1, name = "Cable", sku = "C1", quantity = 10, location = "A1"))
        override suspend fun getAll() = items.values.toList()
        override suspend fun search(query: String) = getAll()
        override suspend fun getById(id: Long) = items[id]
        override suspend fun getBySku(sku: String) = items.values.find { it.sku == sku }
        override suspend fun replaceAll(products: List<Product>) { items.clear(); products.forEach { items[it.id] = it } }
        override suspend fun upsert(vararg products: Product) { products.forEach { items[it.id] = it } }
        override suspend fun remove(id: Long) { items.remove(id) }
        override fun lastSyncedAt(): Long? = null
    }

    private var ids = 0
    private fun repo(server: FakeServer, store: MemoryStore, cache: MemoryCache) =
        OperationsRepository(server, store, cache, newId = { "op-${++ids}" })

    private fun entrada(n: Int) = OperacionBodyDto(tipo = "ENTRADA", productoId = 1, cantidad = n)
    private fun salida(n: Int) = OperacionBodyDto(tipo = "SALIDA", productoId = 1, cantidad = n)

    @Test
    fun online_seAplicaYActualizaElCache() = runTest {
        val server = FakeServer(); val store = MemoryStore(); val cache = MemoryCache()

        val r = repo(server, store, cache).movement(entrada(5), "in", 1, 5)

        assertTrue(r is SubmitResult.Done)
        assertEquals(15, cache.items[1]!!.quantity)
        assertTrue(store.ops.value.isEmpty())
    }

    @Test
    fun rechazoDelServidor_noSeEncola() = runTest {
        val server = FakeServer(); val store = MemoryStore(); val cache = MemoryCache()

        val r = repo(server, store, cache).movement(salida(99), "out", 1, -99)

        assertTrue(r is SubmitResult.Rejected)
        assertTrue(store.ops.value.isEmpty())
        assertEquals(10, cache.items[1]!!.quantity)
    }

    @Test
    fun sinRed_seEncolaConAjusteOptimistaYSeEnviaAlVolver() = runTest {
        val server = FakeServer().apply { online = false }
        val store = MemoryStore(); val cache = MemoryCache()
        val repo = repo(server, store, cache)

        val r = repo.movement(salida(3), "out", 1, -3)

        assertEquals(SubmitResult.Queued, r)
        assertEquals(7, cache.items[1]!!.quantity) // el operario ya lo ve descontado
        assertEquals(1, store.ops.value.size)

        server.online = true
        val drained = repo.drain()

        assertEquals(1, drained.sent)
        assertEquals(7, server.stock)
        assertTrue(store.ops.value.isEmpty())
    }

    @Test
    fun reintentoDeAlgoQueYaLlego_noDuplica() = runTest {
        val server = FakeServer(); val store = MemoryStore(); val cache = MemoryCache()
        val repo = repo(server, store, cache)
        // Simula: el servidor aplicó op-1 pero la respuesta se perdió.
        server.applied += "op-1"
        server.stock = 15
        store.add(PendingOp(clientOpId = "op-1", kind = PendingOp.Kind.MOVEMENT, targetId = null,
            payload = """{"tipo":"ENTRADA","productoId":1,"cantidad":5,"clientOpId":"op-1"}""", summary = "in", createdAt = 0))

        repo.drain()

        assertEquals(15, server.stock)
    }

    @Test
    fun unaOperacionNuevaNoSeAdelantaALaCola() = runTest {
        val server = FakeServer().apply { stock = 0; online = false }
        val store = MemoryStore(); val cache = MemoryCache()
        val repo = repo(server, store, cache)

        repo.movement(entrada(5), "in", 1, 5)   // queda en cola
        server.online = true
        val r = repo.movement(salida(5), "out", 1, -5) // depende de la entrada encolada

        // Primero se vació la cola (entrada) y recién después se aplicó la salida.
        assertTrue(r is SubmitResult.Done)
        assertEquals(0, server.stock)
        assertEquals(listOf("op-1", "op-2"), server.applied)
    }

    @Test
    fun drain_marcaComoFallidaLaRechazadaYSigueConLasDemas() = runTest {
        val server = FakeServer().apply { online = false; stock = 1 }
        val store = MemoryStore(); val cache = MemoryCache()
        val repo = repo(server, store, cache)
        repo.movement(salida(5), "out-imposible", 1, -5)
        repo.movement(entrada(2), "in", 1, 2)

        server.online = true
        val result = repo.drain()

        assertEquals(1, result.sent)
        assertEquals(1, result.failed)
        assertEquals(PendingOp.Status.FAILED, store.ops.value.single().status)
        assertEquals(3, server.stock)
    }
}
