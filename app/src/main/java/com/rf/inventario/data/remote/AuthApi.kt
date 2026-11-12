package com.rf.inventario.data.remote

import com.rf.inventario.auth.BadCredentialsException
import com.rf.inventario.data.ApiException
import com.rf.inventario.data.AppUser
import com.rf.inventario.data.NetworkException
import com.rf.inventario.data.remote.dto.LoginRequestDto
import retrofit2.HttpException
import java.io.IOException

/** Sesión recién obtenida del backend: operario + token JWT. */
data class AuthSession(
    val user: AppUser,
    val token: String,
    val expiraEnSegundos: Long,
)

/** Autenticación contra el backend. Aislada en una interfaz para poder testear. */
interface AuthApi {
    /**
     * @throws BadCredentialsException si el backend responde 401
     * @throws ApiException en cualquier otro fallo (red, 5xx, ...)
     */
    suspend fun login(codigo: String, pin: String): AuthSession
}

class RetrofitAuthApi(private val api: PanelApi) : AuthApi {

    override suspend fun login(codigo: String, pin: String): AuthSession {
        val dto = try {
            api.login(LoginRequestDto(codigo.trim(), pin.trim()))
        } catch (e: HttpException) {
            if (e.code() == 401) throw BadCredentialsException()
            throw ApiException(mensajeDe(e), e)
        } catch (e: IOException) {
            throw NetworkException(cause = e)
        }

        val token = dto.token
            ?: throw ApiException("El servidor no devolvió un token de sesión")

        return AuthSession(
            user = AppUser(code = dto.codigo, name = dto.nombre, role = dto.rol ?: AppUser.ROLE_OPERATOR),
            token = token,
            expiraEnSegundos = dto.expiraEn ?: 0L,
        )
    }
}
