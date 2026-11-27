package com.pratyush.orbitmobile.data.repository.implementations

import android.content.Context
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.Device
import com.pratyush.orbitmobile.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Production repository implementation for devices.
 * Persists paired devices locally on disk across application restarts.
 * No dummy or simulated devices.
 */
class DeviceRepositoryImpl(
    private val context: Context? = null
) : DeviceRepository {

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    private val _pairedDevices = MutableStateFlow<List<Device>>(emptyList())

    private val storageFile: File? by lazy {
        context?.let { File(it.filesDir, "paired_devices.json") }
    }

    init {
        loadFromDisk()
    }

    override fun getDiscoveredDevices(): Flow<List<Device>> = _discoveredDevices.asStateFlow()

    override fun getPairedDevices(): Flow<List<Device>> = _pairedDevices.asStateFlow()

    override suspend fun saveDevice(device: Device): OrbitResult<Unit> {
        _pairedDevices.update { current ->
            val filtered = current.filterNot { it.id == device.id }
            filtered + device.copy(isPaired = true)
        }
        persistToDisk()
        return OrbitResult.Success(Unit)
    }

    override suspend fun removeDevice(deviceId: String): OrbitResult<Unit> {
        _pairedDevices.update { current ->
            current.filterNot { it.id == deviceId }
        }
        persistToDisk()
        return OrbitResult.Success(Unit)
    }

    override suspend fun triggerDiscovery(): OrbitResult<Unit> {
        // Discovered devices are updated through real network mDNS / UDP probes
        return OrbitResult.Success(Unit)
    }

    fun updateDiscovered(devices: List<Device>) {
        _discoveredDevices.value = devices
    }

    private fun loadFromDisk() {
        try {
            val file = storageFile ?: return
            if (!file.exists()) return

            val jsonStr = file.readText()
            if (jsonStr.isBlank()) return

            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<Device>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                // Discard any legacy dummy test devices
                if (id == "device-zenbook-pro" || id == "device-studio-rig" || id == "device-orbit-surface") {
                    continue
                }

                list.add(
                    Device(
                        id = id,
                        name = obj.optString("name", "ORBIT Windows PC"),
                        ipAddress = obj.optString("ipAddress", ""),
                        port = obj.optInt("port", 8765),
                        isPaired = true,
                        lastConnectedText = obj.optString("lastConnectedText", "Previously connected"),
                        osInfo = obj.optString("osInfo", "Windows 11"),
                        isOnline = obj.optBoolean("isOnline", false)
                    )
                )
            }

            _pairedDevices.value = list
        } catch (e: Exception) {
            // Discard corrupted file
        }
    }

    private fun persistToDisk() {
        try {
            val file = storageFile ?: return
            val jsonArray = JSONArray()

            for (device in _pairedDevices.value) {
                val obj = JSONObject().apply {
                    put("id", device.id)
                    put("name", device.name)
                    put("ipAddress", device.ipAddress)
                    put("port", device.port)
                    put("isPaired", device.isPaired)
                    put("lastConnectedText", device.lastConnectedText)
                    put("osInfo", device.osInfo)
                    put("isOnline", device.isOnline)
                }
                jsonArray.put(obj)
            }

            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            // Log/ignore
        }
    }
}
