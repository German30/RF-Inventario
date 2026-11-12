package com.rf.inventario.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rf.inventario.BuildConfig
import com.rf.inventario.R
import com.rf.inventario.labels.LabelPrinter
import com.rf.inventario.ui.common.AppScaffold
import com.rf.inventario.ui.common.rememberApp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = rememberApp()
    val s by app.settings.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var host by remember(s.printerHost) { mutableStateOf(s.printerHost) }
    var idle by remember(s.idleMinutes) { mutableStateOf(s.idleMinutes.toString()) }
    val testOk = stringResource(R.string.settings_printer_ok)
    val resynced = stringResource(R.string.settings_resync_done)

    AppScaffold(title = stringResource(R.string.module_settings), onBack = onBack, snackbarHostState = snackbar) { modifier ->
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Toggle(stringResource(R.string.settings_beep), s.beep) { v -> app.settings.update { it.copy(beep = v) } }
            Toggle(stringResource(R.string.settings_vibrate), s.vibrate) { v -> app.settings.update { it.copy(vibrate = v) } }
            Toggle(stringResource(R.string.settings_scan_adds_one), s.scanAddsOne, stringResource(R.string.settings_scan_adds_one_hint)) { v ->
                app.settings.update { it.copy(scanAddsOne = v) }
            }
            HorizontalDivider()

            OutlinedTextField(
                value = idle,
                onValueChange = { v ->
                    idle = v.filter(Char::isDigit).take(3)
                    idle.toIntOrNull()?.let { m -> app.settings.update { it.copy(idleMinutes = m) } }
                },
                enabled = !s.idleLockedByMdm,
                label = { Text(stringResource(R.string.settings_idle)) },
                supportingText = { Text(stringResource(if (s.idleLockedByMdm) R.string.settings_locked_mdm else R.string.settings_idle_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()

            Text(stringResource(R.string.settings_printer), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim(); app.settings.update { st -> st.copy(printerHost = host) } },
                enabled = !s.printerLockedByMdm,
                label = { Text(stringResource(R.string.settings_printer_host)) },
                supportingText = { Text(stringResource(if (s.printerLockedByMdm) R.string.settings_locked_mdm else R.string.settings_printer_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = s.printerHost.isNotBlank(),
                onClick = {
                    scope.launch {
                        val r = runCatching { LabelPrinter.print(s.printerHost, s.printerPort, "^XA^FO40,40^A0N,40,40^FDRF Inventario OK^FS^XZ") }
                        snackbar.showSnackbar(r.exceptionOrNull()?.message ?: testOk)
                    }
                },
            ) { Text(stringResource(R.string.settings_printer_test)) }
            HorizontalDivider()

            Button(onClick = {
                scope.launch {
                    app.syncRepository.resetCursor()
                    val r = app.syncRepository.sync(full = true)
                    snackbar.showSnackbar(r.exceptionOrNull()?.message ?: resynced)
                }
            }) { Text(stringResource(R.string.settings_resync)) }

            Text(
                stringResource(R.string.settings_about, BuildConfig.VERSION_NAME, app.managedConfig.apiBaseUrl),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
