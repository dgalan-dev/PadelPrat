package com.dgalan.padelprat.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgalan.padelprat.data.auth.AuthRepository
import com.dgalan.padelprat.data.profile.Profile
import com.dgalan.padelprat.data.profile.ProfileRepository
import com.dgalan.padelprat.data.profile.ProfileResult
import com.dgalan.padelprat.data.reminders.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileError { Network, Unknown }

data class ProfileUiState(
    val profile: Profile? = null,
    val isLoading: Boolean = true,
    /** Solo se muestra si no hay datos que enseñar; si ya había, se siguen viendo. */
    val error: ProfileError? = null,
    /** Avisos activados: 3 h antes del partido y el día anterior para anular sin cargos. */
    val matchReminders: Boolean = true,
    val cancelReminders: Boolean = true
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(profile = profileRepository.profile.value))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.profile.collect { profile -> _uiState.update { it.copy(profile = profile) } }
        }
        viewModelScope.launch {
            reminderRepository.state.collect { state ->
                _uiState.update { it.copy(matchReminders = state.matchEnabled, cancelReminders = state.cancelEnabled) }
            }
        }
        refresh()
    }

    fun onRetry() = refresh()

    fun onMatchRemindersChange(enabled: Boolean) {
        viewModelScope.launch { reminderRepository.setMatchEnabled(enabled) }
    }

    fun onCancelRemindersChange(enabled: Boolean) {
        viewModelScope.launch { reminderRepository.setCancelEnabled(enabled) }
    }

    /** Al borrarse la sesión, la navegación raíz vuelve sola al login. */
    fun onLogoutClick() {
        viewModelScope.launch { authRepository.logout() }
    }

    private fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val error = when (profileRepository.refresh()) {
                is ProfileResult.Success, ProfileResult.SessionExpired -> null
                ProfileResult.NetworkError -> ProfileError.Network
                ProfileResult.UnknownError -> ProfileError.Unknown
            }
            _uiState.update { it.copy(isLoading = false, error = error) }
        }
    }
}
