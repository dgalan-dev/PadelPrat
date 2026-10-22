package com.dgalan.padelprat.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgalan.padelprat.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {

    /** Al borrarse la sesión, la navegación raíz vuelve sola al login. */
    fun onLogoutClick() {
        viewModelScope.launch { authRepository.logout() }
    }
}
