package com.rf.inventario.data

/**
 * Producto del inventario. `id == 0L` significa "aún no persistido".
 *
 * [quantity] es el total en todas las ubicaciones; [location] es la ubicación
 * principal (donde caen los movimientos que no indican otra).
 */
data class Product(
    val id: Long = 0,
    val name: String,
    val sku: String,
    val quantity: Int,
    val location: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val description: String = "",
    val category: String = "",
    val unit: String = "UN",
    val minStock: Int = 0,
    val unitCost: Double = 0.0,
    val tracksLots: Boolean = false,
    val altCodes: List<String> = emptyList(),
) {
    /** Tiene mínimo configurado y el stock está en o por debajo. */
    val lowStock: Boolean get() = minStock > 0 && quantity <= minStock

    val outOfStock: Boolean get() = quantity <= 0

    /** ¿Este código escaneado identifica a este producto? */
    fun matchesCode(code: String): Boolean =
        sku.equals(code.trim(), ignoreCase = true) || altCodes.any { it.equals(code.trim(), ignoreCase = true) }
}
