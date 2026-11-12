package com.rf.inventario.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.rf.inventario.data.Product
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporta el inventario del caché local a CSV y abre el selector de
 * "compartir" (mail, Drive, WhatsApp...). Funciona offline. Separador `;` y
 * BOM para que Excel en español lo abra bien de una.
 */
object CsvExporter {

    fun inventoryCsv(products: List<Product>): String = buildString {
        append('\uFEFF') // BOM: Excel detecta UTF-8
        append("SKU;Nombre;Categoría;Unidad;Cantidad;Mínimo;Bajo mínimo;Costo unitario;Valor;Ubicación;Códigos alternativos\n")
        for (p in products) {
            listOf(
                p.sku, p.name, p.category, p.unit, p.quantity, p.minStock,
                if (p.lowStock) "SI" else "NO",
                "%.2f".format(Locale.ROOT, p.unitCost),
                "%.2f".format(Locale.ROOT, p.unitCost * p.quantity),
                p.location, p.altCodes.joinToString(" "),
            ).joinTo(this, ";") { cell(it) }
            append('\n')
        }
    }

    /** Escapa separadores/comillas y neutraliza fórmulas (CSV injection). */
    internal fun cell(value: Any?): String {
        if (value == null) return ""
        var s = value.toString()
        if (value !is Number && s.isNotEmpty() && s[0] in "=+-@") s = "'$s"
        if (s.contains(';') || s.contains('"') || s.contains('\n')) s = "\"" + s.replace("\"", "\"\"") + "\""
        return s
    }

    fun share(context: Context, products: List<Product>) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
        val file = File(dir, "inventario-$stamp.csv").apply { writeText(inventoryCsv(products)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
