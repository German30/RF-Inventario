package com.rf.inventario.data

/** Operario autenticado. Nunca lleva el PIN. */
data class AppUser(
    val code: String,
    val name: String,
    /** OPERARIO o SUPERVISOR (lo decide el backend; acá solo adapta la UI). */
    val role: String = ROLE_OPERATOR,
) {
    /** Puede aprobar conteos, borrar productos y gestionar ubicaciones. El backend lo vuelve a validar. */
    val isSupervisor: Boolean get() = role == ROLE_SUPERVISOR

    companion object {
        const val ROLE_OPERATOR = "OPERARIO"
        const val ROLE_SUPERVISOR = "SUPERVISOR"
    }
}
