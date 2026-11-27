package com.pratyush.orbitmobile.core.platform

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

data class DeviceTelemetryData(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val batteryStatusText: String,
    val batteryHealth: String? = "Good",
    val batteryTemperature: String? = null,
    val chargeSource: String? = null,
    val wifiConnected: Boolean,
    val wifiSsid: String?,
    val wifiFrequency: String?,
    val wifiLinkSpeed: String? = null,
    val bluetoothEnabled: Boolean,
    val bluetoothConnected: Boolean,
    val bluetoothStatusText: String,
    val usedStorageGb: Double = 0.0,
    val totalStorageGb: Double = 0.0,
    val manufacturer: String? = null
)

object TelemetryCollector {

    fun collect(context: Context): DeviceTelemetryData {
        // 1. Battery Telemetry
        var batteryPercent = 0
        var isCharging = false
        var batteryStatusText = "Unavailable"
        var batteryHealth: String? = "Good"
        var batteryTemperature: String? = null
        var chargeSource: String? = null

        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (capacity in 0..100) {
                batteryPercent = capacity
            }

            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val bIntent = context.registerReceiver(null, ifilter)
            if (bIntent != null) {
                val status = bIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val chargePlug = bIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                chargeSource = when (chargePlug) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> if (isCharging) "Charger" else "None"
                }

                val healthVal = bIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                batteryHealth = when (healthVal) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    else -> "Good"
                }

                val tempRaw = bIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                if (tempRaw > 0) {
                    batteryTemperature = "${tempRaw / 10} \u00B0C"
                }

                batteryStatusText = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Not charging"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                    else -> if (isCharging) "Charging" else "Not charging"
                }

                if (batteryPercent == 0) {
                    val level = bIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = bIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        batteryPercent = (level * 100) / scale
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        // 2. Wi-Fi Telemetry
        var wifiConnected = false
        var wifiSsid: String? = null
        var wifiFrequency: String? = null
        var wifiLinkSpeed: String? = null

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNetwork)
            wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            if (wifiConnected) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    (caps?.transportInfo as? WifiInfo) ?: wm?.connectionInfo
                } else {
                    wm?.connectionInfo
                }

                val rawSsid = wifiInfo?.ssid?.trim('"')
                wifiSsid = if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                    rawSsid
                } else null

                val freq = wifiInfo?.frequency ?: 0
                wifiFrequency = when {
                    freq >= 5925 -> "6 GHz"
                    freq >= 4900 -> "5 GHz"
                    freq >= 2400 -> "2.4 GHz"
                    else -> null
                }

                val speed = wifiInfo?.linkSpeed ?: -1
                if (speed > 0) {
                    wifiLinkSpeed = "$speed Mbps"
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        // 3. Bluetooth Telemetry
        var btEnabled = false
        var btConnected = false
        var btStatusText = "Disabled"

        try {
            val btm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btm?.adapter
            btEnabled = adapter?.isEnabled == true
            btConnected = btEnabled // Active adapter state
            btStatusText = if (btEnabled) "Connected" else "Disabled"
        } catch (e: Exception) {
            // fallback
        }

        // 4. Storage Telemetry (Real Android StatFs)
        var usedStorageGb = 0.0
        var totalStorageGb = 0.0
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes
            val bytesInGb = 1024.0 * 1024.0 * 1024.0
            usedStorageGb = kotlin.math.round((usedBytes.toDouble() / bytesInGb) * 10.0) / 10.0
            totalStorageGb = kotlin.math.round((totalBytes.toDouble() / bytesInGb) * 10.0) / 10.0
        } catch (e: Exception) {
            // fallback
        }

        val manufacturer = Build.MANUFACTURER ?: "Android"

        return DeviceTelemetryData(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            batteryStatusText = batteryStatusText,
            batteryHealth = batteryHealth,
            batteryTemperature = batteryTemperature,
            chargeSource = chargeSource,
            wifiConnected = wifiConnected,
            wifiSsid = wifiSsid,
            wifiFrequency = wifiFrequency,
            wifiLinkSpeed = wifiLinkSpeed,
            bluetoothEnabled = btEnabled,
            bluetoothConnected = btConnected,
            bluetoothStatusText = btStatusText,
            usedStorageGb = usedStorageGb,
            totalStorageGb = totalStorageGb,
            manufacturer = manufacturer
        )
    }
}
