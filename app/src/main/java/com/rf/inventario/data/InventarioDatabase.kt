package com.rf.inventario.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Product::class], version = 1, exportSchema = false)
abstract class InventarioDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    companion object {
        private const val DB_NAME = "rf_inventario.db"

        @Volatile
        private var INSTANCE: InventarioDatabase? = null

        fun getInstance(context: Context): InventarioDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InventarioDatabase::class.java,
                    DB_NAME,
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
