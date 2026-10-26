package com.dgalan.padelprat.feature.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgalan.padelprat.BuildConfig
import com.dgalan.padelprat.data.scheduled.ScheduledRunRegistry
import com.dgalan.padelprat.data.update.AppInstaller
import com.dgalan.padelprat.data.update.AppUpdate
import com.dgalan.padelprat.data.update.InstallEvent
import com.dgalan.padelprat.data.update.UpdateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UpdateStep {
    data object Ready : UpdateStep

    data class Downloading(val percent: Int) : UpdateStep

    /** Esperando al diálogo del sistema o a que Android termine de instalar. */
    data object Installing : UpdateStep
}

enum class UpdateError { Download, Install }

data class ForceUpdateUiState(
    val currentVersion: String,
    val newVersion: String,
    val step: UpdateStep = UpdateStep.Ready,
    val error: UpdateError? = null,
    /** Instalar cierra la app y cortaría la reserva programada que se está haciendo. */
    val isScheduledBookingRunning: Boolean = false,
    /** Diálogo del sistema para confirmar la instalación, pendiente de abrir. */
    val confirmation: Intent? = null
) {
    val canUpdate: Boolean get() = step == UpdateStep.Ready && !isScheduledBookingRunning
}

@HiltViewModel(assistedFactory = ForceUpdateViewModel.Factory::class)
class ForceUpdateViewModel @AssistedInject constructor(
    @Assisted private val update: AppUpdate,
    private val repository: UpdateRepository,
    private val installer: AppInstaller,
    runRegistry: ScheduledRunRegistry
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(update: AppUpdate): ForceUpdateViewModel
    }

    private val _uiState = MutableStateFlow(
        ForceUpdateUiState(currentVersion = BuildConfig.VERSION_NAME, newVersion = update.version)
    )
    val uiState: StateFlow<ForceUpdateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runRegistry.runningIds.collect { ids ->
                _uiState.update { it.copy(isScheduledBookingRunning = ids.isNotEmpty()) }
            }
        }
        viewModelScope.launch {
            installer.events.collect { event ->
                _uiState.update {
                    when (event) {
                        is InstallEvent.NeedsConfirmation -> it.copy(confirmation = event.intent)
                        InstallEvent.Cancelled -> it.copy(step = UpdateStep.Ready)
                        InstallEvent.Failed -> it.copy(step = UpdateStep.Ready, error = UpdateError.Install)
                    }
                }
            }
        }
    }

    /** Se llama con el permiso de instalar ya concedido. */
    fun onUpdateClick() {
        if (!_uiState.value.canUpdate) return
        _uiState.update { it.copy(step = UpdateStep.Downloading(0), error = null) }
        viewModelScope.launch {
            val apk = repository.download(update) { progress ->
                val percent = (progress * 100).roundToInt()
                _uiState.update { state ->
                    if (state.step is UpdateStep.Downloading) {
                        state.copy(
                            step = UpdateStep.Downloading(percent)
                        )
                    } else {
                        state
                    }
                }
            }
            if (apk == null) {
                _uiState.update { it.copy(step = UpdateStep.Ready, error = UpdateError.Download) }
                return@launch
            }
            _uiState.update { it.copy(step = UpdateStep.Installing) }
            if (!installer.install(apk)) {
                _uiState.update { it.copy(step = UpdateStep.Ready, error = UpdateError.Install) }
            }
        }
    }

    fun onConfirmationShown() {
        _uiState.update { it.copy(confirmation = null) }
    }
}
