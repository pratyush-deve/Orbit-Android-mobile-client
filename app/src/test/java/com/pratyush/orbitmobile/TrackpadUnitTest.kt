package com.pratyush.orbitmobile

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.remote.RemoteDataSource
import com.pratyush.orbitmobile.data.repository.implementations.TrackpadRepositoryImpl
import com.pratyush.orbitmobile.domain.model.ButtonState
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.Device
import com.pratyush.orbitmobile.domain.model.MouseButton
import com.pratyush.orbitmobile.domain.model.TrackpadInputEvent
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.presentation.screens.trackpad.TrackpadGestureListener
import com.pratyush.orbitmobile.presentation.screens.trackpad.TrackpadGestureProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadUnitTest {

    private class RecordingGestureListener : TrackpadGestureListener {
        val moves = mutableListOf<Pair<Int, Int>>()
        val buttons = mutableListOf<Pair<MouseButton, ButtonState>>()
        val scrolls = mutableListOf<Pair<Int, Int>>()
        val zooms = mutableListOf<Double>()
        val dragStates = mutableListOf<Boolean>()

        override fun onMove(dx: Int, dy: Int) {
            moves.add(dx to dy)
        }

        override fun onButton(button: MouseButton, state: ButtonState) {
            buttons.add(button to state)
        }

        override fun onScroll(dx: Int, dy: Int) {
            scrolls.add(dx to dy)
        }

        override fun onZoom(delta: Double) {
            zooms.add(delta)
        }

        override fun onDragStateChanged(isDragging: Boolean) {
            dragStates.add(isDragging)
        }

        fun clear() {
            moves.clear()
            buttons.clear()
            scrolls.clear()
            zooms.clear()
            dragStates.clear()
        }
    }

    @Test
    fun testCursorMovementAndSensitivity() {
        val listener = RecordingGestureListener()
        val processor = TrackpadGestureProcessor(
            sensitivity = 1.0f,
            listener = listener
        )

        // 1. Single finger touch down at (100, 100)
        processor.onPointerDown(id = 1L, x = 100f, y = 100f, timestampMs = 1000L)
        assertEquals(0, listener.moves.size)

        // 2. Relative move to (110, 115) -> delta (10, 15)
        processor.onPointerMove(id = 1L, x = 110f, y = 115f, timestampMs = 1020L)
        assertEquals(1, listener.moves.size)
        assertEquals(10 to 15, listener.moves[0])

        // 3. Lift finger (repositioning)
        processor.onPointerUp(id = 1L, timestampMs = 1030L)

        // 4. Put finger down elsewhere (500, 600) -> Must NOT jump!
        listener.clear()
        processor.onPointerDown(id = 1L, x = 500f, y = 600f, timestampMs = 1100L)
        assertEquals(0, listener.moves.size)

        // Move by (5, -3) from new position
        processor.onPointerMove(id = 1L, x = 505f, y = 597f, timestampMs = 1116L)
        assertEquals(1, listener.moves.size)
        assertEquals(5 to -3, listener.moves[0])

        // 5. Sensitivity test (2.0x)
        listener.clear()
        processor.sensitivity = 2.0f
        processor.onPointerMove(id = 1L, x = 515f, y = 597f, timestampMs = 1132L) // raw dx = 10 -> scaled = 20
        assertEquals(1, listener.moves.size)
        assertEquals(20 to 0, listener.moves[0])
    }

    @Test
    fun testClickAndDragGesture() {
        val listener = RecordingGestureListener()
        val processor = TrackpadGestureProcessor(listener = listener)

        // Touch down and hold stationary past LONG_PRESS_TIMEOUT_MS (400ms)
        processor.onPointerDown(id = 1L, x = 200f, y = 200f, timestampMs = 1000L)
        processor.onPointerMove(id = 1L, x = 200f, y = 200f, timestampMs = 1450L)

        // Should activate drag (Left button DOWN)
        assertTrue(listener.dragStates.contains(true))
        assertTrue(listener.buttons.contains(MouseButton.LEFT to ButtonState.DOWN))

        // Moving finger while dragging moves cursor
        processor.onPointerMove(id = 1L, x = 220f, y = 230f, timestampMs = 1470L)
        assertTrue(listener.moves.isNotEmpty())

        // Finger release ends drag (Left button UP)
        processor.onPointerUp(id = 1L, timestampMs = 1500L)
        assertEquals(MouseButton.LEFT to ButtonState.UP, listener.buttons.last())
        assertFalse(listener.dragStates.last())
    }

    @Test
    fun testSingleTapAndDoubleClick() {
        val listener = RecordingGestureListener()
        val processor = TrackpadGestureProcessor(
            tapToClickEnabled = true,
            listener = listener
        )

        // 1. Quick tap without move -> Left click (Down then Up)
        processor.onPointerDown(id = 1L, x = 150f, y = 150f, timestampMs = 1000L)
        processor.onPointerUp(id = 1L, timestampMs = 1080L)

        assertEquals(2, listener.buttons.size)
        assertEquals(MouseButton.LEFT to ButtonState.DOWN, listener.buttons[0])
        assertEquals(MouseButton.LEFT to ButtonState.UP, listener.buttons[1])

        // 2. Second quick tap within DOUBLE_TAP_TIMEOUT_MS -> Double click
        listener.clear()
        processor.onPointerDown(id = 1L, x = 152f, y = 151f, timestampMs = 1200L)
        processor.onPointerUp(id = 1L, timestampMs = 1260L)

        // Double click produces 4 button events: Down, Up, Down, Up
        assertEquals(4, listener.buttons.size)
        assertEquals(MouseButton.LEFT to ButtonState.DOWN, listener.buttons[0])
        assertEquals(MouseButton.LEFT to ButtonState.UP, listener.buttons[1])
        assertEquals(MouseButton.LEFT to ButtonState.DOWN, listener.buttons[2])
        assertEquals(MouseButton.LEFT to ButtonState.UP, listener.buttons[3])
    }

    @Test
    fun testTwoFingerTapRightClick() {
        val listener = RecordingGestureListener()
        val processor = TrackpadGestureProcessor(
            twoFingerTapRightClickEnabled = true,
            listener = listener
        )

        // Two fingers touch down together
        processor.onPointerDown(id = 1L, x = 100f, y = 200f, timestampMs = 1000L)
        processor.onPointerDown(id = 2L, x = 150f, y = 200f, timestampMs = 1010L)

        // First finger lifts within tap timeout
        processor.onPointerUp(id = 1L, timestampMs = 1080L)
        // Second finger lifts
        processor.onPointerUp(id = 2L, timestampMs = 1090L)

        // Right click triggered (Down then Up)
        assertEquals(2, listener.buttons.size)
        assertEquals(MouseButton.RIGHT to ButtonState.DOWN, listener.buttons[0])
        assertEquals(MouseButton.RIGHT to ButtonState.UP, listener.buttons[1])
    }

    @Test
    fun testTwoFingerScrollAndZoom() {
        val listener = RecordingGestureListener()
        val processor = TrackpadGestureProcessor(listener = listener)

        // Two fingers down
        processor.onPointerDown(id = 1L, x = 100f, y = 200f, timestampMs = 1000L)
        processor.onPointerDown(id = 2L, x = 150f, y = 200f, timestampMs = 1000L)

        // Move both fingers downward (swiping down) -> scrolls
        processor.onPointerMove(id = 1L, x = 100f, y = 220f, timestampMs = 1020L)
        processor.onPointerMove(id = 2L, x = 150f, y = 220f, timestampMs = 1020L)

        assertTrue(listener.scrolls.isNotEmpty())

        // Pinch apart (distance grows from 50 to 90) -> triggers zoom
        listener.clear()
        processor.onPointerMove(id = 1L, x = 80f, y = 220f, timestampMs = 1040L)
        processor.onPointerMove(id = 2L, x = 170f, y = 220f, timestampMs = 1040L)

        assertTrue(listener.zooms.isNotEmpty())
    }

    private class FakeConnectionRepository(
        val stateFlow: MutableStateFlow<ConnectionState>
    ) : ConnectionRepository {
        override val connectionState = stateFlow.asStateFlow()
        override val activeAuthToken: String? = "test-token"
        override val activeHost: String? = "192.168.1.50"
        override val activePort: Int = 8765

        override suspend fun connectToDevice(device: Device) = OrbitResult.Success(Unit)
        override suspend fun connectManual(host: String, port: Int) = OrbitResult.Success(Unit)
        override suspend fun pairWithQr(qrRawPayload: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun attemptReconnect() = OrbitResult.Success(Unit)
        override suspend fun disconnect() = OrbitResult.Success(Unit)
        override suspend fun sendPing() = OrbitResult.Success(10L)
        override suspend fun sendTelemetry(batteryPercent: Int, isCharging: Boolean, batteryStatusText: String, batteryHealth: String?, batteryTemperature: String?, chargeSource: String?, wifiConnected: Boolean, wifiSsid: String?, wifiFrequency: String?, wifiLinkSpeed: String?, bluetoothEnabled: Boolean, bluetoothConnected: Boolean, bluetoothStatusText: String, usedStorageGb: Double, totalStorageGb: Double, manufacturer: String?) = OrbitResult.Success(Unit)
        override suspend fun sendClipboard(type: String, textContent: String?, urlContent: String?, imageBase64: String?, imageFileName: String?) = OrbitResult.Success(Unit)
        override val clipboardHistory = MutableStateFlow<List<com.pratyush.orbitmobile.domain.model.ClipboardItem>>(emptyList()).asStateFlow()
        override suspend fun updateClipboardSyncState(enabled: Boolean) = OrbitResult.Success(Unit)
        override suspend fun clearClipboardHistory() = OrbitResult.Success(Unit)
        override fun receiveIncomingClipboardFromWindows(id: String, type: String, content: String, timestamp: Long, origin: String) {}
        override suspend fun refreshClipboardHistoryFromPc() = OrbitResult.Success(Unit)
    }

    private class RecordingRemoteDataSource : RemoteDataSource {
        val sentBatches = mutableListOf<List<TrackpadInputEvent>>()
        var releasedAllCount = 0

        override suspend fun sendTrackpadEvents(host: String, port: Int, authToken: String, events: List<TrackpadInputEvent>): OrbitResult<Unit> {
            sentBatches.add(events)
            return OrbitResult.Success(Unit)
        }

        override suspend fun releaseAllTrackpadButtons(host: String, port: Int, authToken: String): OrbitResult<Unit> {
            releasedAllCount++
            return OrbitResult.Success(Unit)
        }

        override suspend fun ping(host: String, port: Int) = OrbitResult.Success(10L)
        override suspend fun fetchRemoteDeviceInfo(host: String, port: Int) = OrbitResult.Error(Exception("Not used"))
        override suspend fun requestPairing(endpointUrl: String, sessionId: String, nonce: String, clientDeviceId: String, clientDeviceName: String, clientDeviceModel: String, osVersion: String, appVersion: String, firebaseUid: String?) = OrbitResult.Error(Exception("Not used"))
        override suspend fun sendTelemetry(host: String, port: Int, authToken: String, clientDeviceId: String, batteryPercent: Int, isCharging: Boolean, batteryStatusText: String, batteryHealth: String?, batteryTemperature: String?, chargeSource: String?, wifiConnected: Boolean, wifiSsid: String?, wifiFrequency: String?, wifiLinkSpeed: String?, bluetoothEnabled: Boolean, bluetoothConnected: Boolean, bluetoothStatusText: String, usedStorageGb: Double, totalStorageGb: Double, manufacturer: String?) = OrbitResult.Success(Unit)
        override suspend fun sendClipboard(host: String, port: Int, authToken: String, clientDeviceId: String, type: String, textContent: String?, urlContent: String?, imageBase64: String?, imageFileName: String?, deviceOrigin: String, id: String?, timestamp: Long?, contentHash: String?) = OrbitResult.Success(Unit)
        override suspend fun updateClipboardSyncState(host: String, port: Int, authToken: String, enabled: Boolean) = OrbitResult.Success(Unit)
        override suspend fun fetchClipboardHistory(host: String, port: Int, authToken: String) = OrbitResult.Success(emptyList<com.pratyush.orbitmobile.domain.model.ClipboardItem>())
        override suspend fun disconnect(host: String, port: Int, authToken: String) = OrbitResult.Success(Unit)
        override suspend fun reconnect(host: String, port: Int, authToken: String, clientDeviceId: String, clientDeviceName: String, firebaseUid: String?) = OrbitResult.Error(Exception("Not used"))
        override suspend fun fetchControlsStatus(host: String, port: Int, authToken: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun fetchWifiNetworks(host: String, port: Int, authToken: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun connectWifi(host: String, port: Int, authToken: String, ssid: String) = OrbitResult.Success(true)
        override suspend fun disconnectWifi(host: String, port: Int, authToken: String) = OrbitResult.Success(true)
        override suspend fun toggleWifiPower(host: String, port: Int, authToken: String, enabled: Boolean?) = OrbitResult.Success(true)
        override suspend fun toggleBluetoothPower(host: String, port: Int, authToken: String, enabled: Boolean?) = OrbitResult.Success(true)
        override suspend fun fetchBluetoothDevices(host: String, port: Int, authToken: String) = OrbitResult.Success(emptyList<com.pratyush.orbitmobile.domain.model.BluetoothDevice>())
        override suspend fun connectBluetoothDevice(host: String, port: Int, authToken: String, deviceId: String) = OrbitResult.Success(true)
        override suspend fun disconnectBluetoothDevice(host: String, port: Int, authToken: String, deviceId: String?) = OrbitResult.Success(true)
        override suspend fun toggleMicrophone(host: String, port: Int, authToken: String, mute: Boolean?) = OrbitResult.Error(Exception("Not used"))
        override suspend fun toggleCamera(host: String, port: Int, authToken: String, enabled: Boolean?) = OrbitResult.Error(Exception("Not used"))
        override suspend fun setVolume(host: String, port: Int, authToken: String, percentage: Int) = OrbitResult.Error(Exception("Not used"))
        override suspend fun toggleVolumeMute(host: String, port: Int, authToken: String, mute: Boolean?) = OrbitResult.Error(Exception("Not used"))
        override suspend fun fetchBrightness(host: String, port: Int, authToken: String) = OrbitResult.Error(Exception("Not used"))
        override suspend fun setBrightness(host: String, port: Int, authToken: String, percentage: Int) = OrbitResult.Error(Exception("Not used"))
        override fun listenToControlEvents(host: String, port: Int, authToken: String) = kotlinx.coroutines.flow.emptyFlow<com.pratyush.orbitmobile.domain.model.ControlEvent>()
        override suspend fun fetchSystemStats(host: String, port: Int, authToken: String) = OrbitResult.Error(Exception("Not used"))

    }

    @Test
    fun testPhysicalClickZonesAndHoldingLeftClickWhileMoving() = runBlocking {
        val connState = MutableStateFlow<ConnectionState>(
            ConnectionState.Connected(
                deviceId = "pc-1",
                deviceName = "My PC",
                host = "192.168.1.50",
                port = 8765
            )
        )
        val connRepo = FakeConnectionRepository(connState)
        val remoteData = RecordingRemoteDataSource()
        val repo = TrackpadRepositoryImpl(connRepo, remoteData, CoroutineScope(Dispatchers.Unconfined))

        assertFalse(repo.isLeftButtonDown.value)
        assertFalse(repo.isRightButtonDown.value)

        // 1. Press Left Click Zone
        repo.sendButton(MouseButton.LEFT, ButtonState.DOWN)
        assertTrue(repo.isLeftButtonDown.value)

        // 2. While held down, user moves finger on touchpad
        repo.sendMove(15, -8)
        repo.flushPendingEvents()

        // 3. Release Left Click Zone
        repo.sendButton(MouseButton.LEFT, ButtonState.UP)
        assertFalse(repo.isLeftButtonDown.value)

        // 4. Press Right Click Zone
        repo.sendButton(MouseButton.RIGHT, ButtonState.DOWN)
        assertTrue(repo.isRightButtonDown.value)
        repo.sendButton(MouseButton.RIGHT, ButtonState.UP)
        assertFalse(repo.isRightButtonDown.value)
    }

    @Test
    fun testConnectionLossDuringHeldClickReleasesButtons() = runBlocking {
        val connState = MutableStateFlow<ConnectionState>(
            ConnectionState.Connected(
                deviceId = "pc-1",
                deviceName = "My PC",
                host = "192.168.1.50",
                port = 8765
            )
        )
        val connRepo = FakeConnectionRepository(connState)
        val remoteData = RecordingRemoteDataSource()
        val repo = TrackpadRepositoryImpl(connRepo, remoteData, CoroutineScope(Dispatchers.Unconfined))

        // Press both Left and Right clicks
        repo.sendButton(MouseButton.LEFT, ButtonState.DOWN)
        repo.sendButton(MouseButton.RIGHT, ButtonState.DOWN)
        assertTrue(repo.isLeftButtonDown.value)
        assertTrue(repo.isRightButtonDown.value)

        // Simulate Connection Drop / Loss
        connState.value = ConnectionState.Disconnected

        // Both buttons must be immediately logically released
        assertFalse(repo.isLeftButtonDown.value)
        assertFalse(repo.isRightButtonDown.value)
    }

    @Test
    fun testNavigationAwayReleasesHeldButtons() = runBlocking {
        val connState = MutableStateFlow<ConnectionState>(
            ConnectionState.Connected(
                deviceId = "pc-1",
                deviceName = "My PC",
                host = "192.168.1.50",
                port = 8765
            )
        )
        val connRepo = FakeConnectionRepository(connState)
        val remoteData = RecordingRemoteDataSource()
        val repo = TrackpadRepositoryImpl(connRepo, remoteData, CoroutineScope(Dispatchers.Unconfined))

        // Hold left button
        repo.sendButton(MouseButton.LEFT, ButtonState.DOWN)
        assertTrue(repo.isLeftButtonDown.value)

        // User navigates away -> calls releaseHeldButtons()
        repo.releaseHeldButtons()
        assertFalse(repo.isLeftButtonDown.value)
    }

    @Test
    fun testRapidMovementCoalescing() = runBlocking {
        val connState = MutableStateFlow<ConnectionState>(
            ConnectionState.Connected(
                deviceId = "pc-1",
                deviceName = "My PC",
                host = "192.168.1.50",
                port = 8765
            )
        )
        val connRepo = FakeConnectionRepository(connState)
        val remoteData = RecordingRemoteDataSource()
        val repo = TrackpadRepositoryImpl(connRepo, remoteData, CoroutineScope(Dispatchers.Unconfined))

        // Rapid 10 movements sent in tight loop
        for (i in 1..10) {
            repo.sendMove(2, 3)
        }

        // Flush coalesced batch
        repo.flushPendingEvents()

        // Verify that moves were coalesced to (20, 30)
        val allSentEvents = remoteData.sentBatches.flatten()
        val moveEvents = allSentEvents.filterIsInstance<TrackpadInputEvent.Move>()
        assertTrue(moveEvents.isNotEmpty())
        val totalDx = moveEvents.sumOf { it.dx }
        val totalDy = moveEvents.sumOf { it.dy }
        assertEquals(20, totalDx)
        assertEquals(30, totalDy)
    }
}
