package com.rf.inventario

import com.rf.inventario.data.ApiException
import com.rf.inventario.data.ConflictException
import com.rf.inventario.data.DataSource
import com.rf.inventario.data.Product
import com.rf.inventario.data.ProductCache
import com.rf.inventario.data.ProductRepository
import com.rf.inventario.data.ProductStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProductRepositoryTest {

    /** Backend falso: `offline = true` hace que cualquier operación tire [ApiException]. */
    private class FakeProductStore : ProductStore {
        var offline = false
        var failWithConflict: ConflictException? = null
        private var nextId = 1L
        private val data = mutableMapOf<Long, Product>()

        fun seed(product: Product) {
            data[product.id] = product
        }

        private fun checkOnline() {
            if (offline) throw ApiException("Sin conexión con el servidor")
        }

        override suspend fun getAll(): List<Product> {
            checkOnline()
            return data.values.toList()
        }

        override suspend fun search(query: String): List<Product> {
            checkOnline()
            return data.values.filter { it.name.contains(query) || it.sku.contains(query) }
        }

        override suspend fun getById(id: Long): Product? {
            checkOnline()
            return data[id]
        }

        override suspend fun getBySku(sku: String): Product? {
            checkOnline()
            return data.values.find { it.sku == sku }
        }

        override suspend fun insert(product: Product): Product {
            checkOnline()
            // El servidor decide id y updatedAt: distintos a lo que mandó el cliente.
            val saved = product.copy(id = nextId++, updatedAt = 111L)
            data[saved.id] = saved
            return saved
        }

        override suspend fun update(product: Product): Product {
            checkOnline()
            failWithConflict?.let { throw it }
            val saved = product.copy(updatedAt = 222L)
            data[saved.id] = saved
            return saved
        }

        override suspend fun delete(product: Product) {
            checkOnline()
            data.remove(product.id)
        }
    }

    private class FakeProductCache : ProductCache {
        val stored = mutableMapOf<Long, Product>()
        var syncedAt: Long? = null

        override suspend fun getAll(): List<Product> = stored.values.toList()
        override suspend fun search(query: String): List<Product> =
            stored.values.filter { it.name.contains(query) || it.sku.contains(query) }
        override suspend fun getById(id: Long): Product? = stored[id]
        override suspend fun getBySku(sku: String): Product? = stored.values.find { it.sku == sku }

        override suspend fun replaceAll(products: List<Product>) {
            stored.clear()
            products.forEach { stored[it.id] = it }
            syncedAt = 999L
        }

        override suspend fun upsert(vararg products: Product) {
            products.forEach { stored[it.id] = it }
            syncedAt = 999L
        }

        override suspend fun remove(id: Long) {
            stored.remove(id)
        }

        override fun lastSyncedAt(): Long? = syncedAt
    }

    private fun product(id: Long = 0, sku: String = "CBL1") =
        Product(id = id, name = "Cable", sku = sku, quantity = 5, location = "A1")

    @Test
    fun `save inserts when id is zero y cachea lo que confirma el servidor`() = runTest {
        val store = FakeProductStore()
        val cache = FakeProductCache()
        val repo = ProductRepository(store, cache)

        repo.save(product(id = 0))

        val cached = cache.stored.values.single()
        assertEquals(111L, cached.updatedAt) // el timestamp real del servidor, no uno local
        assertTrue(cached.id != 0L)
    }

    @Test
    fun `save updates when id is present`() = runTest {
        val store = FakeProductStore()
        store.seed(product(id = 7))
        val cache = FakeProductCache()
        val repo = ProductRepository(store, cache)

        repo.save(product(id = 7))

        assertEquals(222L, cache.stored[7]?.updatedAt)
    }

    @Test
    fun `save propaga el conflicto cuando otro operario edito primero`() = runTest {
        val store = FakeProductStore()
        store.seed(product(id = 7))
        store.failWithConflict = ConflictException("Otro cambio se guardó mientras editabas.", null)
        val repo = ProductRepository(store, FakeProductCache())

        try {
            repo.save(product(id = 7))
            fail("debía lanzar ConflictException")
        } catch (e: ConflictException) {
            assertEquals("Otro cambio se guardó mientras editabas.", e.message)
        }
    }

    @Test
    fun `list trae del servidor y refresca el cache cuando hay conexion`() = runTest {
        val store = FakeProductStore()
        store.seed(product(id = 1))
        val cache = FakeProductCache()
        val repo = ProductRepository(store, cache)

        val result = repo.list("")

        assertEquals(DataSource.REMOTE, result.source)
        assertEquals(1, result.products.size)
        assertEquals(1, cache.stored.size) // quedó reflejado en el caché
    }

    @Test
    fun `list cae al cache cuando el backend no responde`() = runTest {
        val store = FakeProductStore()
        val cache = FakeProductCache()
        cache.replaceAll(listOf(product(id = 1)))
        store.offline = true
        val repo = ProductRepository(store, cache)

        val result = repo.list("")

        assertEquals(DataSource.CACHE, result.source)
        assertEquals(1, result.products.size)
        assertEquals(999L, result.cachedAt)
    }

    @Test
    fun `save sin conexion no toca el cache`() = runTest {
        val store = FakeProductStore()
        store.offline = true
        val cache = FakeProductCache()
        val repo = ProductRepository(store, cache)

        try {
            repo.save(product(id = 0))
            fail("debía lanzar ApiException")
        } catch (e: ApiException) {
            assertTrue(cache.stored.isEmpty())
        }
    }
}
