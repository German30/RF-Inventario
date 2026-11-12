package com.rf.inventario.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rf.inventario.InventarioApplication
import com.rf.inventario.R
import com.rf.inventario.data.Order
import com.rf.inventario.data.OrderType
import com.rf.inventario.data.ops.SubmitResult
import com.rf.inventario.data.remote.dto.PedidoEscaneoDto
import com.rf.inventario.scanner.ScanTarget
import com.rf.inventario.ui.appViewModel
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.CodeEntryField
import com.rf.inventario.ui.common.ConfirmDialog
import com.rf.inventario.ui.common.EmptyMessage
import com.rf.inventario.ui.common.ScanListener
import com.rf.inventario.ui.common.relativeTime
import com.rf.inventario.ui.counts.QuantityDialog
import com.rf.inventario.ui.counts.statusLabel
import com.rf.inventario.ui.movement.Feedback
import com.rf.inventario.ui.movement.FeedbackCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Lista

class OrdersViewModel(private val app: InventarioApplication, handle: SavedStateHandle) : ViewModel() {
    val type: OrderType = OrderType.valueOf(handle["type"] ?: "COMPRA")
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load() = viewModelScope.launch {
        runCatching { app.inventory.orders(type) }
            .onSuccess { _orders.value = it; _error.value = null }
            .onFailure { _error.value = it.message }
    }
}

@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: OrdersViewModel = appViewModel { app, handle -> OrdersViewModel(app, handle) },
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    // Escanear el código del remito/pedido lo abre directo.
    ScanListener { code -> orders.firstOrNull { it.number.equals(code, true) }?.let { onOpen(it.id) } }

    AppScaffold(
        title = stringResource(if (viewModel.type == OrderType.COMPRA) R.string.module_receive else R.string.module_pick),
        onBack = onBack,
        actions = { IconButton(onClick = { viewModel.load() }) { Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh)) } },
    ) { modifier ->
        Column(modifier) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (orders.isEmpty()) {
                EmptyMessage(stringResource(R.string.orders_empty))
            } else {
                LazyColumn {
                    items(orders, key = { it.id }) { o ->
                        ListItem(
                            modifier = Modifier.clickable { onOpen(o.id) },
                            headlineContent = { Text("${o.number} · ${o.party}") },
                            supportingContent = {
                                Column {
                                    Text(stringResource(R.string.order_summary, statusLabel(o.status), o.unitsDone, o.unitsOrdered, relativeTime(o.createdAt)))
                                    LinearProgressIndicator(progress = { o.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Detalle

class OrderDetailViewModel(private val app: InventarioApplication, handle: SavedStateHandle) : ViewModel() {
    val id: Long = handle["id"] ?: 0L
    private val _order = MutableStateFlow<Order?>(null)
    val order: StateFlow<Order?> = _order.asStateFlow()
    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()
    private val _location = MutableStateFlow("")
    /** Recepción: dónde se guarda. Picking: de dónde se saca (vacío = FEFO). */
    val location: StateFlow<String> = _location.asStateFlow()
    private val _ask = MutableStateFlow<String?>(null)
    val ask: StateFlow<String?> = _ask.asStateFlow()

    fun load() = viewModelScope.launch {
        runCatching { app.inventory.order(id) }
            .onSuccess { _order.value = it }
            .onFailure { _feedback.value = Feedback(it.message ?: "", Feedback.Kind.ERROR) }
    }

    fun setLocation(v: String) {
        _location.value = v.uppercase()
    }

    fun onScan(code: String, texts: Texts) = viewModelScope.launch {
        val o = _order.value ?: return@launch
        if (!o.isOpen) return@launch
        when (val t = app.scanRouter.route(code)) {
            is ScanTarget.IsLocation -> {
                app.scanFeedback.success()
                _location.value = t.location.code
            }
            is ScanTarget.IsProduct, is ScanTarget.Unknown -> {
                val line = o.lines.firstOrNull { l ->
                    l.sku.equals(code, true) || (t is ScanTarget.IsProduct && t.product.id == l.productId)
                }
                if (line == null) {
                    app.scanFeedback.error()
                    _feedback.value = Feedback(texts.notInOrder.format(code), Feedback.Kind.ERROR)
                } else if (app.settings.current.scanAddsOne) {
                    send(code, 1, line.productId, texts)
                } else {
                    _ask.value = code
                }
            }
        }
    }

    fun answer(code: String, qty: Int, texts: Texts) {
        _ask.value = null
        val line = _order.value?.lines?.firstOrNull { it.sku.equals(code, true) }
        send(code, qty, line?.productId, texts)
    }

    fun dismissAsk() {
        _ask.value = null
    }

    private fun send(code: String, qty: Int, productId: Long?, texts: Texts) = viewModelScope.launch {
        val o = _order.value ?: return@launch
        val delta = if (o.type == OrderType.COMPRA) qty else -qty
        val body = PedidoEscaneoDto(codigo = code, cantidad = qty, ubicacion = _location.value.ifBlank { null })
        when (val r = app.operations.orderScan(id, body, "${o.number} · $code · ${if (delta > 0) "+" else ""}$delta", productId, delta)) {
            is SubmitResult.Done -> {
                app.scanFeedback.success()
                _feedback.value = Feedback(texts.done.format(code, qty), Feedback.Kind.OK)
                load()
            }
            SubmitResult.Queued -> {
                app.scanFeedback.queued()
                _feedback.value = Feedback(texts.queued, Feedback.Kind.QUEUED)
            }
            is SubmitResult.Rejected -> {
                app.scanFeedback.error()
                _feedback.value = Feedback(r.message, Feedback.Kind.ERROR)
            }
        }
    }

    fun complete() = viewModelScope.launch {
        runCatching { app.inventory.completeOrder(id) }
            .onSuccess { _order.value = it; app.syncRepository.sync() }
            .onFailure { _feedback.value = Feedback(it.message ?: "", Feedback.Kind.ERROR) }
    }

    data class Texts(val done: String, val queued: String, val notInOrder: String)
}

@Composable
fun OrderDetailScreen(
    onBack: () -> Unit,
    viewModel: OrderDetailViewModel = appViewModel { app, handle -> OrderDetailViewModel(app, handle) },
) {
    val order by viewModel.order.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val ask by viewModel.ask.collectAsStateWithLifecycle()
    var confirmComplete by remember { mutableStateOf(false) }
    val texts = OrderDetailViewModel.Texts(
        done = stringResource(R.string.order_scanned),
        queued = stringResource(R.string.movement_queued),
        notInOrder = stringResource(R.string.order_not_in_order),
    )

    LaunchedEffect(Unit) { viewModel.load() }
    ScanListener(enabled = order?.isOpen == true && ask == null) { viewModel.onScan(it, texts) }

    val o = order
    AppScaffold(
        title = o?.let { "${it.number} · ${it.party}" } ?: "",
        subtitle = o?.let { stringResource(R.string.order_summary, statusLabel(it.status), it.unitsDone, it.unitsOrdered, relativeTime(it.createdAt)) },
        onBack = onBack,
        actions = { IconButton(onClick = { viewModel.load() }) { Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh)) } },
    ) { modifier ->
        if (o == null) {
            feedback?.let { Column(Modifier.padding(16.dp)) { FeedbackCard(it) } }
            return@AppScaffold
        }
        LazyColumn(modifier) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { o.progress }, modifier = Modifier.fillMaxWidth())
                    feedback?.let { FeedbackCard(it) }
                    if (o.isOpen) {
                        OutlinedTextField(
                            value = location,
                            onValueChange = viewModel::setLocation,
                            label = {
                                Text(stringResource(if (o.type == OrderType.COMPRA) R.string.order_store_in else R.string.order_take_from))
                            },
                            leadingIcon = { Icon(Icons.Default.Place, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CodeEntryField(stringResource(R.string.field_scan_product), onSubmit = { viewModel.onScan(it, texts) })
                        Button(onClick = { confirmComplete = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.order_complete))
                        }
                    }
                }
            }
            items(o.lines, key = { it.id }) { l ->
                ListItem(
                    headlineContent = { Text(l.name.ifBlank { l.sku }) },
                    supportingContent = {
                        Column {
                            Text(l.sku)
                            if (l.suggestions.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.order_pick_from) + " " + l.suggestions.joinToString(", ") { s ->
                                        "${s.location}${if (s.lot.isNotBlank()) " [${s.lot}]" else ""} (${s.quantity})"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            if (l.pending == 0) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Text("${l.done} / ${l.ordered}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (confirmComplete && o != null) {
        val pending = o.lines.sumOf { it.pending }
        ConfirmDialog(
            title = stringResource(R.string.order_complete),
            text = if (pending > 0) stringResource(R.string.order_complete_partial, pending) else stringResource(R.string.order_complete_full),
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = viewModel::complete,
            onDismiss = { confirmComplete = false },
        )
    }
    ask?.let { code -> QuantityDialog(title = code, initial = 1, onDismiss = viewModel::dismissAsk) { viewModel.answer(code, it, texts) } }
}
