package com.rf.inventario

import android.app.Application
import com.rf.inventario.data.InventarioDatabase
import com.rf.inventario.data.ProductRepository
import com.rf.inventario.scanner.DataWedgeScanner
import com.rf.inventario.scanner.KeyboardWedgeScanner

/**
 * Application class acting as a very small manual DI container.
 * For a larger app consider Hilt or Koin instead.
 */
class InventarioApplication : Application() {

    private val database: InventarioDatabase by lazy { InventarioDatabase.getInstance(this) }

    val productRepository: ProductRepository by lazy { ProductRepository(database.productDao()) }

    /** Fed every key event from [MainActivity]; emits scans from wedge-mode readers. */
    val keyboardWedgeScanner: KeyboardWedgeScanner = KeyboardWedgeScanner()

    /** Receives Zebra DataWedge broadcasts; inert on other devices. */
    val dataWedgeScanner: DataWedgeScanner by lazy { DataWedgeScanner(this) }

    override fun onCreate() {
        super.onCreate()
        dataWedgeScanner.start()
    }
}
