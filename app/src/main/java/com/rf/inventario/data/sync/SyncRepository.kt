package com.rf.inventario.data.sync

import android.content.Context
import com.rf.inventario.data.ProductCache
import com.rf.inventario.data.local.LocationDao
import com.rf.inventario.data.local.toEntity
import com.rf.inventario.data.ops.OperationsRepository
import com.rf.inventario.data.remote.PanelApi
import com.rf.inventario.data.remote.apiCall
import com.rf.inventario.data.remote.dto.toDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Estado visible de la sincronización (chip de la pantalla de inicio). */
data class SyncState(
    val running: Boolean = false,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null,
) {
    val online: Boolean get() = lastError == null && lastSuccessAt != null
}

/**
 * Mantiene el caché local al día: primero manda la cola de operaciones
 * pendientes (para que el servidor tenga lo último del operario) y después
 * baja lo que cambió desde el último cursor.
 */
class SyncRepository(
    private val api: PanelApi,
    private val cache: ProductCache,
    private val locations: LocationDao,
    private val operations: OperationsRepository,
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("rf_sync", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _state = MutableStateFlow(SyncState(lastSuccessAt = cache.lastSyncedAt()))
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** @param full true descarta el cursor y baja todo el catálogo. */
    suspend fun sync(full: Boolean = false): Result<Unit> = lock.withLock {
        _state.value = _state.value.copy(running = true)
        val result = runCatching {
            operations.drain()
            val desde = if (full) 0L else prefs.getLong(KEY_CURSOR, 0L)
            val cambios = apiCall { api.sync(desde) }
            val productos = cambios.productos.map { it.toDomain() }
            if (cambios.completa) {
                cache.replaceAll(productos)
            } else {
                cache.upsert(*productos.toTypedArray())
                cache.removeAll(cambios.eliminados)
            }
            locations.clear()
            locations.upsertAll(cambios.ubicaciones.map { it.toDomain().toEntity() })
            prefs.edit().putLong(KEY_CURSOR, cambios.cursor).apply()
            // Ajustes optimistas de operaciones que siguen en cola se pisan con el
            // valor del servidor; se vuelven a ver bien al enviarse la cola.
        }
        _state.value = SyncState(
            running = false,
            lastSuccessAt = if (result.isSuccess) System.currentTimeMillis() else _state.value.lastSuccessAt,
            lastError = result.exceptionOrNull()?.message,
        )
        result
    }

    fun resetCursor() {
        prefs.edit().remove(KEY_CURSOR).apply()
    }

    private companion object {
        const val KEY_CURSOR = "cursor"
    }
}
