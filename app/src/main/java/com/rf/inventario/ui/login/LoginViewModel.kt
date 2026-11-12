package com.rf.inventario.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rf.inventario.InventarioApplication
import com.rf.inventario.auth.AuthRepository
import com.rf.inventario.auth.LogoutReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val code: String = "",
    val pin: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = code.isNotBlank() && pin.length >= 3 && !loading
}

class LoginViewModel(
    private val auth: AuthRepository,
    val logoutReason: StateFlow<LogoutReason?>,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onCodeChange(value: String) {
        if (value.all(Char::isDigit) && value.length <= 12) {
            _state.update { it.copy(code = value, error = null) }
        }
    }

    fun onPinChange(value: String) {
        if (value.all(Char::isDigit) && value.length <= 10) {
            _state.update { it.copy(pin = value, error = null) }
        }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            auth.login(current.code, current.pin)
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Error de acceso") }
                }
            // En éxito, SessionManager.current emite y MainActivity cambia de pantalla.
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as InventarioApplication
                LoginViewModel(app.authRepository, app.sessionManager.lastLogoutReason)
            }
        }
    }
}
