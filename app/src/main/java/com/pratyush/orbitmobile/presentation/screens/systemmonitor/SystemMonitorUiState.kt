package com.pratyush.orbitmobile.presentation.screens.systemmonitor

import com.pratyush.orbitmobile.domain.model.CpuStats
import com.pratyush.orbitmobile.domain.model.GpuStats
import com.pratyush.orbitmobile.domain.model.RamStats
import com.pratyush.orbitmobile.domain.model.TelemetryFreshness
import com.pratyush.orbitmobile.domain.model.WifiStats

/**
 * UI State for the redesigned System Monitor screen.
 */
data class SystemMonitorUiState(
    val cpu: CpuStats? = null,
    val ram: RamStats? = null,
    val wifi: WifiStats? = null,
    val gpu: GpuStats? = null,
    val freshness: TelemetryFreshness = TelemetryFreshness.OFFLINE,
    val cpuHistory: List<Float> = emptyList(),
    val ramHistory: List<Float> = emptyList(),
    val wifiDownloadHistory: List<Double> = emptyList(),
    val wifiUploadHistory: List<Double> = emptyList(),
    val gpuHistory: List<Float> = emptyList()
)
