package com.rf.inventario.data

/**
 * Operaciones de persistencia de productos. La implementación real
 * ([com.rf.inventario.data.remote.RemoteProductStore]) llama al backend Spring;
 * los tests usan un doble en memoria.
 */
interface ProductStore {
    suspend fun getAll(): List<Product>
    suspend fun search(query: String): List<Product>
    suspend fun getById(id: Long): Product?
    suspend fun getBySku(sku: String): Product?

    /** @return el producto tal como quedó en el servidor (id y `updatedAt` reales). */
    suspend fun insert(product: Product): Product

    /** @return el producto tal como quedó en el servidor (`updatedAt` real, no el propuesto). */
    suspend fun update(product: Product): Product

    suspend fun delete(product: Product)
}
