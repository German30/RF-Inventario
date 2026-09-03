package com.rf.inventario

import com.rf.inventario.data.Product
import com.rf.inventario.data.ProductDao
import com.rf.inventario.data.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductRepositoryTest {

    private class FakeDao : ProductDao {
        val inserted = mutableListOf<Product>()
        val updated = mutableListOf<Product>()
        val deleted = mutableListOf<Product>()

        override fun observeAll(): Flow<List<Product>> = flowOf(emptyList())
        override fun search(query: String): Flow<List<Product>> = flowOf(emptyList())
        override suspend fun getById(id: Long): Product? = null
        override suspend fun getBySku(sku: String): Product? = inserted.plus(updated).find { it.sku == sku }
        override suspend fun insert(product: Product): Long {
            inserted += product
            return 1L
        }

        override suspend fun update(product: Product) {
            updated += product
        }

        override suspend fun delete(product: Product) {
            deleted += product
        }
    }

    @Test
    fun `save inserts when id is zero`() = runTest {
        val dao = FakeDao()
        val repo = ProductRepository(dao)

        repo.save(Product(id = 0, name = "Cable", sku = "CBL1", quantity = 5, location = "A1"))

        assertEquals(1, dao.inserted.size)
        assertTrue(dao.updated.isEmpty())
    }

    @Test
    fun `save updates when id is present`() = runTest {
        val dao = FakeDao()
        val repo = ProductRepository(dao)

        repo.save(Product(id = 7, name = "Cable", sku = "CBL1", quantity = 5, location = "A1"))

        assertEquals(1, dao.updated.size)
        assertTrue(dao.inserted.isEmpty())
    }
}
