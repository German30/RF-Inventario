package com.rf.inventario.data.remote.dto

import com.rf.inventario.data.CountLine
import com.rf.inventario.data.CountSession
import com.rf.inventario.data.Dashboard
import com.rf.inventario.data.Location
import com.rf.inventario.data.Movement
import com.rf.inventario.data.Order
import com.rf.inventario.data.OrderLine
import com.rf.inventario.data.OrderType
import com.rf.inventario.data.Product
import com.rf.inventario.data.StockLevel

/** Cuerpos y respuestas del backend rf-inventario-panel-api. Campos nuevos nullable: tolera backends viejos. */

data class LoginRequestDto(
    val codigo: String,
    val pin: String,
)

/** Respuesta de `GET /api/ping`. [apiVersion] falta en backends anteriores a la v6. */
data class PingDto(
    val status: String?,
    val service: String?,
    val apiVersion: Int?,
)

data class SesionDto(
    val token: String?,
    val expiraEn: Long?,
    val codigo: String,
    val nombre: String,
    val rol: String? = null,
)

data class ProductoDto(
    val id: Long,
    val nombre: String,
    val sku: String,
    val cantidad: Int,
    val ubicacion: String?,
    val actualizadoEn: Long,
    val descripcion: String? = null,
    val categoria: String? = null,
    val unidad: String? = null,
    val stockMinimo: Int? = null,
    val costoUnitario: Double? = null,
    val controlaLote: Boolean? = null,
    val codigosAlternativos: List<String>? = null,
)

data class ProductoBodyDto(
    val nombre: String,
    val sku: String,
    val cantidad: Int,
    val ubicacion: String,
    /** Versión leída (para detectar ediciones concurrentes); null en un alta. */
    val actualizadoEn: Long? = null,
    val descripcion: String? = null,
    val categoria: String? = null,
    val unidad: String? = null,
    val stockMinimo: Int? = null,
    val costoUnitario: Double? = null,
    val controlaLote: Boolean? = null,
    val codigosAlternativos: List<String>? = null,
)

data class AjusteStockDto(
    val delta: Int,
)

/** POST /api/operaciones. */
data class OperacionBodyDto(
    val tipo: String,
    val productoId: Long? = null,
    val codigo: String? = null,
    val cantidad: Int,
    val ubicacion: String? = null,
    val ubicacionDestino: String? = null,
    val lote: String? = null,
    val vencimiento: String? = null,
    val motivo: String? = null,
    val referencia: String? = null,
    val clientOpId: String? = null,
)

data class OperacionResultadoDto(
    val producto: ProductoDto,
    val movimientos: List<MovimientoDto>?,
    val repetida: Boolean,
)

data class MovimientoDto(
    val id: Long,
    val sku: String,
    val operadorCodigo: String,
    val operadorNombre: String?,
    val accion: String,
    val delta: Int,
    val cantidadDespues: Int,
    val creadoEn: Long,
    val productoId: Long?,
    val motivo: String?,
    val referencia: String?,
    val ubicacion: String?,
    val ubicacionDestino: String?,
    val lote: String?,
)

data class PaginaDto<T>(
    val contenido: List<T>,
    val pagina: Int,
    val tamanio: Int,
    val total: Long,
    val totalPaginas: Int,
)

data class ExistenciaDto(
    val productoId: Long,
    val sku: String?,
    val nombre: String?,
    val ubicacion: String,
    val lote: String?,
    val vence: String?,
    val cantidad: Int,
)

data class UbicacionDto(
    val id: Long?,
    val codigo: String,
    val descripcion: String?,
    val zona: String?,
    val activa: Boolean,
)

data class SyncDto(
    val productos: List<ProductoDto>,
    val eliminados: List<Long>,
    val ubicaciones: List<UbicacionDto>,
    val cursor: Long,
    val completa: Boolean,
)

data class ConteoAbrirDto(val ubicacion: String?, val ciego: Boolean, val nota: String?)

data class ConteoLineaBodyDto(
    val codigo: String,
    val cantidad: Int,
    val ubicacion: String? = null,
    val lote: String? = null,
    val sumar: Boolean = true,
    val clientOpId: String? = null,
)

data class ConteoDto(
    val id: Long,
    val ubicacion: String?,
    val ciego: Boolean,
    val estado: String,
    val nota: String?,
    val creadoPorNombre: String?,
    val creadoPorCodigo: String?,
    val creadoEn: Long,
    val lineasTotales: Int,
    val lineasContadas: Int,
    val lineasConDiferencia: Int?,
    val unidadesDiferencia: Int?,
    val lineas: List<ConteoLineaDto>?,
)

data class ConteoLineaDto(
    val id: Long,
    val productoId: Long,
    val sku: String,
    val nombre: String?,
    val ubicacion: String,
    val lote: String?,
    val esperado: Int?,
    val contado: Int?,
    val diferencia: Int?,
)

data class PedidoEscaneoDto(
    val codigo: String,
    val cantidad: Int,
    val ubicacion: String? = null,
    val lote: String? = null,
    val vencimiento: String? = null,
    val clientOpId: String? = null,
)

data class PedidoDto(
    val id: Long,
    val tipo: String,
    val numero: String,
    val contraparte: String?,
    val estado: String,
    val nota: String?,
    val creadoEn: Long,
    val unidadesPedidas: Int,
    val unidadesHechas: Int,
    val lineas: List<PedidoLineaDto>?,
)

data class PedidoLineaDto(
    val id: Long,
    val productoId: Long,
    val sku: String,
    val nombre: String?,
    val pedida: Int,
    val hecha: Int,
    val pendiente: Int,
    val sugerencias: List<ExistenciaDto>?,
)

data class DashboardDto(
    val productos: Long,
    val unidades: Long,
    val valorInventario: Double?,
    val sinStock: Long,
    val bajoMinimo: Long,
    val movimientosHoy: Long,
    val conteosAbiertos: Long,
    val recepcionesPendientes: Long,
    val pickingsPendientes: Long,
    val alertasBajoMinimo: List<ProductoDto>?,
    val proximosAVencer: List<ExistenciaDto>?,
)

data class ErrorClienteDto(
    val mensaje: String,
    val stack: String?,
    val version: String?,
    val dispositivo: String?,
    val ocurridoEn: Long?,
)

/** Forma del error que devuelve el backend (GlobalExceptionHandler). */
data class ApiErrorDto(
    val status: Int?,
    val error: String?,
    val message: String?,
)

/** Cuerpo del 409 cuando alguien más guardó el producto primero. */
data class ConflictoEdicionDto(
    val message: String?,
    val actual: ProductoDto?,
)

// ---------------------------------------------------------------- mappers

fun ProductoDto.toDomain(): Product = Product(
    id = id,
    name = nombre,
    sku = sku,
    quantity = cantidad,
    location = ubicacion.orEmpty(),
    updatedAt = actualizadoEn,
    description = descripcion.orEmpty(),
    category = categoria.orEmpty(),
    unit = unidad ?: "UN",
    minStock = stockMinimo ?: 0,
    unitCost = costoUnitario ?: 0.0,
    tracksLots = controlaLote ?: false,
    altCodes = codigosAlternativos.orEmpty(),
)

fun Product.toBody(): ProductoBodyDto = ProductoBodyDto(
    nombre = name.trim(),
    sku = sku.trim(),
    cantidad = quantity,
    ubicacion = location.trim(),
    // En un alta (id == 0) no hay versión que defender todavía.
    actualizadoEn = updatedAt.takeIf { id != 0L },
    descripcion = description.trim(),
    categoria = category.trim(),
    unidad = unit.trim().ifEmpty { "UN" },
    stockMinimo = minStock,
    costoUnitario = unitCost,
    controlaLote = tracksLots,
    codigosAlternativos = altCodes.map(String::trim).filter(String::isNotEmpty),
)

fun MovimientoDto.toDomain() = Movement(
    id = id, productId = productoId, sku = sku, action = accion, delta = delta, quantityAfter = cantidadDespues,
    createdAt = creadoEn, operatorCode = operadorCodigo, operatorName = operadorNombre.orEmpty(),
    reason = motivo.orEmpty(), reference = referencia.orEmpty(), location = ubicacion.orEmpty(),
    toLocation = ubicacionDestino.orEmpty(), lot = lote.orEmpty(),
)

fun ExistenciaDto.toDomain() = StockLevel(
    productId = productoId, sku = sku.orEmpty(), name = nombre.orEmpty(), location = ubicacion,
    lot = lote.orEmpty(), expiresOn = vence, quantity = cantidad,
)

fun UbicacionDto.toDomain() = Location(codigo, descripcion.orEmpty(), zona.orEmpty(), activa)

fun ConteoDto.toDomain() = CountSession(
    id = id, location = ubicacion.orEmpty(), blind = ciego, status = estado, note = nota.orEmpty(),
    createdBy = creadoPorNombre?.takeIf { it.isNotBlank() } ?: creadoPorCodigo.orEmpty(), createdAt = creadoEn,
    totalLines = lineasTotales, countedLines = lineasContadas, linesWithDifference = lineasConDiferencia,
    unitsDifference = unidadesDiferencia,
    lines = lineas.orEmpty().map {
        CountLine(it.id, it.productoId, it.sku, it.nombre.orEmpty(), it.ubicacion, it.lote.orEmpty(),
            it.esperado, it.contado, it.diferencia)
    },
)

fun PedidoDto.toDomain() = Order(
    id = id, type = OrderType.valueOf(tipo), number = numero, party = contraparte.orEmpty(), status = estado,
    note = nota.orEmpty(), createdAt = creadoEn, unitsOrdered = unidadesPedidas, unitsDone = unidadesHechas,
    lines = lineas.orEmpty().map {
        OrderLine(it.id, it.productoId, it.sku, it.nombre.orEmpty(), it.pedida, it.hecha, it.pendiente,
            it.sugerencias.orEmpty().map { s -> s.toDomain() })
    },
)

fun DashboardDto.toDomain() = Dashboard(
    products = productos, units = unidades, inventoryValue = valorInventario ?: 0.0, outOfStock = sinStock,
    lowStock = bajoMinimo, movementsToday = movimientosHoy, openCounts = conteosAbiertos,
    pendingReceipts = recepcionesPendientes, pendingPicks = pickingsPendientes,
    lowStockAlerts = alertasBajoMinimo.orEmpty().map { it.toDomain() },
    expiringSoon = proximosAVencer.orEmpty().map { it.toDomain() },
)
