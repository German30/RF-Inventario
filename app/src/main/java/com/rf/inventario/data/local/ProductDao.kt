package com.rf.inventario.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products_cache ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<ProductEntity>

    @Query(
        "SELECT * FROM products_cache " +
            "WHERE name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    suspend fun search(query: String): List<ProductEntity>

    /**
     * Lista reactiva para la pantalla de inventario. `filter`: ALL, LOW
     * (bajo mínimo) u OUT (sin stock). El límite evita inflar la UI con un
     * catálogo de decenas de miles: la búsqueda acota.
     */
    @Query(
        "SELECT * FROM products_cache " +
            "WHERE (:query = '' OR name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' " +
            "       OR altCodes LIKE '%|' || :query || '|%' OR category LIKE '%' || :query || '%') " +
            "AND (:filter = 'ALL' " +
            "     OR (:filter = 'LOW' AND minStock > 0 AND quantity <= minStock) " +
            "     OR (:filter = 'OUT' AND quantity <= 0)) " +
            "ORDER BY name COLLATE NOCASE ASC LIMIT :limit",
    )
    fun observe(query: String, filter: String, limit: Int): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products_cache")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM products_cache WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM products_cache WHERE id = :id")
    fun observeById(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products_cache WHERE sku = :sku COLLATE NOCASE LIMIT 1")
    suspend fun getBySku(sku: String): ProductEntity?

    /** Lo escaneado puede ser el SKU o cualquier código alternativo. */
    @Query(
        "SELECT * FROM products_cache WHERE sku = :code COLLATE NOCASE " +
            "OR altCodes LIKE '%|' || :code || '|%' LIMIT 1",
    )
    suspend fun findByCode(code: String): ProductEntity?

    @Query("UPDATE products_cache SET quantity = quantity + :delta WHERE id = :id")
    suspend fun adjustQuantity(id: Long, delta: Int)

    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products_cache WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM products_cache WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM products_cache")
    suspend fun clear()
}
