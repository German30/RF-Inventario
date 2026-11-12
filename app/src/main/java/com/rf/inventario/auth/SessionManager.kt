package com.rf.inventario.auth

import android.content.Context
import com.rf.inventario.data.AppUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistencia de la sesión: operario + token JWT del backend.
 * Nunca se guarda el PIN.
 */
interface SessionStore {
    fun persist(user: AppUser?, token: String?)
    fun restoreUser(): AppUser?
    fun restoreToken(): String?
}

/** Implementación real sobre SharedPreferences, con el token cifrado por el Keystore. */
class PrefsSessionStore(context: Context, private val cipher: TokenCipher = TokenCipher()) : SessionStore {

    private val prefs = context.getSharedPreferences("rf_session", Context.MODE_PRIVATE)

    override fun persist(user: AppUser?, token: String?) {
        prefs.edit().apply {
            if (user == null) {
                clear()
            } else {
                putString(KEY_CODE, user.code)
                putString(KEY_NAME, user.name)
                putString(KEY_ROLE, user.role)
                putString(KEY_TOKEN, token?.let(cipher::encrypt))
            }
        }.apply()
    }

    override fun restoreUser(): AppUser? {
        val code = prefs.getString(KEY_CODE, null) ?: return null
        // Sin token legible no hay sesión que restaurar (p.ej. venía de una versión
        // que lo guardaba en texto plano): mejor pedir login que fallar con 401.
        if (restoreToken() == null) return null
        return AppUser(
            code = code,
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            role = prefs.getString(KEY_ROLE, AppUser.ROLE_OPERATOR) ?: AppUser.ROLE_OPERATOR,
        )
    }

    override fun restoreToken(): String? = prefs.getString(KEY_TOKEN, null)?.let(cipher::decrypt)

    private companion object {
        const val KEY_CODE = "code"
        const val KEY_NAME = "name"
        const val KEY_ROLE = "role"
        const val KEY_TOKEN = "token_enc"
    }
}

/** Por qué terminó la última sesión, para explicarlo en el login. */
enum class LogoutReason { MANUAL, EXPIRED, IDLE }

/**
 * Estado de sesión observable. La UI muestra login mientras [current] sea null.
 * [token] lo usa el interceptor de Retrofit en cada petición.
 */
class SessionManager(
    private val store: SessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _current = MutableStateFlow(store.restoreUser())
    val current: StateFlow<AppUser?> = _current.asStateFlow()

    private val _lastLogoutReason = MutableStateFlow<LogoutReason?>(null)
    val lastLogoutReason: StateFlow<LogoutReason?> = _lastLogoutReason.asStateFlow()

    @Volatile
    var token: String? = store.restoreToken()
        private set

    @Volatile
    private var lastInteraction: Long = clock()

    fun login(user: AppUser, token: String) {
        store.persist(user, token)
        this.token = token
        lastInteraction = clock()
        _lastLogoutReason.value = null
        _current.value = user
    }

    fun logout(reason: LogoutReason = LogoutReason.MANUAL) {
        if (_current.value == null && token == null) return
        store.persist(null, null)
        token = null
        _lastLogoutReason.value = reason
        _current.value = null
    }

    /** La activity lo llama en cada toque/tecla/escaneo. */
    fun touch() {
        lastInteraction = clock()
    }

    /**
     * Cierra la sesión si pasaron más de [timeoutMs] sin interacción: en una
     * terminal compartida, el siguiente operario no hereda la sesión del anterior.
     * @return true si cerró la sesión.
     */
    fun logoutIfIdle(timeoutMs: Long): Boolean {
        if (timeoutMs <= 0 || _current.value == null) return false
        if (clock() - lastInteraction < timeoutMs) return false
        logout(LogoutReason.IDLE)
        return true
    }
}
