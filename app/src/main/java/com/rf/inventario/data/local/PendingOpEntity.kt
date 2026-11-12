package com.rf.inventario.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.rf.inventario.data.ops.PendingOp
import com.rf.inventario.data.ops.PendingOpStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Operación hecha sin conexión, esperando a que vuelva la red. */
@Entity(tableName = "pending_ops")
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientOpId: String,
    val kind: String,
    val targetId: Long?,
    val payload: String,
    val summary: String,
    val createdAt: Long,
    val status: String,
    val attempts: Int,
    val lastError: String?,
)

private fun PendingOpEntity.toDomain() = PendingOp(
    id, clientOpId, PendingOp.Kind.valueOf(kind), targetId, payload, summary, createdAt,
    PendingOp.Status.valueOf(status), attempts, lastError,
)

private fun PendingOp.toEntity() = PendingOpEntity(
    id, clientOpId, kind.name, targetId, payload, summary, createdAt, status.name, attempts, lastError,
)

@Dao
interface PendingOpDao {

    @Insert
    suspend fun insert(op: PendingOpEntity): Long

    @Query("SELECT * FROM pending_ops WHERE status = 'PENDING' ORDER BY id")
    suspend fun pending(): List<PendingOpEntity>

    @Query("SELECT * FROM pending_ops ORDER BY id")
    fun observeAll(): Flow<List<PendingOpEntity>>

    @Query("SELECT COUNT(*) FROM pending_ops WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_ops WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("UPDATE pending_ops SET status = :status, attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun mark(id: Long, status: String, error: String?)

    @Query("UPDATE pending_ops SET status = 'PENDING', lastError = NULL WHERE id = :id")
    suspend fun retry(id: Long)

    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun delete(id: Long)
}

/** [PendingOpStore] sobre Room. */
class RoomPendingOpStore(private val dao: PendingOpDao) : PendingOpStore {
    override suspend fun add(op: PendingOp): Long = dao.insert(op.toEntity())
    override suspend fun pending(): List<PendingOp> = dao.pending().map { it.toDomain() }
    override suspend fun markSent(id: Long) = dao.delete(id)
    override suspend fun markFailed(id: Long, error: String) = dao.mark(id, PendingOp.Status.FAILED.name, error)
    override suspend fun markRetryLater(id: Long, error: String) = dao.mark(id, PendingOp.Status.PENDING.name, error)
    override suspend fun retry(id: Long) = dao.retry(id)
    override suspend fun discard(id: Long) = dao.delete(id)
    override fun observeAll(): Flow<List<PendingOp>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()
    override fun observeFailedCount(): Flow<Int> = dao.observeFailedCount()
}
