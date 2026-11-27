package com.pratyush.orbitmobile.domain.model

/**
 * Real-time CPU telemetry snapshot from paired Windows host.
 */
data class CpuStats(
    val name: String,
    val utilizationPercent: Float,
    val cores: Int,
    val threads: Int,
    val temperatureCelsius: Float?
)

/**
 * Real-time RAM memory telemetry snapshot from paired Windows host.
 */
data class RamStats(
    val spec: String,
    val utilizationPercent: Float,
    val usedGb: Float,
    val totalGb: Float,
    val availableGb: Float
)

/**
 * Real-time Wi-Fi and network throughput telemetry snapshot.
 */
data class WifiStats(
    val name: String,
    val downloadSpeedBps: Double,
    val uploadSpeedBps: Double,
    val signalDbm: Int?,
    val latencyMs: Int?
)

/**
 * Real-time GPU telemetry snapshot from paired Windows host.
 */
data class GpuStats(
    val name: String,
    val utilizationPercent: Float,
    val vramMb: Long?,
    val temperatureCelsius: Float?
)

/**
 * Combined live hardware telemetry payload.
 */
data class SystemTelemetry(
    val cpu: CpuStats,
    val ram: RamStats,
    val wifi: WifiStats,
    val gpu: GpuStats,
    val timestampMs: Long
)

/**
 * Telemetry freshness states reflecting live data arrival rather than mere socket existence.
 */
enum class TelemetryFreshness {
    LIVE,
    STALE,
    OFFLINE
}

/**
 * Consolidated System Monitor data package with bounded historical samples for live graphs.
 */
data class SystemMonitorData(
    val telemetry: SystemTelemetry?,
    val freshness: TelemetryFreshness,
    val cpuHistory: List<Float> = emptyList(),
    val ramHistory: List<Float> = emptyList(),
    val wifiDownloadHistory: List<Double> = emptyList(),
    val wifiUploadHistory: List<Double> = emptyList(),
    val gpuHistory: List<Float> = emptyList()
)
