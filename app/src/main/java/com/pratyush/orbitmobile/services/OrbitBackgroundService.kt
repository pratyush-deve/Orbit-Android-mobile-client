package com.pratyush.orbitmobile.services

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pratyush.orbitmobile.MainActivity
import com.pratyush.orbitmobile.OrbitMobileApp
import com.pratyush.orbitmobile.R
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.core.platform.TelemetryCollector
import com.pratyush.orbitmobile.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.random.Random

/**
 * Android Foreground Service maintaining persistent LAN telemetry and connectivity
 * to ORBIT Windows even when the application UI is closed or backgrounded.
 */
class OrbitBackgroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var connectedDeviceName: String = "PC"
    private var currentTitle: String = "ORBIT connected to PC"
    private var currentContent: String = "Maintaining live telemetry & sync"
    private var telemetryLoopJob: Job? = null
    private var debounceJob: Job? = null

    private var batteryReceiver: BroadcastReceiver? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var debounceWakeLock: PowerManager.WakeLock? = null

    private val isRunning = AtomicBoolean(false)
    private var consecutiveFailures = 0

    // Telemetry concurrency guard, coalescing flag and exponential backoff
    private val telemetryMutex = Mutex()
    private val hasPendingTelemetry = AtomicBoolean(false)
    private var telemetryBackoffMs: Long = 0L

    // Notification rate limiting to prevent system shedding
    private var lastNotificationTitle: String = ""
    private var lastNotificationContent: String = ""
    private var lastNotificationTime: Long = 0L
    private val notificationLock = Any()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isServiceRunning.set(true)
        Log.i(TAG, "OrbitBackgroundService created.")
        createNotificationChannel()
        checkBatteryOptimizationStatus()
    }

    private fun checkBatteryOptimizationStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val ignoring = pm?.isIgnoringBatteryOptimizations(packageName) ?: false
            Log.i(TAG, "Battery optimization whitelist status for $packageName: isIgnoring=$ignoring")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_DISCONNECT -> {
                Log.i(TAG, "Disconnect action received from notification or UI.")
                handleDisconnect()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stop action received.")
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_SYNC_NOW -> {
                scheduleDebouncedTelemetry(immediate = true)
                return START_STICKY
            }
            ACTION_START -> {
                val devName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "PC"
                connectedDeviceName = devName
                currentTitle = "ORBIT connected to $connectedDeviceName"
                currentContent = "Maintaining live telemetry & sync"
                Log.i(TAG, "Starting foreground service for device: $connectedDeviceName")

                val started = startForegroundNotification()
                if (!started) {
                    Log.w(TAG, "Foreground notification could not be started. Aborting background service.")
                    return START_NOT_STICKY
                }

                if (!isRunning.getAndSet(true)) {
                    registerTelemetryReceivers()
                    startTelemetryMonitoring()
                    observeConnectionState()
                } else {
                    updateNotification()
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundNotification(): Boolean {
        return try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground service with type connectedDevice: ${e.message}", e)
            stopForegroundService()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting foreground service: ${e.message}", e)
            stopForegroundService()
            false
        }
    }

    private fun updateNotification(
        title: String? = null,
        content: String? = null,
        force: Boolean = false
    ) {
        synchronized(notificationLock) {
            val newTitle = title ?: currentTitle
            val newContent = content ?: currentContent
            val now = System.currentTimeMillis()

            val textChanged = (newTitle != lastNotificationTitle || newContent != lastNotificationContent)
            val timeElapsed = (now - lastNotificationTime) >= 3000L

            currentTitle = newTitle
            currentContent = newContent

            // Suppress duplicate or excessive updates unless text changed and at least 3s passed, or explicitly forced
            if (!force && (!textChanged || !timeElapsed)) {
                return
            }

            lastNotificationTitle = newTitle
            lastNotificationContent = newContent
            lastNotificationTime = now

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, OrbitBackgroundService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentContent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(launchPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ORBIT Background Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ORBIT connected to your Windows laptop in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Registers system broadcast receivers and network callbacks for event-driven telemetry.
     */
    private fun registerTelemetryReceivers() {
        // 1. Battery changes (level, charging status, power plugged/unplugged)
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Battery event received: ${intent?.action}")
                scheduleDebouncedTelemetry()
            }
        }
        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, batteryFilter)

        // 2. Bluetooth state changes (enabled / disabled)
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    Log.d(TAG, "Bluetooth state change received.")
                    scheduleDebouncedTelemetry()
                }
            }
        }
        val btFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothReceiver, btFilter)

        // 3. Wi-Fi and network connectivity changes
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Wi-Fi became available. Dispatching fresh telemetry.")
                    scheduleDebouncedTelemetry(immediate = true)
                    checkAndTriggerReconnectIfNecessary()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    // Do NOT schedule debounced telemetry on signal strength / RSSI capability changes.
                    // Doing so triggers a dispatch storm when signal fluctuates.
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "Wi-Fi lost in background.")
                    updateNotification(
                        title = "ORBIT searching for $connectedDeviceName",
                        content = "Waiting for Wi-Fi network...",
                        force = true
                    )
                }
            }
            cm?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback in background service: ${e.message}")
        }
    }

    /**
     * Debounces rapid system events (e.g. rapid battery level or Wi-Fi oscillations)
     * to avoid flooding the local network while ensuring immediate updates.
     * Holds a short-duration PARTIAL_WAKE_LOCK (max 3s) so the debounce delay completes
     * even when the display is off.
     */
    private fun scheduleDebouncedTelemetry(immediate: Boolean = false) {
        if (immediate) {
            debounceJob?.cancel()
            serviceScope.launch {
                sendTelemetryNow()
            }
            return
        }

        try {
            if (debounceWakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                debounceWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "orbit:telemetry_debounce")?.apply {
                    setReferenceCounted(false)
                }
            }
            debounceWakeLock?.acquire(3000L)
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire debounce wake lock: ${e.message}")
        }

        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            try {
                delay(1200) // 1.2s debounce
                sendTelemetryNow()
            } finally {
                try {
                    if (debounceWakeLock?.isHeld == true) {
                        debounceWakeLock?.release()
                    }
                } catch (e: Exception) {
                    // Safe release
                }
            }
        }
    }

    /**
     * Periodic telemetry ticker:
     * Dispatches storage telemetry (StatFs) and periodic keepalive heartbeat every 25 seconds,
     * or backs off exponentially when failures occur.
     * Operates alongside AlarmManager so that telemetry dispatches continue reliably
     * even when the display is off and CPU is in deep sleep.
     */
    private fun startTelemetryMonitoring() {
        telemetryLoopJob?.cancel()
        // Arm the first wakeup alarm
        scheduleNextTelemetryAlarm(TELEMETRY_INTERVAL_MS)

        // Perform initial send
        serviceScope.launch {
            sendTelemetryNow()
        }

        telemetryLoopJob = serviceScope.launch {
            while (isActive) {
                val waitDelay = if (telemetryBackoffMs > 0L) telemetryBackoffMs else TELEMETRY_INTERVAL_MS
                delay(waitDelay)
                val app = application as? OrbitMobileApp ?: continue
                val state = app.appContainer.connectionRepository.connectionState.value
                if (state is ConnectionState.Connected) {
                    sendTelemetryNow()
                }
            }
        }
    }

    /**
     * Serializes telemetry dispatch with a Mutex and coalesces concurrent triggers.
     * If a telemetry request is already in-flight, marks hasPendingTelemetry so that
     * at most ONE follow-up dispatch runs immediately after completion.
     * Rearms the periodic AlarmManager wakeup upon completion.
     */
    private suspend fun sendTelemetryNow() = withContext(Dispatchers.IO) {
        if (!telemetryMutex.tryLock()) {
            hasPendingTelemetry.set(true)
            Log.d(TAG, "Telemetry dispatch in progress; queued pending follow-up sync.")
            return@withContext
        }
        try {
            do {
                hasPendingTelemetry.set(false)
                executeTelemetryDispatch()
            } while (hasPendingTelemetry.getAndSet(false) && isActive)
        } finally {
            telemetryMutex.unlock()
            if (isRunning.get()) {
                val nextDelay = if (telemetryBackoffMs > 0L) telemetryBackoffMs else TELEMETRY_INTERVAL_MS
                scheduleNextTelemetryAlarm(nextDelay)
            }
        }
    }

    /**
     * Collects live telemetry from device hardware and dispatches it over authenticated LAN to Windows.
     * Acquires a scoped PARTIAL_WAKE_LOCK and WifiLock for the exact duration of data collection and HTTP I/O.
     * Locks are strictly released in the finally block with a 10s maximum safety timeout.
     */
    private suspend fun executeTelemetryDispatch() {
        val scopedLocks = acquireScopedLocks(10000L)
        val startTime = SystemClock.elapsedRealtime()
        try {
            val app = application as? OrbitMobileApp ?: return
            val connectionRepo = app.appContainer.connectionRepository
            val state = connectionRepo.connectionState.value
            if (state !is ConnectionState.Connected) {
                checkAndTriggerReconnectIfNecessary()
                return
            }

            val telemetry = TelemetryCollector.collect(applicationContext)

            val result = connectionRepo.sendTelemetry(
                batteryPercent = telemetry.batteryPercent,
                isCharging = telemetry.isCharging,
                batteryStatusText = telemetry.batteryStatusText,
                batteryHealth = telemetry.batteryHealth,
                batteryTemperature = telemetry.batteryTemperature,
                chargeSource = telemetry.chargeSource,
                wifiConnected = telemetry.wifiConnected,
                wifiSsid = telemetry.wifiSsid,
                wifiFrequency = telemetry.wifiFrequency,
                wifiLinkSpeed = telemetry.wifiLinkSpeed,
                bluetoothEnabled = telemetry.bluetoothEnabled,
                bluetoothConnected = telemetry.bluetoothConnected,
                bluetoothStatusText = telemetry.bluetoothStatusText,
                usedStorageGb = telemetry.usedStorageGb,
                totalStorageGb = telemetry.totalStorageGb,
                manufacturer = telemetry.manufacturer
            )

            val elapsed = SystemClock.elapsedRealtime() - startTime
            if (result is OrbitResult.Success) {
                consecutiveFailures = 0
                telemetryBackoffMs = 0L
                Log.d(TAG, "Background telemetry delivered successfully (${elapsed}ms): Battery ${telemetry.batteryPercent}%, Wi-Fi: ${telemetry.wifiSsid}, Storage: ${telemetry.usedStorageGb}GB")
            } else if (result is OrbitResult.Error) {
                consecutiveFailures++
                val baseDelay = (5000L * (1L shl min(consecutiveFailures, 4))).coerceAtMost(60000L)
                val jitter = Random.nextLong(0, 3000L)
                telemetryBackoffMs = baseDelay + jitter
                Log.w(TAG, "Background telemetry dispatch failure ($consecutiveFailures) after ${elapsed}ms. Backoff: ${telemetryBackoffMs}ms. Error: ${result.exception.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting/transmitting background telemetry: ${e.message}")
        } finally {
            scopedLocks.close()
        }
    }

    private fun acquireScopedLocks(timeoutMs: Long): ScopedLocks {
        var wakeLock: PowerManager.WakeLock? = null
        var wifiLock: WifiManager.WifiLock? = null

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "orbit:telemetry_dispatch")?.apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire PARTIAL_WAKE_LOCK: ${e.message}")
        }

        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            // Evaluate Wi-Fi lock independently:
            // Do NOT use WIFI_MODE_FULL_LOW_LATENCY as it is inactive when display is off.
            // Use WIFI_MODE_FULL_HIGH_PERF (or legacy WIFI_MODE_FULL) for the brief duration of HTTP I/O.
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            } else {
                WifiManager.WIFI_MODE_FULL
            }
            wifiLock = wm?.createWifiLock(mode, "orbit:telemetry_wifi")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WifiLock: ${e.message}")
        }

        return ScopedLocks(wakeLock, wifiLock)
    }

    private class ScopedLocks(
        private val wakeLock: PowerManager.WakeLock?,
        private val wifiLock: WifiManager.WifiLock?
    ) : AutoCloseable {
        override fun close() {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing wake lock: ${e.message}")
            }

            try {
                if (wifiLock?.isHeld == true) {
                    wifiLock.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing Wi-Fi lock: ${e.message}")
            }
        }
    }

    private fun scheduleNextTelemetryAlarm(delayMs: Long) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, OrbitBackgroundService::class.java).apply {
                action = ACTION_SYNC_NOW
            }
            val pendingIntent = PendingIntent.getService(
                this,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = SystemClock.elapsedRealtime() + delayMs

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule telemetry wakeup alarm: ${e.message}")
        }
    }

    private fun cancelTelemetryAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, OrbitBackgroundService::class.java).apply {
                action = ACTION_SYNC_NOW
            }
            val pendingIntent = PendingIntent.getService(
                this,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel telemetry wakeup alarm: ${e.message}")
        }
    }

    /**
     * If connection drops or network changes, attempt rediscovery and reconnection
     * without needing the user to rescan QR code.
     */
    private fun checkAndTriggerReconnectIfNecessary() {
        serviceScope.launch {
            try {
                val app = application as? OrbitMobileApp ?: return@launch
                val connectionRepo = app.appContainer.connectionRepository
                val state = connectionRepo.connectionState.value

                if (state !is ConnectionState.Connected) {
                    Log.i(TAG, "Triggering automatic background reconnect to paired device...")
                    connectionRepo.attemptReconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background auto-reconnect failed: ${e.message}")
            }
        }
    }

    /**
     * Monitors connection state: updates notification text or shuts down gracefully if disconnected.
     */
    private fun observeConnectionState() {
        serviceScope.launch {
            val app = application as? OrbitMobileApp ?: return@launch
            val connectionRepo = app.appContainer.connectionRepository

            connectionRepo.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        if (state.deviceName.isNotBlank()) {
                            connectedDeviceName = state.deviceName
                        }
                        consecutiveFailures = 0
                        updateNotification(
                            title = "ORBIT connected to $connectedDeviceName",
                            content = "Live telemetry & sync active",
                            force = true
                        )
                    }
                    is ConnectionState.TemporarilyUnavailable -> {
                        updateNotification(
                            title = "ORBIT searching for $connectedDeviceName",
                            content = "Connection paused: ${state.reason}",
                            force = true
                        )
                    }
                    is ConnectionState.Connecting -> {
                        updateNotification(
                            title = "ORBIT connecting to $connectedDeviceName",
                            content = "Negotiating connection..."
                        )
                    }
                    is ConnectionState.Disconnected -> {
                        Log.i(TAG, "Explicit disconnect detected. Stopping background service.")
                        stopForegroundService()
                    }
                    is ConnectionState.Error -> {
                        Log.w(TAG, "Connection in error state: ${state.message}")
                        updateNotification(
                            title = "ORBIT — Reconnecting to $connectedDeviceName",
                            content = state.message,
                            force = true
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun handleDisconnect() {
        serviceScope.launch {
            try {
                val app = application as? OrbitMobileApp
                app?.appContainer?.preferencesManager?.setExplicitlyDisconnected(true)
                app?.appContainer?.connectionRepository?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error during disconnect: ${e.message}")
            } finally {
                stopForegroundService()
            }
        }
    }

    private fun stopForegroundService() {
        isRunning.set(false)
        _isServiceRunning.set(false)
        cancelTelemetryAlarm()
        debounceJob?.cancel()
        try {
            if (debounceWakeLock?.isHeld == true) {
                debounceWakeLock?.release()
            }
        } catch (e: Exception) {
            // Safe cleanup
        }
        debounceWakeLock = null
        telemetryLoopJob?.cancel()
        serviceJob.cancel()

        unregisterTelemetryReceivers()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun unregisterTelemetryReceivers() {
        try {
            batteryReceiver?.let {
                unregisterReceiver(it)
                batteryReceiver = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering battery receiver: ${e.message}")
        }

        try {
            bluetoothReceiver?.let {
                unregisterReceiver(it)
                bluetoothReceiver = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering bluetooth receiver: ${e.message}")
        }

        try {
            networkCallback?.let {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(it)
                networkCallback = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering network callback: ${e.message}")
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "OrbitBackgroundService destroyed.")
        _isServiceRunning.set(false)
        stopForegroundService()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OrbitBackgroundService"
        private val _isServiceRunning = AtomicBoolean(false)
        val isServiceRunning: Boolean get() = _isServiceRunning.get()
        private const val CHANNEL_ID = "orbit_background_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE = 2001
        const val TELEMETRY_INTERVAL_MS = 25000L

        const val ACTION_START = "com.pratyush.orbitmobile.action.START_SERVICE"
        const val ACTION_STOP = "com.pratyush.orbitmobile.action.STOP_SERVICE"
        const val ACTION_DISCONNECT = "com.pratyush.orbitmobile.action.DISCONNECT"
        const val ACTION_SYNC_NOW = "com.pratyush.orbitmobile.action.SYNC_NOW"
        const val EXTRA_DEVICE_NAME = "extra_device_name"

        fun requestImmediateTelemetry(context: Context) {
            try {
                val intent = Intent(context, OrbitBackgroundService::class.java).apply {
                    action = ACTION_SYNC_NOW
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not request immediate telemetry: ${e.message}")
            }
        }

        fun hasRequiredPermissions(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
                val hasFgsConnected = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
                ) == PackageManager.PERMISSION_GRANTED

                // At least one qualifying connected-device permission
                val hasMulticast = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CHANGE_WIFI_MULTICAST_STATE
                ) == PackageManager.PERMISSION_GRANTED

                val hasChangeNetwork = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CHANGE_NETWORK_STATE
                ) == PackageManager.PERMISSION_GRANTED

                val hasBtConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                } else true

                return hasFgsConnected && (hasMulticast || hasChangeNetwork || hasBtConnect)
            }
            return true
        }

        fun start(context: Context, deviceName: String = "PC") {
            try {
                if (_isServiceRunning.get()) {
                    Log.d(TAG, "OrbitBackgroundService is already running.")
                    return
                }

                if (!hasRequiredPermissions(context)) {
                    Log.w(TAG, "Cannot start OrbitBackgroundService: qualifying connectedDevice permissions are not granted.")
                    return
                }

                val intent = Intent(context, OrbitBackgroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_DEVICE_NAME, deviceName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Catches ForegroundServiceStartNotAllowedException on Android 12+ (API 31+) and SecurityException gracefully
                Log.w(TAG, "Foreground service start rejected or not allowed from current execution context: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, OrbitBackgroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop background service: ${e.message}")
            }
        }
    }
}
