package com.rf.inventario.scanner

import com.rf.inventario.data.Location
import com.rf.inventario.data.Product

/** Qué es lo que se acaba de escanear. */
sealed interface ScanTarget {
    val raw: String

    data class IsProduct(override val raw: String, val product: Product) : ScanTarget
    data class IsLocation(override val raw: String, val location: Location) : ScanTarget

    /** No está en el caché: puede ser un producto nuevo, o uno que el caché todavía no tiene. */
    data class Unknown(override val raw: String) : ScanTarget
}

/**
 * Decide si una lectura es la etiqueta de una ubicación o un producto, mirando
 * el caché local (funciona offline). Así el operario escanea estante →
 * producto → estante sin tocar la pantalla para decir "esto es una ubicación".
 *
 * Las ubicaciones se miran primero: sus códigos (A-01-03) no chocan con EAN.
 * Si un código existiera como ambas cosas, gana la ubicación y el producto
 * sigue siendo accesible tecleando el código o desde la ficha.
 */
class ScanRouter(
    private val findLocation: suspend (String) -> Location?,
    private val findProduct: suspend (String) -> Product?,
) {
    suspend fun route(raw: String): ScanTarget {
        val code = clean(raw)
        findLocation(code)?.let { return ScanTarget.IsLocation(code, it) }
        findProduct(code)?.let { return ScanTarget.IsProduct(code, it) }
        return ScanTarget.Unknown(code)
    }

    companion object {
        /**
         * Limpia lo que agregan algunos lectores: espacios, saltos de línea y el
         * prefijo AIM de simbología (`]C1`, `]E0`...) cuando está activado.
         */
        fun clean(raw: String): String {
            val trimmed = raw.trim().trim('\u0000', '\r', '\n', '\t')
            return if (trimmed.length > 3 && trimmed[0] == ']' && trimmed[1].isLetter()) trimmed.substring(3) else trimmed
        }
    }
}
