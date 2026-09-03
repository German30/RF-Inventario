package com.rf.inventario.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sku: String,
    val quantity: Int,
    val location: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
