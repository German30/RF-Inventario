package com.rf.inventario.data

/**
 * Copia local del inventario, usada cuando el backend no responde. La
 * implementación real ([com.rf.inventario.data.local.LocalProductCache]) vive
 * en Room; los tests usan un doble en memoria.
 */
interface ProductCache {
    suspend fun getAll(): List<Product>
    suspend fun search(query: String): List<Product>
    suspend fun getById(id: Long): Product?
    suspend fun getBySku(sku: String): Product?

    /** Reemplaza todo el caché por un snapshot fresco (fetch sin filtro). */
    suspend fun replaceAll(products: List<Product>)

    /** Agrega/actualiza sin tocar el resto del caché. */
    suspend fun upsert(vararg products: Product)

    suspend fun remove(id: Long)

    /** Cuándo se sincronizó el caché por última vez con el servidor, o null si nunca. */
    fun lastSyncedAt(): Long?

    /** SKU o código alternativo exacto. */
    suspend fun findByCode(code: String): Product? =
        getBySku(code) ?: getAll().firstOrNull { it.matchesCode(code) }

    /**
     * Ajuste optimista de una operación encolada offline: el operario ve el
     * stock movido ya, y la próxima sync lo reemplaza por el valor real.
     */
    suspend fun adjustQuantity(id: Long, delta: Int) {
        getById(id)?.let { upsert(it.copy(quantity = it.quantity + delta)) }
    }

    suspend fun removeAll(ids: List<Long>) = ids.forEach { remove(it) }
}
