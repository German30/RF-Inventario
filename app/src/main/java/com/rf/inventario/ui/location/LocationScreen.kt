package com.rf.inventario.ui.location

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rf.inventario.InventarioApplication
import com.rf.inventario.R
import com.rf.inventario.data.Location
import com.rf.inventario.data.StockLevel
import com.rf.inventario.labels.LabelPrinter
import com.rf.inventario.labels.Zpl
import com.rf.inventario.scanner.ScanTarget
import com.rf.inventario.ui.appViewModel
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.CodeEntryField
import com.rf.inventario.ui.common.EmptyMessage
import com.rf.inventario.ui.common.ScanListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationViewModel(private val app: InventarioApplication, handle: SavedStateHandle) : ViewModel() {
    private val _code = MutableStateFlow(handle.get<String>("code").orEmpty())
    val code: StateFlow<String> = _code.asStateFlow()
    private val _contents = MutableStateFlow<List<StockLevel>?>(null)
    val contents: StateFlow<List<StockLevel>?> = _contents.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        if (_code.value.isNotBlank()) load(_code.value)
    }

    fun onScan(raw: String) = viewModelScope.launch {
        when (val t = app.scanRouter.route(raw)) {
            is ScanTarget.IsLocation -> { app.scanFeedback.success(); load(t.location.code) }
            else -> load(raw.uppercase())
        }
    }

    private fun load(code: String) = viewModelScope.launch {
        _code.value = code
        _contents.value = null
        runCatching { app.inventory.locationContents(code) }
            .onSuccess { _contents.value = it }
            .onFailure { _message.value = it.message; _contents.value = emptyList() }
    }

    fun print(onNoPrinter: (String) -> Unit, printed: String) = viewModelScope.launch {
        val zpl = Zpl.location(Location(_code.value))
        val s = app.settings.current
        if (s.printerHost.isBlank()) return@launch onNoPrinter(zpl)
        runCatching { LabelPrinter.print(s.printerHost, s.printerPort, zpl) }
            .onSuccess { _message.value = printed }
            .onFailure { _message.value = it.message }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

@Composable
fun LocationScreen(
    onBack: () -> Unit,
    onProduct: (Long) -> Unit,
    viewModel: LocationViewModel = appViewModel { app, handle -> LocationViewModel(app, handle) },
) {
    val code by viewModel.code.collectAsStateWithLifecycle()
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val printed = stringResource(R.string.label_printed)
    ScanListener { viewModel.onScan(it) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    AppScaffold(
        title = code.ifBlank { stringResource(R.string.module_location) },
        subtitle = contents?.let { list -> stringResource(R.string.location_summary, list.size, list.sumOf { it.quantity }) },
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (code.isNotBlank()) {
                IconButton(onClick = {
                    viewModel.print(onNoPrinter = { zpl ->
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, zpl), code))
                    }, printed = printed)
                }) { Icon(Icons.Default.Print, stringResource(R.string.action_print_label)) }
            }
        },
    ) { modifier ->
        Column(modifier) {
            CodeEntryField(stringResource(R.string.field_scan_location), onSubmit = { viewModel.onScan(it) }, modifier = Modifier.padding(16.dp))
            val list = contents
            when {
                code.isBlank() -> EmptyMessage(stringResource(R.string.location_hint))
                list == null -> Unit
                list.isEmpty() -> EmptyMessage(stringResource(R.string.location_empty))
                else -> LazyColumn {
                    items(list) { s ->
                        ListItem(
                            modifier = Modifier.clickable { onProduct(s.productId) },
                            headlineContent = { Text(s.name.ifBlank { s.sku }) },
                            supportingContent = {
                                Text(listOfNotNull(s.sku, s.lot.takeIf { it.isNotBlank() }?.let { "L:$it" }, s.expiresOn).joinToString(" · "))
                            },
                            trailingContent = { Text("${s.quantity}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
