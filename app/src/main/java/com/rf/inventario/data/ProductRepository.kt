package com.rf.inventario.data

import kotlinx.coroutines.flow.Flow

class ProductRepository(private val dao: ProductDao) {

    fun products(query: String): Flow<List<Product>> =
        if (query.isBlank()) dao.observeAll() else dao.search(query.trim())

    suspend fun getById(id: Long): Product? = dao.getById(id)

    suspend fun findBySku(sku: String): Product? = dao.getBySku(sku.trim())

    suspend fun save(product: Product) {
        val stamped = product.copy(updatedAt = System.currentTimeMillis())
        if (stamped.id == 0L) dao.insert(stamped) else dao.update(stamped)
    }

    suspend fun delete(product: Product) = dao.delete(product)
}
