package com.rf.inventario.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.rf.inventario.data.Location

/**
 * Ubicaciones cacheadas: permiten reconocer offline si lo escaneado es la
 * etiqueta de un estante (y no un producto).
 */
@Entity(tableName = "locations_cache")
data class LocationEntity(
    @PrimaryKey val code: String,
    val description: String,
    val zone: String,
    val active: Boolean,
)

fun LocationEntity.toDomain() = Location(code, description, zone, active)
fun Location.toEntity() = LocationEntity(code, description, zone, active)

@Dao
interface LocationDao {

    @Query("SELECT * FROM locations_cache ORDER BY code")
    suspend fun getAll(): List<LocationEntity>

    @Query("SELECT * FROM locations_cache WHERE code = :code COLLATE NOCASE LIMIT 1")
    suspend fun find(code: String): LocationEntity?

    @Upsert
    suspend fun upsertAll(locations: List<LocationEntity>)

    @Query("DELETE FROM locations_cache")
    suspend fun clear()
}
