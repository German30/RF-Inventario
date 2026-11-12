package com.rf.inventario.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rf.inventario.InventarioApplication
import com.rf.inventario.R
import com.rf.inventario.data.AppUser
import com.rf.inventario.data.Dashboard
import com.rf.inventario.data.MovementType
import com.rf.inventario.data.OrderType
import com.rf.inventario.ui.Routes
import com.rf.inventario.ui.appViewModel
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.ConfirmDialog
import com.rf.inventario.ui.common.KpiCard
import com.rf.inventario.ui.common.ScanListener
import com.rf.inventario.ui.common.relativeTime
import com.rf.inventario.scanner.ScanTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat

class HomeViewModel(private val app: InventarioApplication) : ViewModel() {

    private val _dashboard = MutableStateFlow<Dashboard?>(null)
    val dashboard: StateFlow<Dashboard?> = _dashboard.asStateFlow()

    val sync = app.syncRepository.state
    val pending: StateFlow<Int> = app.operations.pendingCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val failed: StateFlow<Int> = app.operations.failedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun refresh() {
        viewModelScope.launch {
            app.syncRepository.sync()
            // El dashboard es solo online; sin red se muestra el último que hubo.
            runCatching { app.inventory.dashboard() }.onSuccess { _dashboard.value = it }
        }
    }

    suspend fun route(code: String): ScanTarget = app.scanRouter.route(code)
}

private data class Module(val label: Int, val icon: ImageVector, val route: String, val supervisorOnly: Boolean = false)

private val modules = listOf(
    Module(R.string.module_lookup, Icons.Default.Inventory2, Routes.PRODUCTS),
    Module(R.string.module_in, Icons.Default.CallReceived, Routes.movement(MovementType.ENTRADA)),
    Module(R.string.module_out, Icons.Default.CallMade, Routes.movement(MovementType.SALIDA)),
    Module(R.string.module_transfer, Icons.Default.SwapHoriz, Routes.movement(MovementType.TRANSFERENCIA)),
    Module(R.string.module_adjust, Icons.Default.Tune, Routes.movement(MovementType.AJUSTE)),
    Module(R.string.module_count, Icons.Default.Checklist, Routes.COUNTS),
    Module(R.string.module_receive, Icons.Default.LocalShipping, Routes.orders(OrderType.COMPRA)),
    Module(R.string.module_pick, Icons.Default.ShoppingCart, Routes.orders(OrderType.VENTA)),
    Module(R.string.module_location, Icons.Default.Place, Routes.location()),
    Module(R.string.module_history, Icons.Default.History, Routes.HISTORY),
    Module(R.string.module_pending, Icons.Default.CloudSync, Routes.PENDING),
    Module(R.string.module_settings, Icons.Default.Settings, Routes.SETTINGS),
)

@Composable
fun HomeScreen(
    user: AppUser,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = appViewModel { app, _ -> HomeViewModel(app) },
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Desde el inicio, escanear lleva directo a la ficha del producto o al contenido del estante.
    ScanListener { code ->
        scope.launch {
            when (val t = viewModel.route(code)) {
                is ScanTarget.IsProduct -> onNavigate(Routes.product(t.product.id))
                is ScanTarget.IsLocation -> onNavigate(Routes.location(t.location.code))
                is ScanTarget.Unknown -> onNavigate(Routes.movement(MovementType.ENTRADA, t.raw))
            }
        }
    }

    AppScaffold(
        title = stringResource(R.string.app_name),
        subtitle = stringResource(
            if (user.isSupervisor) R.string.session_supervisor else R.string.session_operator,
            user.name.ifBlank { user.code },
        ),
        onBack = null,
        actions = {
            // Con confirmación: en una terminal RF un Enter suelto sobre el botón
            // con foco no debe poder cerrar la sesión (ni perder el contexto).
            IconButton(onClick = { confirmLogout = true }) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.logout))
            }
        },
    ) { modifier ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SyncChip(
                    online = sync.online,
                    running = sync.running,
                    lastSync = sync.lastSuccessAt,
                    pending = pending,
                    failed = failed,
                    onClick = { if (pending + failed > 0) onNavigate(Routes.PENDING) else viewModel.refresh() },
                )
            }
            dashboard?.let { d ->
                item(span = { GridItemSpan(maxLineSpan) }) { Kpis(d, onNavigate) }
            }
            items(modules.filter { !it.supervisorOnly || user.isSupervisor }) { m ->
                Card(onClick = { onNavigate(m.route) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(m.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(m.label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
            dashboard?.takeIf { it.lowStockAlerts.isNotEmpty() || it.expiringSoon.isNotEmpty() }?.let { d ->
                item(span = { GridItemSpan(maxLineSpan) }) { Alerts(d, onNavigate) }
            }
        }
    }

    if (confirmLogout) {
        LogoutDialog(pending = pending, onConfirm = onLogout, onDismiss = { confirmLogout = false })
    }
}

@Composable
private fun LogoutDialog(pending: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.logout),
        text = if (pending > 0) stringResource(R.string.logout_confirm_pending, pending) else stringResource(R.string.logout_confirm),
        confirmLabel = stringResource(R.string.logout),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun SyncChip(online: Boolean, running: Boolean, lastSync: Long?, pending: Int, failed: Int, onClick: () -> Unit) {
    val text = when {
        running -> stringResource(R.string.sync_running)
        failed > 0 -> stringResource(R.string.sync_failed_ops, failed)
        pending > 0 -> stringResource(R.string.sync_pending_ops, pending)
        !online -> stringResource(R.string.sync_offline, lastSync?.let { relativeTime(it) } ?: "—")
        else -> stringResource(R.string.sync_ok, lastSync?.let { relativeTime(it) } ?: "—")
    }
    AssistChip(
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = {
            Icon(
                when {
                    failed > 0 -> Icons.Default.Warning
                    online && pending == 0 -> Icons.Default.CloudSync
                    else -> Icons.Default.CloudOff
                },
                contentDescription = null,
            )
        },
    )
}

@Composable
private fun Kpis(d: Dashboard, onNavigate: (String) -> Unit) {
    val money = NumberFormat.getCurrencyInstance()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiCard(stringResource(R.string.kpi_products), d.products.toString(), Modifier.weight(1f),
                onClick = { onNavigate(Routes.PRODUCTS) })
            KpiCard(stringResource(R.string.kpi_units), d.units.toString(), Modifier.weight(1f))
            KpiCard(stringResource(R.string.kpi_value), money.format(d.inventoryValue), Modifier.weight(1.3f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiCard(stringResource(R.string.kpi_low), d.lowStock.toString(), Modifier.weight(1f), highlight = d.lowStock > 0,
                onClick = { onNavigate(Routes.PRODUCTS) })
            KpiCard(stringResource(R.string.kpi_out), d.outOfStock.toString(), Modifier.weight(1f), highlight = d.outOfStock > 0)
            KpiCard(stringResource(R.string.kpi_today), d.movementsToday.toString(), Modifier.weight(1f),
                onClick = { onNavigate(Routes.HISTORY) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiCard(stringResource(R.string.kpi_receipts), d.pendingReceipts.toString(), Modifier.weight(1f),
                onClick = { onNavigate(Routes.orders(OrderType.COMPRA)) })
            KpiCard(stringResource(R.string.kpi_picks), d.pendingPicks.toString(), Modifier.weight(1f),
                onClick = { onNavigate(Routes.orders(OrderType.VENTA)) })
            KpiCard(stringResource(R.string.kpi_counts), d.openCounts.toString(), Modifier.weight(1f),
                onClick = { onNavigate(Routes.COUNTS) })
        }
    }
}

@Composable
private fun Alerts(d: Dashboard, onNavigate: (String) -> Unit) {
    Card {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.alerts_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            d.lowStockAlerts.take(5).forEach { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text(stringResource(R.string.alert_low_stock, p.quantity, p.minStock)) },
                    leadingContent = { Icon(Icons.Default.Warning, contentDescription = null) },
                    modifier = Modifier.padding(0.dp),
                    trailingContent = {
                        IconButton(onClick = { onNavigate(Routes.movement(MovementType.ENTRADA, p.sku)) }) {
                            Icon(Icons.Default.MoveDown, contentDescription = stringResource(R.string.module_in))
                        }
                    },
                )
            }
            d.expiringSoon.take(5).forEach { s ->
                ListItem(
                    headlineContent = { Text(s.name.ifBlank { s.sku }) },
                    supportingContent = {
                        Text(stringResource(R.string.alert_expiring, s.lot.ifBlank { "—" }, s.expiresOn ?: "—", s.location, s.quantity))
                    },
                    leadingContent = { Icon(Icons.Default.Warning, contentDescription = null) },
                )
            }
        }
    }
}
