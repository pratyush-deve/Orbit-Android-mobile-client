package com.pratyush.orbitmobile.presentation.screens.systemmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratyush.orbitmobile.domain.repository.SystemMonitorRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel orchestrating live hardware monitoring state and lifecycle management.
 */
class SystemMonitorViewModel(
    private val systemMonitorRepository: SystemMonitorRepository
) : ViewModel() {

    val uiState: StateFlow<SystemMonitorUiState> = systemMonitorRepository.systemMonitorData
        .map { data ->
            SystemMonitorUiState(
                cpu = data.telemetry?.cpu,
                ram = data.telemetry?.ram,
                wifi = data.telemetry?.wifi,
                gpu = data.telemetry?.gpu,
                freshness = data.freshness,
                cpuHistory = data.cpuHistory,
                ramHistory = data.ramHistory,
                wifiDownloadHistory = data.wifiDownloadHistory,
                wifiUploadHistory = data.wifiUploadHistory,
                gpuHistory = data.gpuHistory
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SystemMonitorUiState()
        )

    fun onScreenStarted() {
        systemMonitorRepository.startMonitoring()
    }

    fun onScreenDisposed() {
        systemMonitorRepository.stopMonitoring()
    }

    override fun onCleared() {
        super.onCleared()
        systemMonitorRepository.stopMonitoring()
    }
}
