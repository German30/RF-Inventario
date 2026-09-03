package com.rf.inventario.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rf.inventario.InventarioApplication
import com.rf.inventario.data.Product
import com.rf.inventario.data.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ProductsViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val products: StateFlow<List<Product>> =
        _query
            .flatMapLatest { q -> repository.products(q) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun save(product: Product) = viewModelScope.launch {
        repository.save(product)
    }

    fun delete(product: Product) = viewModelScope.launch {
        repository.delete(product)
    }

    /** Looks up an existing product by scanned code (SKU). */
    suspend fun findBySku(sku: String): Product? = repository.findBySku(sku)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as InventarioApplication
                ProductsViewModel(app.productRepository)
            }
        }
    }
}
