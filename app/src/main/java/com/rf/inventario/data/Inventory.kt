package com.rf.inventario.data

/** Modelos de las operaciones de depósito (más allá de la ficha de producto). */

data class Location(
    val code: String,
    val description: String = "",
    val zone: String = "",
    val active: Boolean = true,
)

/** Stock de un producto en una ubicación y lote. */
data class StockLevel(
    val productId: Long,
    val sku: String,
    val name: String,
    val location: String,
    val lot: String,
    /** ISO yyyy-MM-dd, o null. */
    val expiresOn: String?,
    val quantity: Int,
)

enum class MovementType { ENTRADA, SALIDA, TRANSFERENCIA, AJUSTE }

data class Movement(
    val id: Long,
    val productId: Long?,
    val sku: String,
    val action: String,
    val delta: Int,
    val quantityAfter: Int,
    val createdAt: Long,
    val operatorCode: String,
    val operatorName: String,
    val reason: String = "",
    val reference: String = "",
    val location: String = "",
    val toLocation: String = "",
    val lot: String = "",
)

data class Page<T>(val items: List<T>, val page: Int, val totalPages: Int, val total: Long) {
    val hasMore: Boolean get() = page + 1 < totalPages
}

data class CountSession(
    val id: Long,
    val location: String,
    val blind: Boolean,
    val status: String,
    val note: String,
    val createdBy: String,
    val createdAt: Long,
    val totalLines: Int,
    val countedLines: Int,
    val linesWithDifference: Int?,
    val unitsDifference: Int?,
    val lines: List<CountLine> = emptyList(),
) {
    val isOpen: Boolean get() = status == "ABIERTO"
    val isClosed: Boolean get() = status == "CERRADO"
}

data class CountLine(
    val id: Long,
    val productId: Long,
    val sku: String,
    val name: String,
    val location: String,
    val lot: String,
    /** null = oculto (conteo ciego). */
    val expected: Int?,
    /** null = no contado todavía. */
    val counted: Int?,
    val difference: Int?,
)

enum class OrderType { COMPRA, VENTA }

data class Order(
    val id: Long,
    val type: OrderType,
    val number: String,
    val party: String,
    val status: String,
    val note: String,
    val createdAt: Long,
    val unitsOrdered: Int,
    val unitsDone: Int,
    val lines: List<OrderLine> = emptyList(),
) {
    val isOpen: Boolean get() = status == "PENDIENTE" || status == "EN_PROCESO"
    val progress: Float get() = if (unitsOrdered == 0) 0f else unitsDone.toFloat() / unitsOrdered
}

data class OrderLine(
    val id: Long,
    val productId: Long,
    val sku: String,
    val name: String,
    val ordered: Int,
    val done: Int,
    val pending: Int,
    /** Solo picking: dónde sacar (FEFO). */
    val suggestions: List<StockLevel> = emptyList(),
)

data class Dashboard(
    val products: Long,
    val units: Long,
    val inventoryValue: Double,
    val outOfStock: Long,
    val lowStock: Long,
    val movementsToday: Long,
    val openCounts: Long,
    val pendingReceipts: Long,
    val pendingPicks: Long,
    val lowStockAlerts: List<Product>,
    val expiringSoon: List<StockLevel>,
)
