package com.rf.inventario.data

/** De dónde salió la lista que se está mostrando. */
enum class DataSource { REMOTE, CACHE }

data class ProductListResult(
    val products: List<Product>,
    val source: DataSource,
    /** Cuándo se guardó el snapshot de caché mostrado; null si [source] es REMOTE. */
    val cachedAt: Long?,
)

/**
 * Combina el backend (fuente de verdad) con [cache] (copia local de solo
 * lectura): si el backend no responde, cae al último snapshot guardado en vez
 * de dejar la pantalla vacía. Las escrituras son siempre online: si fallan, se
 * propaga el error y no se tocan datos locales a medias.
 */
class ProductRepository(
    private val remote: ProductStore,
    private val cache: ProductCache,
) {

    suspend fun list(query: String): ProductListResult = try {
        val products = if (query.isBlank()) remote.getAll() else remote.search(query.trim())
        if (query.isBlank()) cache.replaceAll(products) else cache.upsert(*products.toTypedArray())
        ProductListResult(products, DataSource.REMOTE, cachedAt = null)
    } catch (e: ApiException) {
        val cached = if (query.isBlank()) cache.getAll() else cache.search(query)
        ProductListResult(cached, DataSource.CACHE, cachedAt = cache.lastSyncedAt())
    }

    suspend fun getById(id: Long): Product? = try {
        remote.getById(id)?.also { cache.upsert(it) }
    } catch (e: ApiException) {
        cache.getById(id)
    }

    suspend fun findBySku(sku: String): Product? = try {
        remote.getBySku(sku.trim())?.also { cache.upsert(it) }
    } catch (e: ApiException) {
        cache.getBySku(sku.trim())
    }

    /** @throws ApiException si falla (incluye [ConflictException] si alguien más lo editó primero). */
    suspend fun save(product: Product) {
        // El servidor decide el `updatedAt` real; lo que guardamos en caché es
        // su respuesta, no lo que mandamos, para no inventar falsos conflictos
        // la próxima vez que se edite este producto.
        val saved = if (product.id == 0L) remote.insert(product) else remote.update(product)
        cache.upsert(saved)
    }

    /** @throws ApiException si falla. */
    suspend fun delete(product: Product) {
        remote.delete(product)
        cache.remove(product.id)
    }
}
