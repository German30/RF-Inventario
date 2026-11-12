package com.rf.inventario.ui.products

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rf.inventario.InventarioApplication
import com.rf.inventario.R
import com.rf.inventario.data.AppUser
import com.rf.inventario.data.Movement
import com.rf.inventario.data.MovementType
import com.rf.inventario.data.Product
import com.rf.inventario.data.StockLevel
import com.rf.inventario.labels.LabelPrinter
import com.rf.inventario.labels.Zpl
import com.rf.inventario.ui.Routes
import com.rf.inventario.ui.appViewModel
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.ConfirmDialog
import com.rf.inventario.ui.common.ProductCard
import com.rf.inventario.ui.common.SectionTitle
import com.rf.inventario.ui.common.actionLabel
import com.rf.inventario.ui.common.relativeTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductDetailViewModel(private val app: InventarioApplication, handle: SavedStateHandle) : ViewModel() {

    val id: Long = handle["id"] ?: 0L

    val product: StateFlow<Product?> = app.productCache.observeById(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _levels = MutableStateFlow<List<StockLevel>>(emptyList())
    val levels: StateFlow<List<StockLevel>> = _levels.asStateFlow()

    private val _history = MutableStateFlow<List<Movement>>(emptyList())
    val history: StateFlow<List<Movement>> = _history.asStateFlow()

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun load() = viewModelScope.launch {
        // Trae la versión del servidor al caché (si hay red) y el detalle online.
        runCatching { app.productRepository.getById(id) }
        runCatching {
            _levels.value = app.inventory.stockLevels(id)
            _history.value = app.inventory.productMovements(id)
            _offline.value = false
        }.onFailure { _offline.value = true }
    }

    fun delete() = viewModelScope.launch {
        val p = product.value ?: return@launch
        runCatching { app.productRepository.delete(p) }
            .onSuccess { _deleted.value = true }
            .onFailure { _message.value = it.message }
    }

    /** Imprime en la Zebra configurada; sin impresora, devuelve el ZPL para compartir. */
    fun print(onNoPrinter: (String) -> Unit, printed: String) = viewModelScope.launch {
        val p = product.value ?: return@launch
        val zpl = Zpl.product(p)
        val s = app.settings.current
        if (s.printerHost.isBlank()) {
            onNoPrinter(zpl)
            return@launch
        }
        runCatching { LabelPrinter.print(s.printerHost, s.printerPort, zpl) }
            .onSuccess { _message.value = printed }
            .onFailure { _message.value = it.message }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

@Composable
fun ProductDetailScreen(
    user: AppUser,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: ProductDetailViewModel = appViewModel { app, handle -> ProductDetailViewModel(app, handle) },
) {
    val product by viewModel.product.collectAsStateWithLifecycle()
    val levels by viewModel.levels.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val printedText = stringResource(R.string.label_printed)

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    val p = product
    AppScaffold(
        title = p?.name ?: stringResource(R.string.product_detail),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (p != null) {
                IconButton(onClick = {
                    viewModel.print(onNoPrinter = { zpl ->
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, zpl), p.sku,
                        ))
                    }, printed = printedText)
                }) { Icon(Icons.Default.Print, contentDescription = stringResource(R.string.action_print_label)) }
                IconButton(onClick = { onNavigate(Routes.editor(p.id)) }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_product))
                }
                if (user.isSupervisor) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_product))
                    }
                }
            }
        },
    ) { modifier ->
        if (p == null) return@AppScaffold
        LazyColumn(modifier) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProductCard(p)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { onNavigate(Routes.movement(MovementType.ENTRADA, p.sku)) }, Modifier.weight(1f)) {
                            Icon(Icons.Default.CallReceived, contentDescription = null); Text(stringResource(R.string.module_in))
                        }
                        OutlinedButton(onClick = { onNavigate(Routes.movement(MovementType.SALIDA, p.sku)) }, Modifier.weight(1f)) {
                            Icon(Icons.Default.CallMade, contentDescription = null); Text(stringResource(R.string.module_out))
                        }
                        OutlinedButton(onClick = { onNavigate(Routes.movement(MovementType.TRANSFERENCIA, p.sku)) }, Modifier.weight(1f)) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        }
                    }
                    Details(p)
                    if (offline) {
                        Text(stringResource(R.string.detail_offline), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (levels.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.section_stock_by_location)) }
                items(levels) { l ->
                    ListItem(
                        headlineContent = { Text(l.location) },
                        supportingContent = {
                            if (l.lot.isNotBlank() || l.expiresOn != null) {
                                Text(stringResource(R.string.lot_expiry, l.lot.ifBlank { "—" }, l.expiresOn ?: "—"))
                            }
                        },
                        trailingContent = { Text("${l.quantity}", style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
            if (history.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.section_history)) }
                items(history) { m -> MovementRow(m) }
            }
        }
    }

    if (confirmDelete && p != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_product),
            text = stringResource(R.string.delete_confirm, p.name, p.quantity),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Details(p: Product) {
    val rows = buildList {
        if (p.category.isNotBlank()) add(stringResource(R.string.field_category) to p.category)
        if (p.minStock > 0) add(stringResource(R.string.field_min_stock) to p.minStock.toString())
        if (p.unitCost > 0) add(stringResource(R.string.field_unit_cost) to "%.2f".format(p.unitCost))
        if (p.tracksLots) add(stringResource(R.string.field_tracks_lots) to stringResource(R.string.yes))
        if (p.altCodes.isNotEmpty()) add(stringResource(R.string.field_alt_codes) to p.altCodes.joinToString(", "))
        if (p.description.isNotBlank()) add(stringResource(R.string.field_description) to p.description)
    }
    rows.forEach { (k, v) ->
        Row {
            Text("$k: ", style = MaterialTheme.typography.labelLarge)
            Text(v, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun MovementRow(m: Movement) {
    ListItem(
        headlineContent = {
            Text("${actionLabel(m.action)}  ${if (m.delta > 0) "+" else ""}${m.delta}  →  ${m.quantityAfter}")
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    m.sku,
                    m.location.takeIf { it.isNotBlank() }?.let { loc -> if (m.toLocation.isNotBlank()) "$loc ⇄ ${m.toLocation}" else loc },
                    m.lot.takeIf { it.isNotBlank() }?.let { "L:$it" },
                    m.reason.takeIf { it.isNotBlank() },
                    m.reference.takeIf { it.isNotBlank() },
                    m.operatorName.ifBlank { m.operatorCode },
                    relativeTime(m.createdAt),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
    HorizontalDivider()
}
