package com.rf.inventario

import com.rf.inventario.auth.AuthRepository
import com.rf.inventario.auth.BadCredentialsException
import com.rf.inventario.auth.SessionManager
import com.rf.inventario.auth.SessionStore
import com.rf.inventario.data.ApiException
import com.rf.inventario.data.AppUser
import com.rf.inventario.data.remote.AuthApi
import com.rf.inventario.data.remote.AuthSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    /** Backend falso: solo acepta 1001 / 1234. */
    private class FakeAuthApi : AuthApi {
        override suspend fun login(codigo: String, pin: String): AuthSession {
            if (codigo == "1001" && pin == "1234") {
                return AuthSession(AppUser("1001", "Operario Demo"), token = "jwt-falso", expiraEnSegundos = 43200)
            }
            throw BadCredentialsException()
        }
    }

    private class CaidoAuthApi : AuthApi {
        override suspend fun login(codigo: String, pin: String): AuthSession =
            throw ApiException("Sin conexión con el servidor")
    }

    private class FakeSessionStore : SessionStore {
        var user: AppUser? = null
        var token: String? = null
        override fun persist(user: AppUser?, token: String?) {
            this.user = user
            this.token = token
        }
        override fun restoreUser(): AppUser? = user
        override fun restoreToken(): String? = token
    }

    @Test
    fun `login ok guarda operario y token`() = runTest {
        val store = FakeSessionStore()
        val repo = AuthRepository(FakeAuthApi(), SessionManager(store))

        val result = repo.login("1001", "1234")

        assertTrue(result.isSuccess)
        assertEquals("Operario Demo", store.user?.name)
        assertEquals("jwt-falso", store.token)
    }

    @Test
    fun `login con PIN incorrecto falla y no abre sesion`() = runTest {
        val store = FakeSessionStore()
        val repo = AuthRepository(FakeAuthApi(), SessionManager(store))

        val result = repo.login("1001", "0000")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BadCredentialsException)
        assertNull(store.user)
    }

    @Test
    fun `error de red se propaga como fallo`() = runTest {
        val repo = AuthRepository(CaidoAuthApi(), SessionManager(FakeSessionStore()))

        val result = repo.login("1001", "1234")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiException)
    }

    @Test
    fun `logout limpia la sesion`() = runTest {
        val store = FakeSessionStore()
        val session = SessionManager(store)
        val repo = AuthRepository(FakeAuthApi(), session)

        repo.login("1001", "1234")
        repo.logout()

        assertNull(store.user)
        assertNull(store.token)
        assertNull(session.current.value)
    }
}
