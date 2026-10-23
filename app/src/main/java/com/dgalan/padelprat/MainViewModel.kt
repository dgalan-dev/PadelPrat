package com.dgalan.padelprat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgalan.padelprat.data.auth.AuthRepository
import com.dgalan.padelprat.data.scheduled.ScheduledBookingRescheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionState {
    /** Leyendo la sesión guardada; mientras tanto se mantiene la pantalla de arranque. */
    data object Loading : SessionState

    data object LoggedIn : SessionState

    data object LoggedOut : SessionState
}

@HiltViewModel
class MainViewModel @Inject constructor(authRepository: AuthRepository, rescheduler: ScheduledBookingRescheduler) :
    ViewModel() {

    init {
        // Al abrir la app se ponen al día las alarmas: cubre que Android las haya borrado (p. ej. al forzar la detención)
        viewModelScope.launch { rescheduler.reconcile() }
    }

    val sessionState: StateFlow<SessionState> = authRepository.isLoggedIn
        .map { loggedIn -> if (loggedIn) SessionState.LoggedIn else SessionState.LoggedOut }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)
}
