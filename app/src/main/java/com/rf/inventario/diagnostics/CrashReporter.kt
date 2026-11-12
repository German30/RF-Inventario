package com.rf.inventario.diagnostics

import android.content.Context
import android.os.Build
import com.rf.inventario.BuildConfig
import com.rf.inventario.data.remote.PanelApi
import com.rf.inventario.data.remote.dto.ErrorClienteDto
import java.io.File

/**
 * Crash reporting autohospedado: el crash se guarda en disco al ocurrir (en
 * ese momento no hay red ni tiempo) y se manda al backend en el próximo
 * arranque con sesión, que lo escribe en su log. Sin SDKs de terceros: los
 * datos del depósito no salen de la infraestructura del cliente.
 */
class CrashReporter(context: Context) {

    private val file = File(context.filesDir, "last_crash.txt")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(
                    "${System.currentTimeMillis()}\n${error::class.java.name}: ${error.message}\n" +
                        error.stackTraceToString().take(18_000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Llamar con sesión iniciada; si no hay red, queda para la próxima. */
    suspend fun uploadPending(api: PanelApi) {
        if (!file.exists()) return
        val lines = runCatching { file.readLines() }.getOrNull() ?: return
        val dto = ErrorClienteDto(
            mensaje = lines.getOrNull(1)?.take(500) ?: "crash",
            stack = lines.drop(2).joinToString("\n"),
            version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            dispositivo = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
            ocurridoEn = lines.firstOrNull()?.toLongOrNull(),
        )
        val ok = runCatching { api.reportarError(dto).isSuccessful }.getOrDefault(false)
        if (ok) file.delete()
    }
}
