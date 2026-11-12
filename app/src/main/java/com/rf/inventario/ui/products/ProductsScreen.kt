package com.rf.inventario.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rf.inventario.R
import com.rf.inventario.data.MovementType
import com.rf.inventario.data.Product
import com.rf.inventario.export.CsvExporter
import com.rf.inventario.ui.Routes
import com.rf.inventario.ui.appViewModel
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.CameraScanButton
import com.rf.inventario.ui.common.EmptyMessage
import com.rf.inventario.ui.common.ProductCard
import com.rf.inventario.ui.common.ScanListener
import com.rf.inventario.ui.common.StatusBadge
import com.rf.inventario.ui.common.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: ProductsViewModel = appViewModel { app, _ -> ProductsViewModel(app) },
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val quick by viewModel.quick.collectAsStateWithLifecycle()
    val notFound by viewModel.notFound.collectAsStateWithLifecycle()
    val location by viewModel.locationScanned.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ScanListener { viewModel.onScan(it) }
    LaunchedEffect(location) {
        location?.let { viewModel.consumeLocation(); onNavigate(Routes.location(it)) }
    }

    AppScaffold(
        title = stringResource(R.string.products_title),
        subtitle = stringResource(
            if (sync.online) R.string.products_subtitle_online else R.string.products_subtitle_offline,
            total,
            sync.lastSuccessAt?.let { relativeTime(it) } ?: "—",
        ),
        onBack = onBack,
        actions = {
            CameraScanButton(onScan = { viewModel.onScan(it) })
            IconButton(onClick = { viewModel.allCached { CsvExporter.share(context, it) } }) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_export_csv))
            }
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate(Routes.editor()) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_product))
            }
        },
    ) { modifier ->
        Column(modifier) {
            if (sync.running) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == StockFilter.ALL, { viewModel.onFilterChange(StockFilter.ALL) }, { Text(stringResource(R.string.filter_all)) })
                FilterChip(filter == StockFilter.LOW, { viewModel.onFilterChange(StockFilter.LOW) }, { Text(stringResource(R.string.filter_low)) })
                FilterChip(filter == StockFilter.OUT, { viewModel.onFilterChange(StockFilter.OUT) }, { Text(stringResource(R.string.filter_out)) })
            }

            if (products.isEmpty()) {
                EmptyMessage(stringResource(if (total == 0) R.string.empty_state else R.string.empty_search))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(products, key = { it.id }) { product ->
                        ProductRow(product, onClick = { onNavigate(Routes.product(product.id)) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    quick?.let { p ->
        ModalBottomSheet(onDismissRequest = viewModel::dismissQuick) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProductCard(p)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickButton(Icons.Default.CallReceived, R.string.module_in, Modifier.weight(1f)) {
                        viewModel.dismissQuick(); onNavigate(Routes.movement(MovementType.ENTRADA, p.sku))
                    }
                    QuickButton(Icons.Default.CallMade, R.string.module_out, Modifier.weight(1f)) {
                        viewModel.dismissQuick(); onNavigate(Routes.movement(MovementType.SALIDA, p.sku))
                    }
                    QuickButton(Icons.Default.SwapHoriz, R.string.module_transfer, Modifier.weight(1f)) {
                        viewModel.dismissQuick(); onNavigate(Routes.movement(MovementType.TRANSFERENCIA, p.sku))
                    }
                }
                Button(onClick = { viewModel.dismissQuick(); onNavigate(Routes.product(p.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_view_detail))
                }
            }
        }
    }

    notFound?.let { code ->
        AlertDialog(
            onDismissRequest = viewModel::dismissNotFound,
            title = { Text(stringResource(R.string.scan_unknown_title)) },
            text = { Text(stringResource(R.string.scan_unknown, code)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissNotFound(); onNavigate(Routes.editor(sku = code)) }) {
                    Text(stringResource(R.string.action_create_product))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissNotFound) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun QuickButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        headlineContent = { Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.product_sku_location, product.sku, product.location.ifBlank { "—" }))
                when {
                    product.outOfStock -> StatusBadge(stringResource(R.string.badge_out), MaterialTheme.colorScheme.error)
                    product.lowStock -> StatusBadge(stringResource(R.string.badge_low_stock), MaterialTheme.colorScheme.error)
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text("${product.quantity}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(product.unit, style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}
