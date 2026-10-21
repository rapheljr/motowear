package com.motowear.wear.state

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.motowear.telemetry.ManeuverKind
import com.motowear.telemetry.ProtobufTelemetryMapper
import com.motowear.telemetry.RoutePolyline
import com.motowear.telemetry.ScreenMode
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.telemetry.TelemetryPaths
import com.motowear.telemetry.Vec2
import com.motowear.wear.data.WearTelemetryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Section 2, Section 5 & Section 6:
 * - Subscribes to live 10Hz Protobuf frames from `WearTelemetryRepository` and `MessageClient`
 *   while seamlessly falling back to the 60fps local mock generator when no phone stream is active.
 * - Supports touch swipe gestures (horizontal left/right & vertical up/down) and edge tap targets
 *   to cycle across all Ride Modes and Device Settings, alongside physical stem buttons and rotary crown events.
 */
class RideViewModel(application: Application) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {

    private val allModesOrder = listOf(
        ScreenMode.QR_ONBOARDING,
        ScreenMode.QUICK_START,
        ScreenMode.DEVICE_SETTINGS,
        ScreenMode.TURN_BY_TURN,
        ScreenMode.FAST_REROUTING,
        ScreenMode.SPEEDO,
        ScreenMode.JOURNEY_PROGRESS,
        ScreenMode.JOURNEY_TRACKING,
        ScreenMode.WAYPOINT_SKIPPING,
        ScreenMode.JOURNEY_SUMMARY,
        ScreenMode.DEBUG_TELEMETRY
    )

    private val carouselOrder = listOf(
        ScreenMode.TURN_BY_TURN,
        ScreenMode.SPEEDO,
        ScreenMode.JOURNEY_PROGRESS,
        ScreenMode.JOURNEY_TRACKING,
        ScreenMode.DEBUG_TELEMETRY
    )

    private val _uiState = MutableStateFlow(createReferenceBaselineState())
    val uiState: StateFlow<TelemetryFrameState> = _uiState.asStateFlow()

    private val messageClient = Wearable.getMessageClient(application)
    private var lastPhonePacketTimestampMs: Long = 0L
    private var userSwipedModeOverride: ScreenMode? = null
    private var lastRemotePhoneMode: ScreenMode? = null
    private var simulationTimeSec = 0f
    private var lastValidCogDegrees = 330

    var packetsReceivedCount: Long = 0L
        private set

    init {
        messageClient.addListener(this).addOnSuccessListener {
            Log.d("RideViewModel", "MessageClient listener registered successfully")
        }.addOnFailureListener {
            Log.e("RideViewModel", "Failed to register MessageClient listener", it)
        }
        observeIncomingPhoneTelemetry()
        start60FpsRenderAndBurnInLoop()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == TelemetryPaths.TELEMETRY_FRAME_PATH) {
            val decoded = runCatching {
                ProtobufTelemetryMapper.fromProtoByteArray(messageEvent.data, _uiState.value)
            }.onFailure {
                Log.e("RideViewModel", "Failed to decode incoming telemetry frame", it)
            }.getOrNull()
            if (decoded != null) {
                applyPhoneTelemetryFrame(decoded)
            }
        }
    }

    private fun observeIncomingPhoneTelemetry() {
        viewModelScope.launch {
            WearTelemetryRepository.incomingFrames.collect { frame ->
                applyPhoneTelemetryFrame(frame)
            }
        }
    }

    private fun applyPhoneTelemetryFrame(incoming: TelemetryFrameState) {
        val now = System.currentTimeMillis()
        lastPhonePacketTimestampMs = now
        packetsReceivedCount++

        if (lastRemotePhoneMode != null && incoming.mode != lastRemotePhoneMode) {
            userSwipedModeOverride = null
        }
        lastRemotePhoneMode = incoming.mode

        _uiState.update { current ->
            val cog = resolveCogHeading(incoming.currentSpeedKmh, incoming.headingDegrees)
            val dynamicAmbient = if (incoming.distanceToNextMeters <= 400 || incoming.isOffRoute) {
                false
            } else {
                current.isAmbientMode
            }
            if (incoming.mode != ScreenMode.QUICK_START && userSwipedModeOverride == ScreenMode.QUICK_START) {
                userSwipedModeOverride = null
            }
            val resolvedMode = when {
                incoming.isOffRoute -> ScreenMode.FAST_REROUTING
                userSwipedModeOverride != null -> userSwipedModeOverride!!
                else -> incoming.mode
            }
            incoming.copy(
                mode = resolvedMode,
                headingDegrees = cog,
                odometerSubPage = current.odometerSubPage,
                settingsSelectedIndex = current.settingsSelectedIndex,
                lastPacketMsAgo = 0L,
                packetsReceivedCount = packetsReceivedCount,
                isTouchLocked = current.isTouchLocked,
                isAmbientMode = dynamicAmbient,
                burnInOffsetPxX = current.burnInOffsetPxX,
                burnInOffsetPxY = current.burnInOffsetPxY
            )
        }
    }

    /**
     * Swipe gesture handler for switching across all Ride Modes on the Wear OS touchscreen.
     */
    fun onSwipeRideMode(direction: Int) {
        val step = if (direction >= 0) 1 else -1
        _uiState.update { state ->
            val currentIndex = allModesOrder.indexOf(state.mode).coerceAtLeast(0)
            val nextIndex = (currentIndex + step + allModesOrder.size) % allModesOrder.size
            val nextMode = allModesOrder[nextIndex]
            userSwipedModeOverride = nextMode
            state.copy(
                mode = nextMode,
                isOffRoute = nextMode == ScreenMode.FAST_REROUTING
            )
        }
    }

    /**
     * Vertical swipe gesture handler:
     * - On `DEVICE_SETTINGS`, scrolls through `"About"` (0), `"Unpair"` (1), `"Reset"` (2), `"Legal"` (3).
     * - On `WAYPOINT_SKIPPING`, increments/decrements the target waypoint (`1/3 -> 2/3`).
     * - On `JOURNEY_TRACKING`, toggles the odometer sub-page (`○ ●`).
     * - On all other screens, cycles through the active ride modes.
     */
    fun onVerticalSwipe(direction: Int) {
        val step = if (direction >= 0) 1 else -1
        _uiState.update { state ->
            when (state.mode) {
                ScreenMode.DEVICE_SETTINGS -> {
                    val nextIdx = (state.settingsSelectedIndex + step).coerceIn(0, 3)
                    state.copy(settingsSelectedIndex = nextIdx)
                }
                ScreenMode.WAYPOINT_SKIPPING -> {
                    val nextWp = (state.currentWaypointIdx + step).coerceIn(1, state.totalWaypoints)
                    state.copy(currentWaypointIdx = nextWp)
                }
                ScreenMode.JOURNEY_TRACKING -> {
                    state.copy(odometerSubPage = (state.odometerSubPage + 1) % 2)
                }
                else -> {
                    val currentIndex = allModesOrder.indexOf(state.mode).coerceAtLeast(0)
                    val nextIndex = (currentIndex + step + allModesOrder.size) % allModesOrder.size
                    val nextMode = allModesOrder[nextIndex]
                    userSwipedModeOverride = nextMode
                    state.copy(
                        mode = nextMode,
                        isOffRoute = nextMode == ScreenMode.FAST_REROUTING
                    )
                }
            }
        }
    }

    /**
     * Handles taps on the left (9 o'clock `<`), right (3 o'clock `>`), or bottom (6 o'clock yellow dome)
     * interactive edge zones.
     */
    fun onEdgeZoneTap(zone: EdgeTapZone) {
        val current = _uiState.value
        when (zone) {
            EdgeTapZone.LEFT_9_OCLOCK -> {
                when (current.mode) {
                    ScreenMode.WAYPOINT_SKIPPING -> {
                        _uiState.update {
                            it.copy(currentWaypointIdx = (it.currentWaypointIdx - 1).coerceAtLeast(1))
                        }
                    }
                    ScreenMode.DEVICE_SETTINGS -> {
                        userSwipedModeOverride = ScreenMode.QUICK_START
                        _uiState.update { it.copy(mode = ScreenMode.QUICK_START) }
                    }
                    else -> onSwipeRideMode(direction = -1)
                }
            }
            EdgeTapZone.RIGHT_3_OCLOCK -> {
                when (current.mode) {
                    ScreenMode.WAYPOINT_SKIPPING -> {
                        _uiState.update {
                            it.copy(
                                currentWaypointIdx = (it.currentWaypointIdx + 1).coerceAtMost(it.totalWaypoints)
                            )
                        }
                    }
                    ScreenMode.DEVICE_SETTINGS -> executeSelectedDeviceSetting()
                    ScreenMode.QUICK_START,
                    ScreenMode.FAST_REROUTING -> onBottomActionButtonPress()
                    else -> onSwipeRideMode(direction = 1)
                }
            }
            EdgeTapZone.BOTTOM_6_OCLOCK -> {
                onBottomActionButtonPress()
            }
        }
    }

    private fun executeSelectedDeviceSetting() {
        _uiState.update { state ->
            when (state.settingsSelectedIndex) {
                0 -> {
                    // "About" -> Show Telemetry Debug / Version inspector
                    userSwipedModeOverride = ScreenMode.DEBUG_TELEMETRY
                    state.copy(mode = ScreenMode.DEBUG_TELEMETRY)
                }
                1 -> {
                    // "Unpair" -> Return to QR Onboarding pairing screen
                    userSwipedModeOverride = ScreenMode.QR_ONBOARDING
                    state.copy(mode = ScreenMode.QR_ONBOARDING)
                }
                2 -> {
                    // "Reset" -> Reset odometer & average speed and return to QUICK_START
                    userSwipedModeOverride = ScreenMode.QUICK_START
                    state.copy(
                        odometerKm = 0.0f,
                        avgSpeedKmh = 0,
                        settingsSelectedIndex = 0,
                        mode = ScreenMode.QUICK_START
                    )
                }
                else -> {
                    // "Legal" -> Return to QUICK_START
                    userSwipedModeOverride = ScreenMode.QUICK_START
                    state.copy(mode = ScreenMode.QUICK_START)
                }
            }
        }
    }

    /**
     * Toggles Capacitive Touch Lock (Wet/Glove Mode) when explicitly requested by the rider.
     */
    fun toggleTouchLock() {
        _uiState.update { it.copy(isTouchLocked = !it.isTouchLocked) }
    }

    /**
     * Rotary Crown Scroll / Top Button Short Press handler (Section 5).
     */
    fun onRotaryOrTopShortPress(direction: Int = 1) {
        val step = if (direction >= 0) 1 else -1
        _uiState.update { state ->
            when (state.mode) {
                ScreenMode.QUICK_START -> {
                    userSwipedModeOverride = ScreenMode.TURN_BY_TURN
                    state.copy(mode = ScreenMode.TURN_BY_TURN)
                }
                ScreenMode.DEVICE_SETTINGS -> {
                    val nextIdx = (state.settingsSelectedIndex + step).coerceIn(0, 3)
                    state.copy(settingsSelectedIndex = nextIdx)
                }
                ScreenMode.TURN_BY_TURN,
                ScreenMode.FAST_REROUTING,
                ScreenMode.SPEEDO,
                ScreenMode.JOURNEY_PROGRESS,
                ScreenMode.JOURNEY_TRACKING -> {
                    val baseMode = if (state.mode == ScreenMode.FAST_REROUTING) {
                        ScreenMode.TURN_BY_TURN
                    } else {
                        state.mode
                    }
                    val currentIndex = carouselOrder.indexOf(baseMode).coerceAtLeast(0)
                    val nextIndex = (currentIndex + step + carouselOrder.size) % carouselOrder.size
                    val nextMode = carouselOrder[nextIndex]
                    userSwipedModeOverride = nextMode
                    state.copy(mode = nextMode)
                }
                ScreenMode.WAYPOINT_SKIPPING -> {
                    val nextWp = (state.currentWaypointIdx + step).coerceIn(1, state.totalWaypoints)
                    state.copy(currentWaypointIdx = nextWp)
                }
                ScreenMode.JOURNEY_SUMMARY,
                ScreenMode.QR_ONBOARDING,
                ScreenMode.DEBUG_TELEMETRY -> {
                    userSwipedModeOverride = ScreenMode.QUICK_START
                    state.copy(mode = ScreenMode.QUICK_START)
                }
            }
        }
    }

    /**
     * Top Button Long Press (1.5s) handler (Section 5).
     */
    fun onTopButtonLongPress() {
        _uiState.update { state ->
            when (state.mode) {
                ScreenMode.QUICK_START -> {
                    userSwipedModeOverride = ScreenMode.DEVICE_SETTINGS
                    state.copy(mode = ScreenMode.DEVICE_SETTINGS)
                }
                ScreenMode.DEVICE_SETTINGS -> {
                    userSwipedModeOverride = ScreenMode.QUICK_START
                    state.copy(mode = ScreenMode.QUICK_START)
                }
                ScreenMode.TURN_BY_TURN,
                ScreenMode.FAST_REROUTING -> {
                    userSwipedModeOverride = ScreenMode.WAYPOINT_SKIPPING
                    state.copy(mode = ScreenMode.WAYPOINT_SKIPPING)
                }
                ScreenMode.SPEEDO -> {
                    state.copy(avgSpeedKmh = state.currentSpeedKmh)
                }
                ScreenMode.JOURNEY_PROGRESS,
                ScreenMode.JOURNEY_TRACKING -> {
                    userSwipedModeOverride = ScreenMode.JOURNEY_SUMMARY
                    state.copy(mode = ScreenMode.JOURNEY_SUMMARY)
                }
                ScreenMode.WAYPOINT_SKIPPING -> {
                    userSwipedModeOverride = ScreenMode.TURN_BY_TURN
                    state.copy(mode = ScreenMode.TURN_BY_TURN)
                }
                ScreenMode.JOURNEY_SUMMARY,
                ScreenMode.QR_ONBOARDING,
                ScreenMode.DEBUG_TELEMETRY -> state
            }
        }
    }

    /**
     * Bottom Button / Action Button Press handler (Section 5).
     */
    fun onBottomActionButtonPress() {
        val current = _uiState.value
        when (current.mode) {
            ScreenMode.QUICK_START -> {
                userSwipedModeOverride = ScreenMode.JOURNEY_TRACKING
                _uiState.update {
                    it.copy(mode = ScreenMode.JOURNEY_TRACKING)
                }
            }
            ScreenMode.DEVICE_SETTINGS -> {
                executeSelectedDeviceSetting()
            }
            ScreenMode.TURN_BY_TURN -> {
                viewModelScope.launch {
                    WearTelemetryRepository.sendCommandToPhoneHost(
                        getApplication(),
                        "REPLAY_VOICE_PROMPT"
                    )
                }
            }
            ScreenMode.FAST_REROUTING -> {
                userSwipedModeOverride = ScreenMode.TURN_BY_TURN
                _uiState.update {
                    it.copy(
                        mode = ScreenMode.TURN_BY_TURN,
                        isOffRoute = false
                    )
                }
                viewModelScope.launch {
                    WearTelemetryRepository.sendCommandToPhoneHost(
                        getApplication(),
                        "FORCE_RECALCULATE"
                    )
                }
            }
            ScreenMode.SPEEDO,
            ScreenMode.JOURNEY_PROGRESS -> {
                userSwipedModeOverride = ScreenMode.TURN_BY_TURN
                _uiState.update { it.copy(mode = ScreenMode.TURN_BY_TURN) }
            }
            ScreenMode.JOURNEY_TRACKING -> {
                _uiState.update { it.copy(odometerSubPage = (it.odometerSubPage + 1) % 2) }
            }
            ScreenMode.WAYPOINT_SKIPPING -> {
                val nextWp = (current.currentWaypointIdx + 1).coerceAtMost(current.totalWaypoints)
                userSwipedModeOverride = ScreenMode.TURN_BY_TURN
                _uiState.update {
                    it.copy(
                        currentWaypointIdx = nextWp,
                        mode = ScreenMode.TURN_BY_TURN
                    )
                }
                viewModelScope.launch {
                    WearTelemetryRepository.sendCommandToPhoneHost(
                        getApplication(),
                        "SKIP_WAYPOINT:$nextWp"
                    )
                }
            }
            ScreenMode.JOURNEY_SUMMARY,
            ScreenMode.QR_ONBOARDING,
            ScreenMode.DEBUG_TELEMETRY -> {
                userSwipedModeOverride = ScreenMode.QUICK_START
                _uiState.update {
                    it.copy(
                        mode = ScreenMode.QUICK_START,
                        isTouchLocked = false
                    )
                }
            }
        }
    }

    fun setAmbientMode(isAmbient: Boolean) {
        _uiState.update { it.copy(isAmbientMode = isAmbient) }
    }

    private fun resolveCogHeading(speedKmh: Int, rawGpsCogDegrees: Int): Int {
        if (speedKmh > 5) {
            lastValidCogDegrees = ((rawGpsCogDegrees % 360) + 360) % 360
        }
        return lastValidCogDegrees
    }

    private fun start60FpsRenderAndBurnInLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(16L) // ~60fps (16.6ms)
                simulationTimeSec += 0.016f
                val nowMs = System.currentTimeMillis()
                val packetAgeMs = if (lastPhonePacketTimestampMs > 0L) {
                    nowMs - lastPhonePacketTimestampMs
                } else {
                    -1L
                }
                val isPhoneStreamActive = packetAgeMs in 0L..2_999L

                _uiState.update { current ->
                    val lissajousPhase = (simulationTimeSec / 60f) * (2f * PI.toFloat())
                    val shiftX = if (current.isAmbientMode) 3f * sin(lissajousPhase) else 0f
                    val shiftY = if (current.isAmbientMode) 3f * cos(2f * lissajousPhase) else 0f

                    if (isPhoneStreamActive) {
                        current.copy(
                            lastPacketMsAgo = packetAgeMs,
                            packetsReceivedCount = packetsReceivedCount,
                            burnInOffsetPxX = shiftX,
                            burnInOffsetPxY = shiftY
                        )
                    } else {
                        val animatedSpeed = (80 + 4f * sin(simulationTimeSec * 0.7f)).roundToInt()
                        val cog = resolveCogHeading(animatedSpeed, 330)
                        current.copy(
                            headingDegrees = cog,
                            lastPacketMsAgo = packetAgeMs,
                            packetsReceivedCount = packetsReceivedCount,
                            burnInOffsetPxX = shiftX,
                            burnInOffsetPxY = shiftY
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        messageClient.removeListener(this)
    }

    companion object {
        fun createReferenceBaselineState(): TelemetryFrameState {
            val primaryPolyline = RoutePolyline(
                isPrimaryRoute = true,
                points = listOf(
                    Vec2(0.0f, 0.05f),
                    Vec2(0.0f, -0.42f),
                    Vec2(0.05f, -0.50f),
                    Vec2(0.45f, -0.50f),
                    Vec2(0.58f, -0.62f),
                    Vec2(0.68f, -0.95f)
                )
            )
            val leftSideStreet = RoutePolyline(
                isPrimaryRoute = false,
                points = listOf(
                    Vec2(0.0f, -0.25f),
                    Vec2(-0.85f, -0.25f)
                )
            )
            val topContinuationStreet = RoutePolyline(
                isPrimaryRoute = false,
                points = listOf(
                    Vec2(0.0f, -0.48f),
                    Vec2(0.0f, -0.98f)
                )
            )
            val rightBranchStreet = RoutePolyline(
                isPrimaryRoute = false,
                points = listOf(
                    Vec2(0.45f, -0.50f),
                    Vec2(0.92f, -0.35f)
                )
            )
            val breadcrumbs = listOf(
                Vec2(0.06f, -0.14f),
                Vec2(0.14f, -0.28f),
                Vec2(0.22f, -0.42f),
                Vec2(0.30f, -0.56f)
            )

            return TelemetryFrameState(
                mode = ScreenMode.TURN_BY_TURN,
                mapVectors = listOf(
                    leftSideStreet,
                    topContinuationStreet,
                    rightBranchStreet,
                    primaryPolyline
                ),
                rerouteBreadcrumbs = breadcrumbs,
                nextManeuver = ManeuverKind.TURN_RIGHT,
                roundExitNumber = 2,
                distanceToNextMeters = 300,
                legProgressRatio = 0.62f,
                isOffRoute = false,
                currentSpeedKmh = 80,
                speedLimitKmh = 100,
                avgSpeedKmh = 72,
                speedLimitApproachRatio = 0.78f,
                remainingDurationMin = 145,
                elapsedTimeStr = "07:20",
                etaTimeStr = "09:45",
                remainingDistance = 158f,
                useImperialUnits = true,
                routeCompletionRatio = 0.72f,
                headingDegrees = 330,
                cardinalDirection = "NW",
                odometerKm = 99.9f,
                currentClockStr = "12:55 am",
                odometerSubPage = 1,
                currentWaypointIdx = 1,
                totalWaypoints = 3,
                summaryDistanceKm = 98.2f,
                summaryDurationHours = 2,
                summaryDurationMinutes = 32,
                summaryAvgSpeedKmh = 38.8f,
                isTouchLocked = false
            )
        }
    }
}

enum class EdgeTapZone {
    LEFT_9_OCLOCK,
    RIGHT_3_OCLOCK,
    BOTTOM_6_OCLOCK
}
