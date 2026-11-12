package com.rf.inventario.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rf.inventario.InventarioApplication
import com.rf.inventario.data.Product
import com.rf.inventario.scanner.ScanTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StockFilter { ALL, LOW, OUT }

/**
 * Offline-first: la lista sale SIEMPRE de Room (instantánea, con o sin red) y
 * la sync la mantiene al día por detrás. Buscar no pega al servidor.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ProductsViewModel(private val app: InventarioApplication) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(StockFilter.ALL)
    val filter: StateFlow<StockFilter> = _filter.asStateFlow()

    val products: StateFlow<List<Product>> = combine(_query.debounce(150), _filter) { q, f -> q to f }
        .flatMapLatest { (q, f) -> app.productCache.observe(q, f.name) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Int> = app.productCache.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sync = app.syncRepository.state

    private val _quick = MutableStateFlow<Product?>(null)
    /** Producto recién escaneado: abre la hoja de acción rápida. */
    val quick: StateFlow<Product?> = _quick.asStateFlow()

    private val _notFound = MutableStateFlow<String?>(null)
    val notFound: StateFlow<String?> = _notFound.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onFilterChange(value: StockFilter) {
        _filter.value = value
    }

    fun refresh() = viewModelScope.launch { app.syncRepository.sync() }

    fun onScan(code: String) = viewModelScope.launch {
        when (val t = app.scanRouter.route(code)) {
            is ScanTarget.IsProduct -> {
                app.scanFeedback.success()
                _quick.value = t.product
            }
            is ScanTarget.IsLocation -> {
                app.scanFeedback.success()
                _query.value = ""
                _notFound.value = null
                _quick.value = null
                _locationScanned.value = t.location.code
            }
            is ScanTarget.Unknown -> {
                val remote = runCatching { app.productRepository.findBySku(t.raw) }.getOrNull()
                if (remote != null) {
                    app.scanFeedback.success()
                    _quick.value = remote
                } else {
                    app.scanFeedback.error()
                    _notFound.value = t.raw
                }
            }
        }
    }

    private val _locationScanned = MutableStateFlow<String?>(null)
    val locationScanned: StateFlow<String?> = _locationScanned.asStateFlow()

    fun consumeLocation() {
        _locationScanned.value = null
    }

    fun dismissQuick() {
        _quick.value = null
    }

    fun dismissNotFound() {
        _notFound.value = null
    }

    fun allCached(onReady: (List<Product>) -> Unit) = viewModelScope.launch {
        onReady(app.productCache.getAll())
    }
}
