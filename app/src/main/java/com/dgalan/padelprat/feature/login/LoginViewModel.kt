package com.dgalan.padelprat.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgalan.padelprat.data.auth.AuthRepository
import com.dgalan.padelprat.data.auth.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginError { InvalidCredentials, Network, Unknown }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: LoginError? = null,
    /** La sesión anterior caducó: se explica al usuario por qué tiene que volver a entrar. */
    val isSessionExpired: Boolean = false
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}

/**
 * Si el login va bien no hace falta navegar desde aquí: la sesión pasa a existir y la navegación raíz cambia sola
 * a la pantalla principal.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionExpired.collect { expired ->
                _uiState.update { it.copy(isSessionExpired = expired) }
            }
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onLoginClick() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val error = when (authRepository.login(state.email, state.password)) {
                LoginResult.Success -> null
                LoginResult.InvalidCredentials -> LoginError.InvalidCredentials
                LoginResult.NetworkError -> LoginError.Network
                LoginResult.UnknownError -> LoginError.Unknown
            }
            _uiState.update { it.copy(isLoading = false, error = error) }
        }
    }
}
