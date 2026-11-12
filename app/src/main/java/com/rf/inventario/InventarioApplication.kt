package com.rf.inventario

import android.app.Application
import android.util.Log
import com.rf.inventario.auth.AuthRepository
import com.rf.inventario.auth.LogoutReason
import com.rf.inventario.auth.PrefsSessionStore
import com.rf.inventario.auth.SessionManager
import com.rf.inventario.config.AppSettings
import com.rf.inventario.config.ManagedConfig
import com.rf.inventario.data.ProductRepository
import com.rf.inventario.data.local.InventarioLocalDb
import com.rf.inventario.data.local.LocalProductCache
import com.rf.inventario.data.local.RoomPendingOpStore
import com.rf.inventario.data.local.toDomain
import com.rf.inventario.data.ops.OperationsRepository
import com.rf.inventario.data.remote.ApiClient
import com.rf.inventario.data.remote.InventoryRepository
import com.rf.inventario.data.remote.PanelApi
import com.rf.inventario.data.remote.RemoteOperationSender
import com.rf.inventario.data.remote.RemoteProductStore
import com.rf.inventario.data.remote.RetrofitAuthApi
import com.rf.inventario.data.remote.ServerCheck
import com.rf.inventario.data.remote.ServerGate
import com.rf.inventario.data.remote.ServerStatus
import com.rf.inventario.data.sync.SyncRepository
import com.rf.inventario.data.sync.SyncWorker
import com.rf.inventario.diagnostics.CrashReporter
import com.rf.inventario.scanner.DataWedgeScanner
import com.rf.inventario.scanner.KeyboardWedgeScanner
import com.rf.inventario.scanner.ScanFeedback
import com.rf.inventario.scanner.ScanResult
import com.rf.inventario.scanner.ScanRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Contenedor de dependencias manual (para el tamaño de la app, Hilt no paga
 * su complejidad). Todo es lazy: nada se construye hasta que se usa.
 *
 * Los datos vienen del backend Spring (rf-inventario-panel-api); localmente
 * hay una copia del catálogo (Room) para trabajar offline y una cola de
 * operaciones que se envía sola cuando vuelve la red.
 */
class InventarioApplication : Application() {

    /** Scope de la app (sobrevive a las pantallas): sync, subida de crashes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionManager: SessionManager by lazy { SessionManager(PrefsSessionStore(this)) }

    /** URL del backend, timeout de inactividad e impresora: los puede fijar el MDM. */
    val managedConfig: ManagedConfig by lazy { ManagedConfig(this) }

    val settings: AppSettings by lazy { AppSettings(this, managedConfig) }

    val panelApi: PanelApi by lazy {
        ApiClient.create(
            baseUrl = managedConfig.apiBaseUrl,
            token = { sessionManager.token },
            onUnauthorized = { sessionManager.logout(LogoutReason.EXPIRED) },
        )
    }

    /** Verificación de arranque: nada se ejecuta contra el backend hasta que pase. */
    val serverGate: ServerGate by lazy {
        ServerGate(
            check = ServerCheck(ping = { panelApi.ping() }),
            scope = appScope,
            onFailure = { Log.e(TAG, "Servidor no disponible en ${managedConfig.apiBaseUrl}", it) },
        )
    }

    private val localDb: InventarioLocalDb by lazy { InventarioLocalDb.getInstance(this) }

    val productCache: LocalProductCache by lazy { LocalProductCache(localDb.productDao(), this) }

    val productRepository: ProductRepository by lazy {
        ProductRepository(remote = RemoteProductStore(panelApi), cache = productCache)
    }

    val operations: OperationsRepository by lazy {
        OperationsRepository(
            RemoteOperationSender(panelApi),
            RoomPendingOpStore(localDb.pendingOpDao()),
            productCache,
            // WorkManager lo corre apenas haya red: la cola no espera al ciclo de 15 min.
            onQueued = { SyncWorker.runSoon(this) },
        )
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(panelApi, productCache, localDb.locationDao(), operations, this)
    }

    val inventory: InventoryRepository by lazy { InventoryRepository(panelApi) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(RetrofitAuthApi(panelApi), sessionManager)
    }

    /** Distingue offline si lo escaneado es una ubicación o un producto. */
    val scanRouter: ScanRouter by lazy {
        ScanRouter(
            findLocation = { localDb.locationDao().find(it)?.toDomain() },
            findProduct = { productCache.findByCode(it) },
        )
    }

    val scanFeedback: ScanFeedback by lazy { ScanFeedback(this, settings) }

    private val crashReporter by lazy { CrashReporter(this) }

    /** Fed every key event from [MainActivity]; emits scans from wedge-mode readers. */
    val keyboardWedgeScanner: KeyboardWedgeScanner = KeyboardWedgeScanner()

    /** Receives Zebra DataWedge broadcasts; inert on other devices. */
    val dataWedgeScanner: DataWedgeScanner by lazy { DataWedgeScanner(this) }

    /** Todas las lecturas del lector físico, vengan por teclado o por DataWedge. */
    val hardwareScans: Flow<ScanResult> by lazy { merge(keyboardWedgeScanner.scans, dataWedgeScanner.scans) }

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        dataWedgeScanner.start()
        SyncWorker.schedulePeriodic(this)

        // Al arrancar se verifica que el backend esté arriba y sea compatible.
        serverGate.verify()

        // Con el servidor verificado y una sesión abierta (login o sesión guardada):
        // sync inmediata y subida de cualquier crash pendiente.
        appScope.launch {
            combine(sessionManager.current, serverGate.status) { user, server ->
                user != null && server == ServerStatus.Ready
            }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    SyncWorker.runSoon(this@InventarioApplication)
                    crashReporter.uploadPending(panelApi)
                }
        }
    }

    private companion object {
        const val TAG = "InventarioApp"
    }
}
