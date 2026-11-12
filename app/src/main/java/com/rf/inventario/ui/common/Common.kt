package com.rf.inventario.ui.common

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.rf.inventario.InventarioApplication
import com.rf.inventario.R
import com.rf.inventario.data.Product
import com.rf.inventario.scanner.ScanRouter
import com.rf.inventario.ui.scanner.CameraScannerScreen

/** Acceso al contenedor de dependencias desde un composable. */
@Composable
fun rememberApp(): InventarioApplication {
    val context = LocalContext.current
    return remember { context.applicationContext as InventarioApplication }
}

/**
 * Escucha el lector físico mientras la pantalla esté visible (RESUMED). Es
 * lo que permite trabajar "a gatillo": escanear sin tocar la pantalla.
 */
@Composable
fun ScanListener(enabled: Boolean = true, onScan: suspend (String) -> Unit) {
    val app = rememberApp()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(onScan)
    LaunchedEffect(enabled, lifecycle) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            app.hardwareScans.collect { current(ScanRouter.clean(it.value)) }
        }
    }
}

/** Botón de cámara (plan B sin lector físico) que entrega el código leído. */
@Composable
fun CameraScanButton(onScan: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.scan_use_camera))
    }
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            CameraScannerScreen(
                onResult = { open = false; onScan(ScanRouter.clean(it.value)) },
                onClose = { open = false },
            )
        }
    }
}

/**
 * Campo para teclear un código a mano (etiqueta dañada) con botón de cámara.
 * El lector físico NO pasa por acá: lo toma [ScanListener] aunque el campo no
 * tenga foco.
 */
@Composable
fun CodeEntryField(
    label: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (text.isNotBlank()) {
                onSubmit(text.trim()); text = ""
            }
        }),
        trailingIcon = { CameraScanButton(onScan = onSubmit) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!subtitle.isNullOrBlank()) {
                            Text(subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = actions,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
    ) { padding ->
        content(Modifier.fillMaxSize().padding(padding))
    }
}

/** Cantidad con botones grandes (+/−): se usa con guantes. */
@Composable
fun QuantityStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    label: String = stringResource(R.string.field_quantity),
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(onClick = { onValueChange((value - 1).coerceAtLeast(min)) }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_less))
        }
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter(Char::isDigit).take(6)
                text = digits
                digits.toIntOrNull()?.let { onValueChange(it.coerceAtLeast(min)) }
            },
            label = { Text(label) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
        FilledTonalIconButton(onClick = { onValueChange(value + 1) }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_more))
        }
    }
}

/** Tarjeta resumen de un producto (lo que se escaneó). */
@Composable
fun ProductCard(product: Product, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.product_sku_location, product.sku, product.location.ifBlank { "—" }),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (product.lowStock) StatusBadge(stringResource(R.string.badge_low_stock), MaterialTheme.colorScheme.error)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${product.quantity}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(product.unit, style = MaterialTheme.typography.labelSmall)
            }
            trailing()
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier.padding(top = 4.dp), color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
fun KpiCard(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = if (highlight) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        CardDefaults.cardColors()
    }
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = colors) { content() }
    else Card(modifier = modifier, colors = colors) { content() }
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

fun relativeTime(epochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** Etiqueta legible de una acción del historial. */
@Composable
fun actionLabel(action: String): String = stringResource(
    when (action) {
        "CREACION" -> R.string.action_kind_create
        "EDICION" -> R.string.action_kind_edit
        "AJUSTE_STOCK" -> R.string.action_kind_adjust
        "ELIMINACION" -> R.string.action_kind_delete
        "ENTRADA" -> R.string.action_kind_in
        "SALIDA" -> R.string.action_kind_out
        "TRANSFERENCIA" -> R.string.action_kind_transfer
        "CONTEO" -> R.string.action_kind_count
        "RECEPCION" -> R.string.action_kind_receive
        "PICKING" -> R.string.action_kind_pick
        else -> R.string.action_kind_other
    },
)
