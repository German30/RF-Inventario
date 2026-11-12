package com.rf.inventario.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferencias del operario en este equipo. Lo que fija el MDM ([ManagedConfig]) gana. */
data class Settings(
    val beep: Boolean = true,
    val vibrate: Boolean = true,
    /** En conteo/recepción/picking, cada escaneo suma 1 sin preguntar cantidad. */
    val scanAddsOne: Boolean = true,
    val idleMinutes: Int = 10,
    val printerHost: String = "",
    val printerPort: Int = 9100,
    /** true si [idleMinutes] / [printerHost] los fija el MDM (la UI los muestra bloqueados). */
    val idleLockedByMdm: Boolean = false,
    val printerLockedByMdm: Boolean = false,
)

class AppSettings(context: Context, private val managed: ManagedConfig) {

    private val prefs = context.applicationContext.getSharedPreferences("rf_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putBoolean("beep", next.beep)
            .putBoolean("vibrate", next.vibrate)
            .putBoolean("scanAddsOne", next.scanAddsOne)
            .putInt("idleMinutes", next.idleMinutes)
            .putString("printerHost", next.printerHost.trim())
            .putInt("printerPort", next.printerPort)
            .apply()
        _settings.value = read()
    }

    private fun read(): Settings {
        val mdmIdle = managed.idleTimeoutMinutes
        val mdmPrinter = managed.printerHost
        return Settings(
            beep = prefs.getBoolean("beep", true),
            vibrate = prefs.getBoolean("vibrate", true),
            scanAddsOne = prefs.getBoolean("scanAddsOne", true),
            idleMinutes = mdmIdle ?: prefs.getInt("idleMinutes", 10),
            printerHost = mdmPrinter ?: prefs.getString("printerHost", "").orEmpty(),
            printerPort = prefs.getInt("printerPort", 9100),
            idleLockedByMdm = mdmIdle != null,
            printerLockedByMdm = mdmPrinter != null,
        )
    }
}
