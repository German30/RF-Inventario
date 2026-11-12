package com.rf.inventario.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rf.inventario.InventarioApplication
import com.rf.inventario.R
import com.rf.inventario.data.Movement
import com.rf.inventario.ui.appViewModel
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.EmptyMessage
import com.rf.inventario.ui.common.ScanListener
import com.rf.inventario.ui.common.actionLabel
import com.rf.inventario.ui.products.MovementRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val app: InventarioApplication) : ViewModel() {
    private val _items = MutableStateFlow<List<Movement>>(emptyList())
    val items: StateFlow<List<Movement>> = _items.asStateFlow()
    private val _sku = MutableStateFlow("")
    val sku: StateFlow<String> = _sku.asStateFlow()
    private val _action = MutableStateFlow<String?>(null)
    val action: StateFlow<String?> = _action.asStateFlow()
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var page = 0
    private var job: Job? = null

    fun setSku(v: String) {
        _sku.value = v
    }

    fun setAction(v: String?) {
        _action.value = if (_action.value == v) null else v
        reload()
    }

    fun reload() {
        page = 0
        _items.value = emptyList()
        loadMore()
    }

    fun loadMore() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            runCatching { app.inventory.history(_sku.value.trim(), _action.value, page) }
                .onSuccess {
                    _items.value = _items.value + it.items
                    _hasMore.value = it.hasMore
                    page++
                    _error.value = null
                }
                .onFailure { _error.value = it.message }
        }
    }
}

private val filtros = listOf("ENTRADA", "SALIDA", "TRANSFERENCIA", "AJUSTE_STOCK", "CONTEO", "RECEPCION", "PICKING")

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = appViewModel { app, _ -> HistoryViewModel(app) },
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val sku by viewModel.sku.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reload() }
    ScanListener { viewModel.setSku(it); viewModel.reload() }

    AppScaffold(title = stringResource(R.string.module_history), onBack = onBack) { modifier ->
        Column(modifier) {
            OutlinedTextField(
                value = sku,
                onValueChange = viewModel::setSku,
                label = { Text(stringResource(R.string.history_filter_sku)) },
                singleLine = true,
                trailingIcon = { androidx.compose.material3.IconButton(onClick = viewModel::reload) { Icon(Icons.Default.Search, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filtros.forEach { f -> FilterChip(action == f, { viewModel.setAction(f) }, { Text(actionLabel(f)) }) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (items.isEmpty() && error == null) {
                EmptyMessage(stringResource(R.string.history_empty))
            } else {
                LazyColumn {
                    items(items, key = { it.id }) { MovementRow(it) }
                    if (hasMore) {
                        item {
                            LaunchedEffect(items.size) { viewModel.loadMore() }
                            TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}
