package com.rf.inventario.config

import android.content.Context
import android.content.RestrictionsManager
import com.rf.inventario.BuildConfig

/**
 * Configuración administrada por el MDM (Android Enterprise "App Restrictions"),
 * declarada en `res/xml/app_restrictions.xml`. Si el dispositivo no está
 * inscripto en un MDM, o el admin no seteó nada, cae al valor con el que se
 * compiló el APK ([BuildConfig.API_BASE_URL]).
 *
 * Se lee una sola vez al arrancar la app; un cambio que empuje el MDM en
 * caliente se aplica recién en el próximo inicio (comportamiento estándar de
 * la mayoría de las apps que consumen App Restrictions).
 */
class ManagedConfig(context: Context) {

    private val restrictions = (context.applicationContext.getSystemService(Context.RESTRICTIONS_SERVICE)
        as? RestrictionsManager)
        ?.applicationRestrictions

    /** URL base del backend, con `/` final (lo exige Retrofit). */
    val apiBaseUrl: String by lazy {
        val fromMdm = restrictions?.getString(KEY_API_BASE_URL)?.trim()
        val url = fromMdm?.takeIf { it.isNotEmpty() } ?: BuildConfig.API_BASE_URL
        if (url.endsWith("/")) url else "$url/"
    }

    /** Minutos sin uso tras los que se cierra la sesión; null = lo decide el operario en Ajustes. */
    val idleTimeoutMinutes: Int? by lazy {
        restrictions?.getInt(KEY_IDLE_TIMEOUT, 0)?.takeIf { it > 0 }
    }

    /** IP/host de la impresora Zebra de la zona; null = la configura el operario. */
    val printerHost: String? by lazy {
        restrictions?.getString(KEY_PRINTER_HOST)?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val KEY_API_BASE_URL = "apiBaseUrl"
        const val KEY_IDLE_TIMEOUT = "idleTimeoutMinutes"
        const val KEY_PRINTER_HOST = "printerHost"
    }
}
