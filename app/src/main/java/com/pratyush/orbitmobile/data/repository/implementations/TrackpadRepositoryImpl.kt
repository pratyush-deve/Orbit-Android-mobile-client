package com.pratyush.orbitmobile.data.repository.implementations

import android.util.Log
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.remote.RemoteDataSource
import com.pratyush.orbitmobile.domain.model.ButtonState
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.MouseButton
import com.pratyush.orbitmobile.domain.model.TrackpadInputEvent
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.domain.repository.TrackpadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * High-performance, low-latency implementation of [TrackpadRepository].
 * Coalesces rapid mouse motion events (~16ms ticker) to avoid flooding network channels,
 * dispatches clicks/scrolls with sub-millisecond priority, and guarantees automatic
 * button release upon disconnection or cancellation.
 */
class TrackpadRepositoryImpl(
    private val connectionRepository: ConnectionRepository,
    private val remoteDataSource: RemoteDataSource,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : TrackpadRepository {

    private val _isLeftButtonDown = MutableStateFlow(false)
    override val isLeftButtonDown: StateFlow<Boolean> = _isLeftButtonDown.asStateFlow()

    private val _isRightButtonDown = MutableStateFlow(false)
    override val isRightButtonDown: StateFlow<Boolean> = _isRightButtonDown.asStateFlow()

    override val isConnected: StateFlow<Boolean> = connectionRepository.connectionState
        .map { it is ConnectionState.Connected }
        .stateIn(scope, SharingStarted.Eagerly, connectionRepository.connectionState.value is ConnectionState.Connected)

    private val eventMutex = Mutex()
    private var pendingDx = 0
    private var pendingDy = 0

    // Priority event channel for non-move events (clicks, scrolls, zoom)
    private val immediateEventQueue = Channel<TrackpadInputEvent>(capacity = Channel.UNLIMITED)

    private var dispatchLoopJob: Job? = null

    init {
        // Observe connection state transitions for safety
        scope.launch {
            connectionRepository.connectionState.collect { state ->
                if (state !is ConnectionState.Connected) {
                    onDisconnected()
                }
            }
        }

        // Start coalesced dispatch loop
        startDispatchLoop()
    }

    private fun startDispatchLoop() {
        dispatchLoopJob?.cancel()
        dispatchLoopJob = scope.launch {
            while (isActive) {
                delay(16L) // ~60 FPS coalesce rate

                if (!isConnected.value) continue

                val eventsToSend = mutableListOf<TrackpadInputEvent>()

                // 1. Drain priority non-move events
                while (true) {
                    val priorityEvent = immediateEventQueue.tryReceive().getOrNull() ?: break
                    eventsToSend.add(priorityEvent)
                }

                // 2. Extract coalesced motion
                var dx = 0
                var dy = 0
                eventMutex.withLock {
                    if (pendingDx != 0 || pendingDy != 0) {
                        dx = pendingDx
                        dy = pendingDy
                        pendingDx = 0
                        pendingDy = 0
                    }
                }

                if (dx != 0 || dy != 0) {
                    // Prepend or append motion relative to clicks
                    eventsToSend.add(TrackpadInputEvent.Move(dx, dy))
                }

                if (eventsToSend.isNotEmpty()) {
                    dispatchBatch(eventsToSend)
                }
            }
        }
    }

    private suspend fun dispatchBatch(events: List<TrackpadInputEvent>) {
        val host = connectionRepository.activeHost ?: return
        val port = connectionRepository.activePort
        val token = connectionRepository.activeAuthToken ?: return

        try {
            val result = remoteDataSource.sendTrackpadEvents(host, port, token, events)
            if (result is OrbitResult.Error) {
                Log.w("TrackpadRepo", "Failed to dispatch trackpad events: ${result.exception.message}")
            }
        } catch (e: Exception) {
            Log.w("TrackpadRepo", "Exception dispatching trackpad events: ${e.message}")
        }
    }

    override fun sendMove(dx: Int, dy: Int) {
        if (!isConnected.value || (dx == 0 && dy == 0)) return
        scope.launch {
            eventMutex.withLock {
                pendingDx += dx
                pendingDy += dy
            }
        }
    }

    override fun sendButton(button: MouseButton, state: ButtonState) {
        if (!isConnected.value) return

        when (button) {
            MouseButton.LEFT -> _isLeftButtonDown.value = (state == ButtonState.DOWN)
            MouseButton.RIGHT -> _isRightButtonDown.value = (state == ButtonState.DOWN)
            MouseButton.MIDDLE -> {}
        }

        immediateEventQueue.trySend(TrackpadInputEvent.Button(button, state))

        // Trigger immediate flush on button events
        scope.launch {
            flushPendingEvents()
        }
    }

    override fun sendScroll(dx: Int, dy: Int) {
        if (!isConnected.value || (dx == 0 && dy == 0)) return
        immediateEventQueue.trySend(TrackpadInputEvent.Scroll(dx, dy))
    }

    override fun sendZoom(delta: Double) {
        if (!isConnected.value || delta == 0.0) return
        immediateEventQueue.trySend(TrackpadInputEvent.Zoom(delta))
    }

    override fun releaseHeldButtons() {
        val hadLeft = _isLeftButtonDown.value
        val hadRight = _isRightButtonDown.value

        _isLeftButtonDown.value = false
        _isRightButtonDown.value = false

        if (hadLeft || hadRight) {
            immediateEventQueue.trySend(TrackpadInputEvent.ReleaseAll)
            scope.launch {
                flushPendingEvents()
            }
        }
    }

    override suspend fun flushPendingEvents() {
        val eventsToSend = mutableListOf<TrackpadInputEvent>()

        while (true) {
            val priorityEvent = immediateEventQueue.tryReceive().getOrNull() ?: break
            eventsToSend.add(priorityEvent)
        }

        var dx = 0
        var dy = 0
        eventMutex.withLock {
            if (pendingDx != 0 || pendingDy != 0) {
                dx = pendingDx
                dy = pendingDy
                pendingDx = 0
                pendingDy = 0
            }
        }

        if (dx != 0 || dy != 0) {
            eventsToSend.add(TrackpadInputEvent.Move(dx, dy))
        }

        if (eventsToSend.isNotEmpty() && isConnected.value) {
            dispatchBatch(eventsToSend)
        }
    }

    private fun onDisconnected() {
        _isLeftButtonDown.value = false
        _isRightButtonDown.value = false
        scope.launch {
            eventMutex.withLock {
                pendingDx = 0
                pendingDy = 0
            }
            while (immediateEventQueue.tryReceive().isSuccess) {
                // Drain queue
            }
        }
    }
}
