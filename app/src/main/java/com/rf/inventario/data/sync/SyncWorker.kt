package com.rf.inventario.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rf.inventario.InventarioApplication
import com.rf.inventario.data.RejectedException
import java.util.concurrent.TimeUnit

/**
 * Corre en segundo plano con red disponible: envía la cola offline y baja los
 * cambios del catálogo. Periódico (cada 15 min, el mínimo de WorkManager) y
 * también a demanda cuando vuelve la conectividad o al abrir la app.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as InventarioApplication
        if (app.sessionManager.token == null) return Result.success() // sin sesión no hay a quién sincronizar
        return app.syncRepository.sync().fold(
            onSuccess = { Result.success() },
            onFailure = { if (it is RejectedException) Result.failure() else Result.retry() },
        )
    }

    companion object {
        private const val PERIODIC = "rf-sync-periodic"
        private const val NOW = "rf-sync-now"

        private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(conRed).build(),
            )
        }

        /**
         * Se ejecuta en cuanto haya red (si ya hay, enseguida). KEEP: si ya hay
         * uno esperando la red, no hace falta otro (el worker envía TODA la cola).
         */
        fun runSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(conRed).build(),
            )
        }
    }
}
