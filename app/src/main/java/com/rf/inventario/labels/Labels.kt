package com.rf.inventario.labels

import com.rf.inventario.data.Location
import com.rf.inventario.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Etiquetas en ZPL II (impresoras Zebra, el estándar de facto en depósitos).
 * Pensadas para rollo de 2" x 1" (406 x 203 dots a 203 dpi).
 */
object Zpl {

    fun product(p: Product, copies: Int = 1): String = buildString {
        append("^XA^CI28^PW406^LL203")
        append("^FO16,12^A0N,26,26^FB374,2,0,L^FD").append(escape(p.name)).append("^FS")
        append("^FO16,72^BY2^BCN,70,Y,N,N^FD").append(escape(p.sku)).append("^FS")
        if (p.location.isNotBlank()) {
            append("^FO300,12^A0N,18,18^FD").append(escape(p.location)).append("^FS")
        }
        append("^PQ").append(copies.coerceIn(1, 999))
        append("^XZ")
    }

    fun location(l: Location, copies: Int = 1): String = buildString {
        append("^XA^CI28^PW406^LL203")
        append("^FO16,10^A0N,40,40^FD").append(escape(l.code)).append("^FS")
        if (l.description.isNotBlank()) {
            append("^FO16,54^A0N,20,20^FB374,1,0,L^FD").append(escape(l.description)).append("^FS")
        }
        append("^FO40,82^BY2^BCN,90,N,N,N^FD").append(escape(l.code)).append("^FS")
        append("^PQ").append(copies.coerceIn(1, 999))
        append("^XZ")
    }

    /** `^` y `~` son comandos ZPL: dentro de un campo se reemplazan para no romper la etiqueta. */
    internal fun escape(text: String): String = text.replace('^', ' ').replace('~', '-')
}

/** Envía ZPL crudo por TCP al puerto 9100 (RAW) de la impresora. */
object LabelPrinter {
    suspend fun print(host: String, port: Int, zpl: String) = withContext(Dispatchers.IO) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 4_000)
            socket.soTimeout = 4_000
            socket.getOutputStream().apply {
                write(zpl.toByteArray(Charsets.UTF_8))
                flush()
            }
        }
    }
}
