package com.rf.inventario.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rf.inventario.InventarioApplication
import com.rf.inventario.R
import com.rf.inventario.data.ConflictException
import com.rf.inventario.data.Product
import com.rf.inventario.ui.appViewModel
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.ScanListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val loaded: Boolean = false,
    val original: Product? = null,
    val name: String = "",
    val sku: String = "",
    val quantity: String = "0",
    val location: String = "",
    val category: String = "",
    val unit: String = "UN",
    val minStock: String = "0",
    val unitCost: String = "0",
    val tracksLots: Boolean = false,
    val altCodes: String = "",
    val description: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val savedId: Long? = null,
    val saved: Boolean = false,
) {
    val isNew: Boolean get() = original == null
    val valid: Boolean
        get() = name.isNotBlank() && sku.isNotBlank() && quantity.toIntOrNull() != null &&
            (minStock.isBlank() || minStock.toIntOrNull() != null) &&
            (unitCost.isBlank() || unitCost.replace(',', '.').toDoubleOrNull() != null)
}

class ProductEditorViewModel(private val app: InventarioApplication, handle: SavedStateHandle) : ViewModel() {

    private val id: Long = handle["id"] ?: 0L
    private val _state = MutableStateFlow(EditorState(sku = handle["sku"] ?: ""))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = if (id != 0L) app.productRepository.getById(id) else null
            _state.update { s ->
                if (p == null) s.copy(loaded = true)
                else s.copy(
                    loaded = true, original = p, name = p.name, sku = p.sku, quantity = p.quantity.toString(),
                    location = p.location, category = p.category, unit = p.unit, minStock = p.minStock.toString(),
                    unitCost = if (p.unitCost == 0.0) "0" else p.unitCost.toString(), tracksLots = p.tracksLots,
                    altCodes = p.altCodes.joinToString(", "), description = p.description,
                )
            }
        }
    }

    fun edit(transform: (EditorState) -> EditorState) = _state.update { transform(it).copy(error = null) }

    /** Escanear en el editor: completa SKU si está vacío; si no, agrega un código alternativo. */
    fun onScan(code: String) = _state.update { s ->
        when {
            s.sku.isBlank() -> s.copy(sku = code)
            code.equals(s.sku, true) || s.altCodes.split(',').any { it.trim().equals(code, true) } -> s
            else -> s.copy(altCodes = listOf(s.altCodes, code).filter { it.isNotBlank() }.joinToString(", "))
        }
    }

    fun save() {
        val s = _state.value
        if (!s.valid || s.saving) return
        val base = s.original ?: Product(name = "", sku = "", quantity = 0, location = "")
        val product = base.copy(
            name = s.name.trim(), sku = s.sku.trim(), quantity = s.quantity.toInt(), location = s.location.trim().uppercase(),
            category = s.category.trim(), unit = s.unit.trim().ifEmpty { "UN" }.uppercase(),
            minStock = s.minStock.toIntOrNull() ?: 0, unitCost = s.unitCost.replace(',', '.').toDoubleOrNull() ?: 0.0,
            tracksLots = s.tracksLots, description = s.description.trim(),
            altCodes = s.altCodes.split(',', ' ', ';').map(String::trim).filter(String::isNotEmpty).distinct(),
        )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { app.productRepository.save(product) }
                .onSuccess {
                    val saved = app.productCache.findByCode(product.sku)
                    _state.update { it.copy(saving = false, saved = true, savedId = saved?.id) }
                }
                .onFailure { e ->
                    _state.update {
                        val server = (e as? ConflictException)?.serverProduct
                        // Conflicto: se recarga lo del servidor para que el operario decida sobre datos reales.
                        val base2 = if (server != null) it.copy(original = server, quantity = server.quantity.toString()) else it
                        base2.copy(saving = false, error = e.message)
                    }
                }
        }
    }
}

@Composable
fun ProductEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long?) -> Unit,
    viewModel: ProductEditorViewModel = appViewModel { app, handle -> ProductEditorViewModel(app, handle) },
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ScanListener { viewModel.onScan(it) }
    LaunchedEffect(s.saved) { if (s.saved) onSaved(s.savedId) }
    LaunchedEffect(s.error) { s.error?.let { snackbar.showSnackbar(it) } }

    AppScaffold(
        title = stringResource(if (s.isNew) R.string.add_product else R.string.edit_product),
        onBack = onBack,
        snackbarHostState = snackbar,
    ) { modifier ->
        if (!s.loaded) return@AppScaffold
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.editor_scan_hint), style = MaterialTheme.typography.bodySmall)
            Field(s.name, R.string.field_name) { v -> viewModel.edit { it.copy(name = v) } }
            Field(s.sku, R.string.field_sku) { v -> viewModel.edit { it.copy(sku = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(s.quantity, R.string.field_quantity_total, Modifier.weight(1f), KeyboardType.Number) { v ->
                    viewModel.edit { it.copy(quantity = v.filter(Char::isDigit)) }
                }
                Field(s.unit, R.string.field_unit, Modifier.weight(1f)) { v -> viewModel.edit { it.copy(unit = v) } }
            }
            if (!s.isNew) Text(stringResource(R.string.editor_quantity_note), style = MaterialTheme.typography.bodySmall)
            Field(s.location, R.string.field_location_main) { v -> viewModel.edit { it.copy(location = v) } }
            Field(s.category, R.string.field_category) { v -> viewModel.edit { it.copy(category = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(s.minStock, R.string.field_min_stock, Modifier.weight(1f), KeyboardType.Number) { v ->
                    viewModel.edit { it.copy(minStock = v.filter(Char::isDigit)) }
                }
                Field(s.unitCost, R.string.field_unit_cost, Modifier.weight(1f), KeyboardType.Decimal) { v ->
                    viewModel.edit { it.copy(unitCost = v) }
                }
            }
            Field(s.altCodes, R.string.field_alt_codes) { v -> viewModel.edit { it.copy(altCodes = v) } }
            Field(s.description, R.string.field_description, singleLine = false) { v -> viewModel.edit { it.copy(description = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.field_tracks_lots))
                    Text(stringResource(R.string.field_tracks_lots_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = s.tracksLots, onCheckedChange = { v -> viewModel.edit { it.copy(tracksLots = v) } })
            }
            Button(
                onClick = viewModel::save,
                enabled = s.valid && !s.saving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun Field(
    value: String,
    label: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
    )
}
