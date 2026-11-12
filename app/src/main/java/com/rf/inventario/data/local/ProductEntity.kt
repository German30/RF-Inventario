package com.rf.inventario.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rf.inventario.data.Product

/**
 * Espejo local de un [Product] tal como lo confirmó el servidor (más los
 * ajustes optimistas de operaciones encoladas offline, que la próxima sync
 * reemplaza por el valor real).
 *
 * [altCodes] se guarda como `|cod1|cod2|` para poder buscar un código exacto
 * con LIKE '%|cod|%' sin una tabla aparte.
 */
@Entity(tableName = "products_cache", indices = [Index("sku")])
data class ProductEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val sku: String,
    val quantity: Int,
    val location: String,
    val updatedAt: Long,
    val description: String = "",
    val category: String = "",
    val unit: String = "UN",
    val minStock: Int = 0,
    val unitCost: Double = 0.0,
    val tracksLots: Boolean = false,
    val altCodes: String = "",
)

fun ProductEntity.toDomain(): Product = Product(
    id = id,
    name = name,
    sku = sku,
    quantity = quantity,
    location = location,
    updatedAt = updatedAt,
    description = description,
    category = category,
    unit = unit,
    minStock = minStock,
    unitCost = unitCost,
    tracksLots = tracksLots,
    altCodes = altCodes.split('|').filter(String::isNotBlank),
)

fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id,
    name = name,
    sku = sku,
    quantity = quantity,
    location = location,
    updatedAt = updatedAt,
    description = description,
    category = category,
    unit = unit,
    minStock = minStock,
    unitCost = unitCost,
    tracksLots = tracksLots,
    altCodes = if (altCodes.isEmpty()) "" else altCodes.joinToString("|", prefix = "|", postfix = "|"),
)
