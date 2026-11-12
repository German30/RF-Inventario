package com.rf.inventario.data.local

import android.content.Context
import com.rf.inventario.data.Product
import com.rf.inventario.data.ProductCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [ProductCache] respaldado por Room + SharedPreferences (para el timestamp de sync). */
class LocalProductCache(
    private val dao: ProductDao,
    context: Context,
) : ProductCache {

    private val prefs = context.applicationContext
        .getSharedPreferences("rf_product_cache", Context.MODE_PRIVATE)

    override suspend fun getAll(): List<Product> = dao.getAll().map { it.toDomain() }

    override suspend fun search(query: String): List<Product> =
        dao.search(query.trim()).map { it.toDomain() }

    override suspend fun getById(id: Long): Product? = dao.getById(id)?.toDomain()

    override suspend fun getBySku(sku: String): Product? = dao.getBySku(sku.trim())?.toDomain()

    override suspend fun findByCode(code: String): Product? = dao.findByCode(code.trim())?.toDomain()

    override suspend fun replaceAll(products: List<Product>) {
        dao.clear()
        dao.upsertAll(products.map { it.toEntity() })
        touch()
    }

    override suspend fun upsert(vararg products: Product) {
        if (products.isEmpty()) return
        dao.upsertAll(products.map { it.toEntity() })
        touch()
    }

    override suspend fun remove(id: Long) {
        dao.delete(id)
    }

    override suspend fun removeAll(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteAll(ids)
    }

    override suspend fun adjustQuantity(id: Long, delta: Int) = dao.adjustQuantity(id, delta)

    override fun lastSyncedAt(): Long? = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }

    fun observe(query: String, filter: String, limit: Int = 500): Flow<List<Product>> =
        dao.observe(query.trim(), filter, limit).map { list -> list.map { it.toDomain() } }

    fun observeById(id: Long): Flow<Product?> = dao.observeById(id).map { it?.toDomain() }

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun markSynced() = touch()

    private fun touch() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    private companion object {
        const val KEY_LAST_SYNC = "last_synced_at"
    }
}
