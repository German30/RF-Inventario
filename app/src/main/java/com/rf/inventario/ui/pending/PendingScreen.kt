package com.rf.inventario.ui.pending

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rf.inventario.InventarioApplication
import com.rf.inventario.R
import com.rf.inventario.data.ops.PendingOp
import com.rf.inventario.ui.appViewModel
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.ConfirmDialog
import com.rf.inventario.ui.common.EmptyMessage
import com.rf.inventario.ui.common.relativeTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PendingViewModel(private val app: InventarioApplication) : ViewModel() {
    val ops: StateFlow<List<PendingOp>> = app.operations.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sync = app.syncRepository.state

    fun sendNow() = viewModelScope.launch { app.syncRepository.sync() }
    fun retry(op: PendingOp) = viewModelScope.launch { app.operations.retry(op.id); app.syncRepository.sync() }
    fun discard(op: PendingOp) = viewModelScope.launch {
        app.operations.discard(op.id)
        // El ajuste optimista de esa operación ya no aplica: se trae el valor real.
        app.syncRepository.sync()
    }
}

@Composable
fun PendingScreen(
    onBack: () -> Unit,
    viewModel: PendingViewModel = appViewModel { app, _ -> PendingViewModel(app) },
) {
    val ops by viewModel.ops.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    var discarding by remember { mutableStateOf<PendingOp?>(null) }

    AppScaffold(title = stringResource(R.string.module_pending), onBack = onBack) { modifier ->
        Column(modifier) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.pending_explain), style = MaterialTheme.typography.bodySmall)
                sync.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = viewModel::sendNow, enabled = !sync.running && ops.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Default.Sync, null)
                    Text(stringResource(R.string.pending_send_now))
                }
            }
            if (ops.isEmpty()) {
                EmptyMessage(stringResource(R.string.pending_empty))
            } else {
                LazyColumn {
                    items(ops, key = { it.id }) { op ->
                        val failed = op.status == PendingOp.Status.FAILED
                        ListItem(
                            headlineContent = { Text(op.summary) },
                            supportingContent = {
                                Text(
                                    (if (failed) stringResource(R.string.pending_rejected) else stringResource(R.string.pending_waiting)) +
                                        " · " + relativeTime(op.createdAt) + (op.lastError?.let { "\n$it" } ?: ""),
                                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                Row {
                                    if (failed) {
                                        IconButton(onClick = { viewModel.retry(op) }) { Icon(Icons.Default.Replay, stringResource(R.string.action_retry)) }
                                    }
                                    IconButton(onClick = { discarding = op }) { Icon(Icons.Default.Delete, stringResource(R.string.action_discard)) }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    discarding?.let { op ->
        ConfirmDialog(
            title = stringResource(R.string.action_discard),
            text = stringResource(R.string.pending_discard_confirm, op.summary),
            confirmLabel = stringResource(R.string.action_discard),
            onConfirm = { viewModel.discard(op) },
            onDismiss = { discarding = null },
        )
    }
}
