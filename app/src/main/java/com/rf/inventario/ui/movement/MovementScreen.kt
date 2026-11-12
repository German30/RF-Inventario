package com.rf.inventario.ui.movement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rf.inventario.InventarioApplication
import com.rf.inventario.R
import com.rf.inventario.data.MovementType
import com.rf.inventario.data.Product
import com.rf.inventario.data.StockLevel
import com.rf.inventario.data.ops.SubmitResult
import com.rf.inventario.data.remote.dto.OperacionBodyDto
import com.rf.inventario.scanner.ScanTarget
import com.rf.inventario.ui.appViewModel
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.CodeEntryField
import com.rf.inventario.ui.common.ProductCard
import com.rf.inventario.ui.common.QuantityStepper
import com.rf.inventario.ui.common.ScanListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * yyyy-MM-dd válido. Sin java.time a propósito: minSdk 24 (terminales Zebra con
 * Android 7) no lo tiene sin desugaring.
 */
internal fun isIsoDate(text: String): Boolean {
    val shape = text.length == 10 && text[4] == '-' && text[7] == '-' &&
        text.filterIndexed { i, _ -> i != 4 && i != 7 }.all(Char::isDigit)
    if (!shape) return false
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(text)
    }.isSuccess
}

/** Mensaje de resultado (verde/ámbar/rojo) que queda visible hasta la próxima lectura. */
data class Feedback(val text: String, val kind: Kind) {
    enum class Kind { OK, QUEUED, ERROR }
}

data class MovementUiState(
    val type: MovementType,
    val product: Product? = null,
    val levels: List<StockLevel> = emptyList(),
    val location: String = "",
    val destination: String = "",
    val lot: String = "",
    val expiry: String = "",
    val quantity: Int = 1,
    /** Solo AJUSTE: true = descontar. */
    val negative: Boolean = false,
    val reason: String = "",
    val unknownCode: String? = null,
    val busy: Boolean = false,
    val feedback: Feedback? = null,
) {
    val expiryValid: Boolean get() = expiry.isBlank() || isIsoDate(expiry)
    val needsLot: Boolean get() = product?.tracksLots == true && (type == MovementType.ENTRADA || (type == MovementType.AJUSTE && !negative))
    val canSubmit: Boolean
        get() = product != null && quantity > 0 && !busy && expiryValid &&
            (!needsLot || lot.isNotBlank()) &&
            (type != MovementType.TRANSFERENCIA || destination.isNotBlank())
}

class MovementViewModel(private val app: InventarioApplication, handle: SavedStateHandle) : ViewModel() {

    private val _state = MutableStateFlow(MovementUiState(type = MovementType.valueOf(handle["type"] ?: "ENTRADA")))
    val state: StateFlow<MovementUiState> = _state.asStateFlow()

    init {
        handle.get<String>("code")?.takeIf { it.isNotBlank() }?.let { onScan(it) }
    }

    fun onScan(code: String) = viewModelScope.launch {
        when (val target = app.scanRouter.route(code)) {
            is ScanTarget.IsLocation -> {
                app.scanFeedback.success()
                _state.update {
                    if (it.type == MovementType.TRANSFERENCIA && it.location.isNotBlank() && it.product != null) {
                        it.copy(destination = target.location.code, feedback = null)
                    } else {
                        it.copy(location = target.location.code, feedback = null)
                    }
                }
            }
            is ScanTarget.IsProduct -> selectProduct(target.product)
            is ScanTarget.Unknown -> {
                // El caché puede estar atrasado: se pregunta al servidor antes de rendirse.
                val remote = runCatching { app.productRepository.findBySku(target.raw) }.getOrNull()
                if (remote != null) {
                    selectProduct(remote)
                } else {
                    app.scanFeedback.error()
                    _state.update { it.copy(unknownCode = target.raw, feedback = null) }
                }
            }
        }
    }

    private fun selectProduct(p: Product) {
        app.scanFeedback.success()
        _state.update {
            // Re-escanear el mismo producto suma 1 (flujo típico de terminal RF).
            if (it.product?.id == p.id) it.copy(quantity = it.quantity + 1, feedback = null)
            else it.copy(product = p, quantity = 1, lot = "", expiry = "", levels = emptyList(), unknownCode = null, feedback = null)
        }
        viewModelScope.launch {
            runCatching { app.inventory.stockLevels(p.id) }.onSuccess { levels -> _state.update { it.copy(levels = levels) } }
        }
    }

    fun setQuantity(q: Int) = _state.update { it.copy(quantity = q) }
    fun setLocation(v: String) = _state.update { it.copy(location = v.uppercase()) }
    fun setDestination(v: String) = _state.update { it.copy(destination = v.uppercase()) }
    fun setLot(v: String) = _state.update { it.copy(lot = v) }
    fun setExpiry(v: String) = _state.update { it.copy(expiry = v) }
    fun setReason(v: String) = _state.update { it.copy(reason = if (it.reason == v) "" else v) }
    fun setNegative(v: Boolean) = _state.update { it.copy(negative = v) }
    fun clearProduct() = _state.update { it.copy(product = null, levels = emptyList(), quantity = 1, unknownCode = null) }
    fun dismissUnknown() = _state.update { it.copy(unknownCode = null) }

    fun submit(okText: (Int, Int) -> String, queuedText: String) {
        val s = _state.value
        val p = s.product ?: return
        if (!s.canSubmit) return
        val signed = if (s.type == MovementType.AJUSTE && s.negative) -s.quantity else s.quantity
        val body = OperacionBodyDto(
            tipo = s.type.name,
            productoId = p.id,
            cantidad = signed,
            ubicacion = s.location.ifBlank { null },
            ubicacionDestino = s.destination.ifBlank { null },
            lote = s.lot.ifBlank { null },
            vencimiento = s.expiry.ifBlank { null },
            motivo = s.reason.ifBlank { null },
        )
        val localDelta = when (s.type) {
            MovementType.ENTRADA -> s.quantity
            MovementType.SALIDA -> -s.quantity
            MovementType.AJUSTE -> signed
            MovementType.TRANSFERENCIA -> 0
        }
        val summary = "${s.type.name} · ${p.sku} · ${if (localDelta > 0) "+" else ""}${if (s.type == MovementType.TRANSFERENCIA) "${s.quantity} → ${s.destination}" else localDelta}"
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = app.operations.movement(body, summary, p.id, localDelta)
            _state.update {
                when (result) {
                    is SubmitResult.Done -> {
                        app.scanFeedback.success()
                        // Se conserva la ubicación: lo normal es seguir trabajando en el mismo estante.
                        it.copy(busy = false, product = null, levels = emptyList(), quantity = 1, lot = "", expiry = "",
                            destination = "", feedback = Feedback(okText(localDelta, result.product?.quantity ?: (p.quantity + localDelta)), Feedback.Kind.OK))
                    }
                    SubmitResult.Queued -> {
                        app.scanFeedback.queued()
                        it.copy(busy = false, product = null, levels = emptyList(), quantity = 1, lot = "", expiry = "",
                            destination = "", feedback = Feedback(queuedText, Feedback.Kind.QUEUED))
                    }
                    is SubmitResult.Rejected -> {
                        app.scanFeedback.error()
                        it.copy(busy = false, feedback = Feedback(result.message, Feedback.Kind.ERROR))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovementScreen(
    onBack: () -> Unit,
    onCreateProduct: (String) -> Unit,
    viewModel: MovementViewModel = appViewModel { app, handle -> MovementViewModel(app, handle) },
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    ScanListener { viewModel.onScan(it) }

    val title = stringResource(
        when (s.type) {
            MovementType.ENTRADA -> R.string.module_in
            MovementType.SALIDA -> R.string.module_out
            MovementType.TRANSFERENCIA -> R.string.module_transfer
            MovementType.AJUSTE -> R.string.module_adjust
        },
    )
    val reasons = stringArrayResource(
        when (s.type) {
            MovementType.ENTRADA -> R.array.reasons_in
            MovementType.SALIDA -> R.array.reasons_out
            MovementType.TRANSFERENCIA -> R.array.reasons_transfer
            MovementType.AJUSTE -> R.array.reasons_adjust
        },
    )
    val okTemplate = stringResource(R.string.movement_done)
    val queuedText = stringResource(R.string.movement_queued)

    AppScaffold(title = title, onBack = onBack) { modifier ->
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            s.feedback?.let { FeedbackCard(it) }

            Text(
                stringResource(if (s.type == MovementType.TRANSFERENCIA) R.string.movement_hint_transfer else R.string.movement_hint),
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = s.location,
                onValueChange = viewModel::setLocation,
                label = {
                    Text(stringResource(if (s.type == MovementType.TRANSFERENCIA) R.string.field_origin else R.string.field_location_optional))
                },
                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val product = s.product
            if (product == null) {
                CodeEntryField(label = stringResource(R.string.field_scan_product), onSubmit = viewModel::onScan)
                s.unknownCode?.let { code ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.scan_unknown, code))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onCreateProduct(code) }) { Text(stringResource(R.string.action_create_product)) }
                                OutlinedButton(onClick = viewModel::dismissUnknown) { Text(stringResource(R.string.action_close)) }
                            }
                        }
                    }
                }
            } else {
                ProductCard(product) {
                    IconButton(onClick = viewModel::clearProduct) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                }
                if (s.levels.isNotEmpty()) {
                    Text(
                        s.levels.joinToString("  ·  ") { l ->
                            buildString {
                                append(l.location)
                                if (l.lot.isNotBlank()) append(" [").append(l.lot).append(']')
                                append(": ").append(l.quantity)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (s.type == MovementType.AJUSTE) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !s.negative, onClick = { viewModel.setNegative(false) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.adjust_add)) }
                        SegmentedButton(selected = s.negative, onClick = { viewModel.setNegative(true) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.adjust_remove)) }
                    }
                }

                QuantityStepper(value = s.quantity, onValueChange = viewModel::setQuantity)

                if (s.type == MovementType.TRANSFERENCIA) {
                    OutlinedTextField(
                        value = s.destination,
                        onValueChange = viewModel::setDestination,
                        label = { Text(stringResource(R.string.field_destination)) },
                        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (s.type != MovementType.TRANSFERENCIA || product.tracksLots) {
                    OutlinedTextField(
                        value = s.lot,
                        onValueChange = viewModel::setLot,
                        label = { Text(stringResource(if (s.needsLot) R.string.field_lot_required else R.string.field_lot)) },
                        isError = s.needsLot && s.lot.isBlank(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (s.type == MovementType.ENTRADA || (s.type == MovementType.AJUSTE && !s.negative)) {
                    OutlinedTextField(
                        value = s.expiry,
                        onValueChange = viewModel::setExpiry,
                        label = { Text(stringResource(R.string.field_expiry)) },
                        isError = !s.expiryValid,
                        supportingText = { if (!s.expiryValid) Text(stringResource(R.string.error_date_format)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(stringResource(R.string.field_reason), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reasons.forEach { r ->
                        FilterChip(selected = s.reason == r, onClick = { viewModel.setReason(r) }, label = { Text(r) })
                    }
                }

                Button(
                    onClick = { viewModel.submit({ delta, total -> okTemplate.format(delta, total) }, queuedText) },
                    enabled = s.canSubmit,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        }
    }
}

@Composable
fun FeedbackCard(feedback: Feedback) {
    val color = when (feedback.kind) {
        Feedback.Kind.OK -> MaterialTheme.colorScheme.primaryContainer
        Feedback.Kind.QUEUED -> MaterialTheme.colorScheme.tertiaryContainer
        Feedback.Kind.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Text(feedback.text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
