package com.dgalan.padelprat.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgalan.padelprat.data.auth.AuthRepository
import com.dgalan.padelprat.data.friends.FriendAlertsRepository
import com.dgalan.padelprat.data.friends.FriendAuthRepository
import com.dgalan.padelprat.data.friends.GoogleSignInResult
import com.dgalan.padelprat.data.friends.Membership
import com.dgalan.padelprat.data.profile.Profile
import com.dgalan.padelprat.data.profile.ProfileRepository
import com.dgalan.padelprat.data.profile.ProfileResult
import com.dgalan.padelprat.data.reminders.ReminderRepository
import com.dgalan.padelprat.data.reservations.ReservationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileError { Network, Unknown, WebChanged }

/** Reservas compartidas con el amigo (ver docs/plan-reservas-amigo.md). */
sealed interface SharingState {
    /** Sin sesión de Google: no se comparte nada. */
    data object Off : SharingState

    /** Iniciando sesión o comprobando el alta. */
    data object Loading : SharingState

    /** Con sesión, pero aún sin alta en la consola de Firebase. */
    data class Pending(val email: String?) : SharingState

    data class Active(val email: String?) : SharingState

    /** Con sesión, pero sin poder comprobar el alta (sin conexión). */
    data class Unknown(val email: String?) : SharingState
}

enum class SharingError { NoGoogleAccount, Network, Unknown }

data class ProfileUiState(
    val profile: Profile? = null,
    val isLoading: Boolean = true,
    /** Solo se muestra si no hay datos que enseñar; si ya había, se siguen viendo. */
    val error: ProfileError? = null,
    /** Avisos activados: 3 h antes del partido y el día anterior para anular sin cargos. */
    val matchReminders: Boolean = true,
    val cancelReminders: Boolean = true,
    val sharing: SharingState = SharingState.Off,
    /** Por qué no se ha podido iniciar sesión con Google; se quita al volver a intentarlo. */
    val sharingError: SharingError? = null,
    /** Avisos cuando el amigo reserva o anula. */
    val friendAlerts: Boolean = true
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val reminderRepository: ReminderRepository,
    private val friendAuthRepository: FriendAuthRepository,
    private val reservationsRepository: ReservationsRepository,
    private val friendAlertsRepository: FriendAlertsRepository
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
        viewModelScope.launch {
            friendAlertsRepository.enabled.collect { enabled -> _uiState.update { it.copy(friendAlerts = enabled) } }
        }
        refresh()
        refreshSharing()
    }

    fun onFriendAlertsChange(enabled: Boolean) {
        viewModelScope.launch { friendAlertsRepository.setEnabled(enabled) }
    }

    fun onRetry() = refresh()

    /** [activityContext] tiene que ser la Activity: encima se muestra el selector de cuentas de Google. */
    fun onSharingEnable(activityContext: Context) {
        _uiState.update { it.copy(sharing = SharingState.Loading, sharingError = null) }
        viewModelScope.launch {
            val error = when (friendAuthRepository.signIn(activityContext)) {
                GoogleSignInResult.Success -> null
                GoogleSignInResult.Cancelled -> null
                GoogleSignInResult.NoAccount -> SharingError.NoGoogleAccount
                GoogleSignInResult.NetworkError -> SharingError.Network
                GoogleSignInResult.UnknownError -> SharingError.Unknown
            }
            _uiState.update { it.copy(sharingError = error) }
            refreshSharing(publish = true)
        }
    }

    fun onSharingDisable() {
        viewModelScope.launch {
            friendAuthRepository.signOut()
            _uiState.update { it.copy(sharing = SharingState.Off, sharingError = null) }
        }
    }

    fun onSharingCheck() = refreshSharing(publish = true)

    /** Con [publish], si ya están activas se publican las reservas al momento, sin esperar a abrir Mis reservas. */
    private fun refreshSharing(publish: Boolean = false) {
        if (!friendAuthRepository.isSignedIn) {
            _uiState.update { it.copy(sharing = SharingState.Off) }
            return
        }
        _uiState.update { it.copy(sharing = SharingState.Loading) }
        viewModelScope.launch {
            val email = friendAuthRepository.email
            val sharing = when (friendAuthRepository.membership()) {
                Membership.SignedOut -> SharingState.Off
                Membership.NotMember -> SharingState.Pending(email)
                is Membership.Member -> SharingState.Active(email)
                Membership.Unknown -> SharingState.Unknown(email)
            }
            _uiState.update { it.copy(sharing = sharing) }
            if (publish && sharing is SharingState.Active) {
                // Leer el listado lo publica (ver ReservationsRepository)
                reservationsRepository.refreshActiveCount()
                // Lo que ya tiene el amigo queda como visto: solo se avisará de lo que cambie a partir de ahora
                friendAlertsRepository.check()
            }
        }
    }

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
                ProfileResult.FormatChanged -> ProfileError.WebChanged
            }
            _uiState.update { it.copy(isLoading = false, error = error) }
        }
    }
}
