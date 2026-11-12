package com.rf.inventario.ui.counts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.rf.inventario.data.AppUser
import com.rf.inventario.data.CountLine
import com.rf.inventario.data.CountSession
import com.rf.inventario.data.ops.SubmitResult
import com.rf.inventario.data.remote.dto.ConteoLineaBodyDto
import com.rf.inventario.scanner.ScanTarget
import com.rf.inventario.ui.appViewModel
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.CodeEntryField
import com.rf.inventario.ui.common.ConfirmDialog
import com.rf.inventario.ui.common.EmptyMessage
import com.rf.inventario.ui.common.QuantityStepper
import com.rf.inventario.ui.common.ScanListener
import com.rf.inventario.ui.common.relativeTime
import com.rf.inventario.ui.movement.Feedback
import com.rf.inventario.ui.movement.FeedbackCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Lista

class CountsViewModel(private val app: InventarioApplication) : ViewModel() {
    private val _counts = MutableStateFlow<List<CountSession>>(emptyList())
    val counts: StateFlow<List<CountSession>> = _counts.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _opened = MutableStateFlow<Long?>(null)
    val opened: StateFlow<Long?> = _opened.asStateFlow()

    fun load() = viewModelScope.launch {
        runCatching { app.inventory.counts() }
            .onSuccess { _counts.value = it; _error.value = null }
            .onFailure { _error.value = it.message }
    }

    fun open(location: String, blind: Boolean) = viewModelScope.launch {
        runCatching { app.inventory.openCount(location.trim().uppercase(), blind, null) }
            .onSuccess { _opened.value = it.id }
            .onFailure { _error.value = it.message }
    }

    fun consumeOpened() {
        _opened.value = null
    }
}

@Composable
fun CountsScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: CountsViewModel = appViewModel { app, _ -> CountsViewModel(app) },
) {
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val opened by viewModel.opened.collectAsStateWithLifecycle()
    var newDialog by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf("") }
    var blind by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(opened) { opened?.let { viewModel.consumeOpened(); onOpen(it) } }
    // Escanear la etiqueta de un estante en esta pantalla precarga el diálogo de nuevo conteo.
    ScanListener(enabled = newDialog) { location = it.uppercase() }

    AppScaffold(
        title = stringResource(R.string.module_count),
        onBack = onBack,
        actions = { IconButton(onClick = { viewModel.load() }) { Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh)) } },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { newDialog = true }, icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.count_new)) })
        },
    ) { modifier ->
        Column(modifier) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (counts.isEmpty()) {
                EmptyMessage(stringResource(R.string.count_empty))
            } else {
                LazyColumn {
                    items(counts, key = { it.id }) { c ->
                        ListItem(
                            modifier = Modifier.clickable { onOpen(c.id) },
                            headlineContent = {
                                Text(stringResource(R.string.count_title, c.id, c.location.ifBlank { stringResource(R.string.count_free) }))
                            },
                            supportingContent = {
                                Text(stringResource(R.string.count_summary, statusLabel(c.status), c.countedLines, c.totalLines,
                                    c.createdBy, relativeTime(c.createdAt)))
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (newDialog) {
        AlertDialog(
            onDismissRequest = { newDialog = false },
            title = { Text(stringResource(R.string.count_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.count_new_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(location, { location = it.uppercase() }, label = { Text(stringResource(R.string.field_location_optional)) },
                        singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.count_blind), Modifier.weight(1f))
                        Switch(blind, { blind = it })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { newDialog = false; viewModel.open(location, blind) }) { Text(stringResource(R.string.action_start)) } },
            dismissButton = { TextButton(onClick = { newDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun statusLabel(status: String): String = stringResource(
    when (status) {
        "ABIERTO" -> R.string.status_open
        "CERRADO" -> R.string.status_closed
        "APLICADO" -> R.string.status_applied
        "CANCELADO" -> R.string.status_cancelled
        "PENDIENTE" -> R.string.status_pending
        "EN_PROCESO" -> R.string.status_in_progress
        "COMPLETADO" -> R.string.status_completed
        else -> R.string.status_unknown
    },
)

// ------------------------------------------------------------------ Detalle

class CountDetailViewModel(private val app: InventarioApplication, handle: SavedStateHandle) : ViewModel() {
    val id: Long = handle["id"] ?: 0L

    private val _count = MutableStateFlow<CountSession?>(null)
    val count: StateFlow<CountSession?> = _count.asStateFlow()
    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()
    private val _ask = MutableStateFlow<String?>(null)
    /** Código pendiente de que el operario indique cantidad (modo "preguntar cantidad"). */
    val ask: StateFlow<String?> = _ask.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val scanAddsOne: Boolean get() = app.settings.current.scanAddsOne

    fun load() = viewModelScope.launch {
        _loading.value = true
        runCatching { app.inventory.count(id) }
            .onSuccess { _count.value = it }
            .onFailure { _feedback.value = Feedback(it.message ?: "", Feedback.Kind.ERROR) }
        _loading.value = false
    }

    fun onScan(code: String, texts: Texts) = viewModelScope.launch {
        val c = _count.value ?: return@launch
        if (!c.isOpen) return@launch
        if (app.scanRouter.route(code) is ScanTarget.IsLocation) {
            app.scanFeedback.error()
            _feedback.value = Feedback(texts.locationInCount, Feedback.Kind.ERROR)
            return@launch
        }
        if (scanAddsOne) register(code, 1, sum = true, texts) else _ask.value = code
    }

    fun answer(code: String, qty: Int, texts: Texts) {
        _ask.value = null
        register(code, qty, sum = true, texts)
    }

    fun dismissAsk() {
        _ask.value = null
    }

    /** Corrige una línea ya contada (reemplaza, no suma). */
    fun correct(line: CountLine, qty: Int, texts: Texts) = register(line.sku, qty, sum = false, texts, line.lot, line.location)

    private fun register(code: String, qty: Int, sum: Boolean, texts: Texts, lot: String? = null, location: String? = null) =
        viewModelScope.launch {
            val body = ConteoLineaBodyDto(codigo = code, cantidad = qty, lote = lot?.ifBlank { null },
                ubicacion = location?.takeIf { _count.value?.location.isNullOrBlank() }, sumar = sum)
            when (val r = app.operations.countLine(id, body, "CONTEO #$id · $code · ${if (sum) "+" else "="}$qty")) {
                is SubmitResult.Done -> {
                    app.scanFeedback.success()
                    _feedback.value = Feedback(texts.counted.format(code, qty), Feedback.Kind.OK)
                    load()
                }
                SubmitResult.Queued -> {
                    app.scanFeedback.queued()
                    _feedback.value = Feedback(texts.queued.format(code, qty), Feedback.Kind.QUEUED)
                }
                is SubmitResult.Rejected -> {
                    app.scanFeedback.error()
                    _feedback.value = Feedback(r.message, Feedback.Kind.ERROR)
                }
            }
        }

    /** cerrar | reabrir | aplicar | cancelar */
    fun action(action: String) = viewModelScope.launch {
        runCatching { app.inventory.countAction(id, action) }
            .onSuccess {
                _count.value = it
                if (action == "aplicar") app.syncRepository.sync()
            }
            .onFailure { _feedback.value = Feedback(it.message ?: "", Feedback.Kind.ERROR) }
    }

    data class Texts(val counted: String, val queued: String, val locationInCount: String)
}

@Composable
fun CountDetailScreen(
    user: AppUser,
    onBack: () -> Unit,
    viewModel: CountDetailViewModel = appViewModel { app, handle -> CountDetailViewModel(app, handle) },
) {
    val count by viewModel.count.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val ask by viewModel.ask.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CountLine?>(null) }
    val texts = CountDetailViewModel.Texts(
        counted = stringResource(R.string.count_registered),
        queued = stringResource(R.string.count_queued),
        locationInCount = stringResource(R.string.count_location_scanned),
    )

    LaunchedEffect(Unit) { viewModel.load() }
    ScanListener(enabled = count?.isOpen == true && ask == null) { viewModel.onScan(it, texts) }

    val c = count
    AppScaffold(
        title = stringResource(R.string.count_title, viewModel.id, c?.location?.ifBlank { stringResource(R.string.count_free) } ?: ""),
        subtitle = c?.let { stringResource(R.string.count_progress, statusLabel(it.status), it.countedLines, it.totalLines) },
        onBack = onBack,
        snackbarHostState = remember { SnackbarHostState() },
        actions = { IconButton(onClick = { viewModel.load() }) { Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh)) } },
    ) { modifier ->
        if (c == null) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            feedback?.let { Column(Modifier.padding(16.dp)) { FeedbackCard(it) } }
            return@AppScaffold
        }
        LazyColumn(modifier) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    feedback?.let { FeedbackCard(it) }
                    if (c.isOpen) {
                        Text(stringResource(if (c.blind) R.string.count_hint_blind else R.string.count_hint), style = MaterialTheme.typography.bodySmall)
                        CodeEntryField(stringResource(R.string.field_scan_product), onSubmit = { viewModel.onScan(it, texts) })
                    }
                    if (c.unitsDifference != null && (c.isClosed || user.isSupervisor)) {
                        Text(
                            stringResource(R.string.count_differences, c.linesWithDifference ?: 0, c.unitsDifference),
                            style = MaterialTheme.typography.titleSmall,
                            color = if ((c.linesWithDifference ?: 0) > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (c.isOpen) {
                            Button(onClick = { confirm = "cerrar" }) { Text(stringResource(R.string.count_close)) }
                        }
                        if (user.isSupervisor && (c.isOpen || c.isClosed)) {
                            Button(onClick = { confirm = "aplicar" }) { Text(stringResource(R.string.count_apply)) }
                        }
                        if (user.isSupervisor && c.isClosed) {
                            OutlinedButton(onClick = { viewModel.action("reabrir") }) { Text(stringResource(R.string.count_reopen)) }
                        }
                    }
                }
            }
            items(c.lines, key = { it.id }) { l ->
                ListItem(
                    modifier = Modifier.clickable(enabled = c.isOpen) { editing = l },
                    headlineContent = { Text(l.name.ifBlank { l.sku }) },
                    supportingContent = {
                        Text(listOfNotNull(l.sku, l.location, l.lot.takeIf { it.isNotBlank() }?.let { "L:$it" },
                            l.expected?.let { stringResource(R.string.count_expected, it) }).joinToString(" · "))
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(l.counted?.toString() ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            l.difference?.takeIf { it != 0 }?.let {
                                Text(if (it > 0) "+$it" else "$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    confirm?.let { action ->
        ConfirmDialog(
            title = stringResource(if (action == "aplicar") R.string.count_apply else R.string.count_close),
            text = stringResource(if (action == "aplicar") R.string.count_apply_confirm else R.string.count_close_confirm),
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = { viewModel.action(action) },
            onDismiss = { confirm = null },
        )
    }

    ask?.let { code -> QuantityDialog(title = code, initial = 1, onDismiss = viewModel::dismissAsk) { viewModel.answer(code, it, texts) } }
    editing?.let { line ->
        QuantityDialog(title = line.name.ifBlank { line.sku }, initial = line.counted ?: 0, min = 0, onDismiss = { editing = null }) {
            editing = null
            viewModel.correct(line, it, texts)
        }
    }
}

@Composable
fun QuantityDialog(title: String, initial: Int, min: Int = 1, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var qty by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { QuantityStepper(value = qty, onValueChange = { qty = it }, min = min) },
        confirmButton = { TextButton(onClick = { onConfirm(qty) }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
