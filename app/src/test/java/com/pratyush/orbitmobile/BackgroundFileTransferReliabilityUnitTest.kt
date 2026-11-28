package com.pratyush.orbitmobile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.remote.RemoteDataSource
import com.pratyush.orbitmobile.data.repository.implementations.FileTransferRepositoryImpl
import com.pratyush.orbitmobile.domain.model.ClipboardItem
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.Device
import com.pratyush.orbitmobile.domain.model.FileCategory
import com.pratyush.orbitmobile.domain.model.FileTransferState
import com.pratyush.orbitmobile.domain.model.ReceivedRemoteFile
import com.pratyush.orbitmobile.domain.model.SelectedFileItem
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.presentation.screens.filesystem.send.SendFilesViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicInteger

class BackgroundFileTransferReliabilityUnitTest {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        appScope.cancel()
    }

    @Test
    fun transferContinuesAfterViewModelClearedAndProgressRemainsObservableOnReturn() = runBlocking {
        val chunkGate = CompletableDeferred<Unit>()
        val remote = FakeRemoteDataSource(onSendChunk = {
            chunkGate.await()
            OrbitResult.Success(Unit)
        })
        val serviceStarts = AtomicInteger(0)
        val serviceStops = AtomicInteger(0)

        val uri1 = allocateTestUri()
        val repo = createRepository(
            remoteDataSource = remote,
            serviceStarts = serviceStarts,
            serviceStops = serviceStops,
            filePayloads = identityMapOf(uri1 to ByteArray(1024) { 1 })
        )
        repo.setSelectedFilesForTest(listOf(testFileItem("file1", uri1, 1024)))

        // Simulate user opening SendFilesScreen, starting transfer, and navigating away (ViewModel cleared)
        val store1 = ViewModelStore()
        val vm1 = SendFilesViewModel(repo)
        store1.put("send_vm", vm1)

        vm1.onStartSend()
        waitUntil { repo.isTransferActive() }
        assertEquals(1, serviceStarts.get())
        assertEquals(0, serviceStops.get())

        // Clearing ViewModelStore cancels vm1.viewModelScope
        store1.clear()
        delay(100)

        // Transfer MUST still be active on application scope
        assertTrue(repo.isTransferActive())
        assertEquals(0, serviceStops.get())

        // Simulate user returning to SendFilesScreen (new ViewModel instance)
        val vm2 = SendFilesViewModel(repo)
        assertTrue(vm2.transferState.value is FileTransferState.Preparing || vm2.transferState.value is FileTransferState.Transferring)

        // Allow chunk to finish
        chunkGate.complete(Unit)
        waitUntil { vm2.transferState.value is FileTransferState.Completed }

        assertTrue(vm2.transferState.value is FileTransferState.Completed)
        assertFalse(repo.isTransferActive())
        assertEquals(1, serviceStops.get())
    }

    @Test
    fun duplicateTransferExecutionIsPrevented() = runBlocking {
        val chunkGate = CompletableDeferred<Unit>()
        val initCount = AtomicInteger(0)
        val remote = FakeRemoteDataSource(
            onInit = {
                initCount.incrementAndGet()
                OrbitResult.Success(Unit)
            },
            onSendChunk = {
                chunkGate.await()
                OrbitResult.Success(Unit)
            }
        )

        val uri1 = allocateTestUri()
        val repo = createRepository(
            remoteDataSource = remote,
            filePayloads = identityMapOf(uri1 to ByteArray(1024) { 2 })
        )
        repo.setSelectedFilesForTest(listOf(testFileItem("file1", uri1, 1024)))

        val vm = SendFilesViewModel(repo)
        vm.onStartSend()
        waitUntil { repo.isTransferActive() }

        // Second request via ViewModel and direct repository call must not launch duplicate job
        vm.onStartSend()
        val secondResult = repo.startTransfer()
        assertTrue(secondResult is OrbitResult.Error)

        chunkGate.complete(Unit)
        waitUntil { repo.transferState.value is FileTransferState.Completed }
        assertEquals(1, initCount.get())
    }

    @Test
    fun cancelTerminatesActiveTransferAndStopsServiceWithoutStateOverwrite() = runBlocking {
        val chunkGate = CompletableDeferred<Unit>()
        val cancelCalls = AtomicInteger(0)
        val serviceStops = AtomicInteger(0)

        val remote = FakeRemoteDataSource(
            onSendChunk = {
                chunkGate.await()
                OrbitResult.Success(Unit)
            },
            onCancel = {
                cancelCalls.incrementAndGet()
                OrbitResult.Success(Unit)
            }
        )

        val uri1 = allocateTestUri()
        val repo = createRepository(
            remoteDataSource = remote,
            serviceStops = serviceStops,
            filePayloads = identityMapOf(uri1 to ByteArray(2048) { 3 })
        )
        repo.setSelectedFilesForTest(listOf(testFileItem("file1", uri1, 2048)))

        val vm = SendFilesViewModel(repo)
        vm.onStartSend()
        waitUntil { repo.isTransferActive() }

        vm.onCancelTransfer()
        chunkGate.complete(Unit)

        waitUntil { !repo.isTransferActive() && serviceStops.get() == 1 }
        delay(50)

        assertEquals(FileTransferState.Cancelled, repo.transferState.value)
        assertEquals(1, serviceStops.get())
    }

    @Test
    fun multipleSelectedFilesAreTransferredAndServiceStopsOnFailure() = runBlocking {
        val serviceStarts = AtomicInteger(0)
        val serviceStops = AtomicInteger(0)
        val completedIds = AtomicInteger(0)

        val remote = FakeRemoteDataSource(
            onComplete = {
                completedIds.incrementAndGet()
                OrbitResult.Success(Unit)
            }
        )

        val uri1 = allocateTestUri()
        val uri2 = allocateTestUri()
        val repo = createRepository(
            remoteDataSource = remote,
            serviceStarts = serviceStarts,
            serviceStops = serviceStops,
            filePayloads = identityMapOf(
                uri1 to ByteArray(512) { 1 },
                uri2 to ByteArray(1024) { 2 }
            )
        )
        repo.setSelectedFilesForTest(
            listOf(
                testFileItem("file1", uri1, 512),
                testFileItem("file2", uri2, 1024)
            )
        )

        val result = repo.startTransfer()
        assertTrue(result is OrbitResult.Success)
        assertEquals(2, completedIds.get())
        assertEquals(1, serviceStarts.get())
        assertEquals(1, serviceStops.get())
        val state = repo.transferState.value as FileTransferState.Completed
        assertEquals(2, state.totalFiles)
        assertTrue(state.failedFiles.isEmpty())

        // Verify failure when disconnected also leaves service stopped and Failed state
        val uriFail = allocateTestUri()
        val disconnectedRepo = createRepository(
            connectionState = ConnectionState.Disconnected,
            remoteDataSource = remote,
            serviceStarts = serviceStarts,
            serviceStops = serviceStops,
            filePayloads = identityMapOf(uriFail to ByteArray(100))
        )
        disconnectedRepo.setSelectedFilesForTest(listOf(testFileItem("file1", uriFail, 100)))
        val failRes = disconnectedRepo.startTransfer()
        assertTrue(failRes is OrbitResult.Error)
        assertTrue(disconnectedRepo.transferState.value is FileTransferState.Failed)
        assertEquals(1, serviceStarts.get())
        assertEquals(1, serviceStops.get())
    }

    private fun createRepository(
        connectionState: ConnectionState = ConnectionState.Connected(
            deviceId = "pc-1",
            deviceName = "TestPC",
            host = "192.168.1.10",
            port = 8767
        ),
        remoteDataSource: RemoteDataSource,
        serviceStarts: AtomicInteger = AtomicInteger(0),
        serviceStops: AtomicInteger = AtomicInteger(0),
        filePayloads: Map<Uri, ByteArray>
    ): FileTransferRepositoryImpl {
        val connRepo = FakeConnectionRepository(connectionState)
        return FileTransferRepositoryImpl(
            context = ContextStub(),
            connectionRepository = connRepo,
            remoteDataSource = remoteDataSource,
            scope = appScope,
            firebaseFileTransferClient = null,
            serviceStarter = { _, _ -> serviceStarts.incrementAndGet() },
            serviceStopper = { serviceStops.incrementAndGet() },
            streamOpener = { uri ->
                filePayloads[uri]?.let { ByteArrayInputStream(it) }
            }
        )
    }

    private fun testFileItem(id: String, uri: Uri, size: Long) = SelectedFileItem(
        id = id,
        uri = uri,
        name = "$id.bin",
        size = size,
        mimeType = "application/octet-stream",
        category = FileCategory.OTHER
    )

    private fun identityMapOf(vararg pairs: Pair<Uri, ByteArray>): Map<Uri, ByteArray> {
        val map = IdentityHashMap<Uri, ByteArray>()
        for ((k, v) in pairs) map[k] = v
        return map
    }

    private fun allocateTestUri(): Uri = android.net.TestUri("content://test/${System.nanoTime()}")

    private suspend fun waitUntil(timeoutMs: Long = 3_000L, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Timed out waiting for condition")
            }
            delay(15)
        }
    }

    private class ContextStub : android.content.ContextWrapper(null)

    private class FakeConnectionRepository(
        initialState: ConnectionState
    ) : ConnectionRepository {
        override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(initialState)
        override val activeAuthToken: String? = "test-token"
        override val activeHost: String? = (initialState as? ConnectionState.Connected)?.host
        override val activePort: Int = 8767
        override val clipboardHistory: StateFlow<List<ClipboardItem>> = MutableStateFlow(emptyList())
        override suspend fun connectToDevice(device: Device) = OrbitResult.Success(Unit)
        override suspend fun connectManual(host: String, port: Int) = OrbitResult.Success(Unit)
        override suspend fun pairWithQr(qrRawPayload: String) = OrbitResult.Error(UnsupportedOperationException())
        override suspend fun attemptReconnect() = OrbitResult.Success(Unit)
        override suspend fun disconnect() = OrbitResult.Success(Unit)
        override suspend fun sendPing() = OrbitResult.Success(1L)
        override suspend fun sendTelemetry(
            batteryPercent: Int, isCharging: Boolean, batteryStatusText: String,
            batteryHealth: String?, batteryTemperature: String?, chargeSource: String?,
            wifiConnected: Boolean, wifiSsid: String?, wifiFrequency: String?,
            wifiLinkSpeed: String?, bluetoothEnabled: Boolean, bluetoothConnected: Boolean,
            bluetoothStatusText: String, usedStorageGb: Double, totalStorageGb: Double,
            manufacturer: String?
        ) = OrbitResult.Success(Unit)
        override suspend fun sendClipboard(
            type: String, textContent: String?, urlContent: String?,
            imageBase64: String?, imageFileName: String?
        ) = OrbitResult.Success(Unit)
        override suspend fun updateClipboardSyncState(enabled: Boolean) = OrbitResult.Success(Unit)
        override suspend fun clearClipboardHistory() = OrbitResult.Success(Unit)
        override fun receiveIncomingClipboardFromWindows(
            id: String, type: String, content: String, timestamp: Long, origin: String
        ) = Unit
        override suspend fun refreshClipboardHistoryFromPc() = OrbitResult.Success(Unit)
    }

    private class FakeRemoteDataSource(
        private val onInit: suspend () -> OrbitResult<Unit> = { OrbitResult.Success(Unit) },
        private val onSendChunk: suspend () -> OrbitResult<Unit> = { OrbitResult.Success(Unit) },
        private val onComplete: suspend () -> OrbitResult<Unit> = { OrbitResult.Success(Unit) },
        private val onCancel: suspend () -> OrbitResult<Unit> = { OrbitResult.Success(Unit) }
    ) : RemoteDataSource by (java.lang.reflect.Proxy.newProxyInstance(
        RemoteDataSource::class.java.classLoader,
        arrayOf(RemoteDataSource::class.java)
    ) { _, method, _ ->
        throw UnsupportedOperationException("Unexpected call to ${method.name}")
    } as RemoteDataSource) {
        override suspend fun initFileTransfer(
            host: String, port: Int, authToken: String, transferId: String,
            fileName: String, fileSize: Long, mimeType: String, totalChunks: Int,
            chunkSize: Int, sha256: String
        ): OrbitResult<Unit> = onInit()

        override suspend fun sendFileChunk(
            host: String, port: Int, authToken: String, transferId: String,
            chunkIndex: Int, chunkSha256: String, chunkData: ByteArray, offset: Int, length: Int
        ): OrbitResult<Unit> = onSendChunk()

        override suspend fun completeFileTransfer(
            host: String, port: Int, authToken: String, transferId: String, sha256: String
        ): OrbitResult<Unit> = onComplete()

        override suspend fun cancelFileTransfer(
            host: String, port: Int, authToken: String, transferId: String
        ): OrbitResult<Unit> = onCancel()

        override suspend fun fetchReceivedFiles(host: String, port: Int, authToken: String): OrbitResult<List<ReceivedRemoteFile>> =
            OrbitResult.Success(emptyList())
    }
}
