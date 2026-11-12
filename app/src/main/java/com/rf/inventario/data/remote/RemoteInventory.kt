package com.rf.inventario.data.remote

import com.rf.inventario.data.CountSession
import com.rf.inventario.data.Dashboard
import com.rf.inventario.data.Movement
import com.rf.inventario.data.Order
import com.rf.inventario.data.OrderType
import com.rf.inventario.data.Page
import com.rf.inventario.data.Product
import com.rf.inventario.data.StockLevel
import com.rf.inventario.data.ops.OperationSender
import com.rf.inventario.data.remote.dto.ConteoAbrirDto
import com.rf.inventario.data.remote.dto.ConteoLineaBodyDto
import com.rf.inventario.data.remote.dto.OperacionBodyDto
import com.rf.inventario.data.remote.dto.PedidoEscaneoDto
import com.rf.inventario.data.remote.dto.toDomain

/** [OperationSender] contra el backend. */
class RemoteOperationSender(private val api: PanelApi) : OperationSender {
    override suspend fun movement(body: OperacionBodyDto): Product? =
        apiCall { api.operar(body).producto.toDomain() }

    override suspend fun countLine(countId: Long, body: ConteoLineaBodyDto) {
        apiCall { api.contar(countId, body) }
    }

    override suspend fun orderScan(orderId: Long, body: PedidoEscaneoDto) {
        apiCall { api.escanearPedido(orderId, body) }
    }
}

/**
 * Consultas que solo tienen sentido online (historial, conteos, pedidos,
 * existencias por ubicación...). Todas lanzan [com.rf.inventario.data.ApiException].
 */
class InventoryRepository(private val api: PanelApi) {

    suspend fun dashboard(): Dashboard = apiCall { api.dashboard().toDomain() }

    suspend fun stockLevels(productId: Long): List<StockLevel> =
        apiCall { api.existencias(productId).map { it.toDomain() } }

    suspend fun productMovements(productId: Long): List<Movement> =
        apiCall { api.movimientosDe(productId).map { it.toDomain() } }

    suspend fun history(sku: String?, action: String?, page: Int): Page<Movement> = apiCall {
        val p = api.movimientos(sku?.takeIf { it.isNotBlank() }, action, page)
        Page(p.contenido.map { it.toDomain() }, p.pagina, p.totalPaginas, p.total)
    }

    suspend fun locationContents(code: String): List<StockLevel> =
        apiCall { api.contenidoUbicacion(code).map { it.toDomain() } }

    suspend fun categories(): List<String> = apiCall { api.categorias() }

    // ---- Conteos
    suspend fun counts(): List<CountSession> = apiCall { api.conteos().map { it.toDomain() } }

    suspend fun count(id: Long): CountSession = apiCall { api.conteo(id).toDomain() }

    suspend fun openCount(location: String?, blind: Boolean, note: String?): CountSession =
        apiCall { api.abrirConteo(ConteoAbrirDto(location?.takeIf { it.isNotBlank() }, blind, note)).toDomain() }

    /** `action`: cerrar | reabrir | aplicar | cancelar. */
    suspend fun countAction(id: Long, action: String): CountSession =
        apiCall { api.accionConteo(id, action).toDomain() }

    // ---- Pedidos
    suspend fun orders(type: OrderType): List<Order> = apiCall { api.pedidos(type.name).map { it.toDomain() } }

    suspend fun order(id: Long): Order = apiCall { api.pedido(id).toDomain() }

    suspend fun completeOrder(id: Long): Order = apiCall { api.completarPedido(id).toDomain() }
}
