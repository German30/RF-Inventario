package com.rf.inventario.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base SQLite local:
 *  - products_cache / locations_cache: copia del servidor para trabajar offline.
 *  - pending_ops: operaciones hechas sin red, esperando a enviarse.
 *
 * La migración destructiva solo es aceptable porque las cachés se reconstruyen
 * con una sync; pending_ops nace en esta versión, así que no hay nada que perder.
 * A partir de la v2, cualquier cambio necesita una Migration real (pending_ops
 * tiene trabajo del operario que no está en el servidor).
 */
@Database(
    entities = [ProductEntity::class, LocationEntity::class, PendingOpEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class InventarioLocalDb : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun locationDao(): LocationDao
    abstract fun pendingOpDao(): PendingOpDao

    companion object {
        private const val DB_NAME = "rf_inventario_cache.db"

        @Volatile
        private var INSTANCE: InventarioLocalDb? = null

        fun getInstance(context: Context): InventarioLocalDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InventarioLocalDb::class.java,
                    DB_NAME,
                ).fallbackToDestructiveMigrationFrom(1).build().also { INSTANCE = it }
            }
    }
}
