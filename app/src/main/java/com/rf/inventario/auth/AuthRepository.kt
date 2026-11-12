package com.rf.inventario.auth

import com.rf.inventario.data.ApiException
import com.rf.inventario.data.AppUser
import com.rf.inventario.data.remote.AuthApi

/** El backend respondió 401 al login: código o PIN incorrectos. */
class BadCredentialsException : Exception("Código o PIN incorrecto")

class AuthRepository(
    private val auth: AuthApi,
    private val session: SessionManager,
) {
    val currentUser get() = session.current

    suspend fun login(code: String, pin: String): Result<AppUser> = try {
        val sesion = auth.login(code, pin)
        session.login(sesion.user, sesion.token)
        Result.success(sesion.user)
    } catch (e: BadCredentialsException) {
        Result.failure(e)
    } catch (e: ApiException) {
        Result.failure(e)
    }

    fun logout() = session.logout()
}
