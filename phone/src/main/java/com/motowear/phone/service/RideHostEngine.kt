package com.motowear.phone.service

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.motowear.phone.BroadcastMetrics
import com.motowear.phone.TelemetryBroadcaster
import com.motowear.phone.bridge.ExternalNavBridgeRepository
import com.motowear.phone.bridge.ExternalNavSnapshot
import com.motowear.phone.bridge.NavigationBridgeParser
import com.motowear.phone.gpx.GeoPoint
import com.motowear.phone.gpx.GpxRouteParser
import com.motowear.phone.gpx.ParsedGpxRoute
import com.motowear.phone.location.GpsCourseSnapshot
import com.motowear.phone.location.LiveGpsLocationTracker
import com.motowear.phone.projection.LocalVectorProjector
import com.motowear.telemetry.ScreenMode
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.telemetry.TelemetryPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dual-Ingest Android Host Routing Engine:
 * - Ingest Path 1: Native GPX Route Engine + Local 2D Vector Projector (`GpxRouteParser` + `LocalVectorProjector`).
 * - Ingest Path 2: Official Beeline App (`co.beeline`), Google Maps (`com.google.android.apps.maps`),
 *   and OsmAnd (`net.osmand`) Notification Bridge (`ExternalNavBridgeRepository`) fused with 10Hz
 *   `FusedLocationProviderClient` GPS Course-Over-Ground (`COG`).
 */
class RideHostEngine(context: Context) : MessageClient.OnMessageReceivedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val broadcaster = TelemetryBroadcaster(appContext)
    private val gpsTracker = LiveGpsLocationTracker(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)

    private val _activeRoute = MutableStateFlow(GpxRouteParser.createSampleMotoGpxRoute())
    val activeRoute: StateFlow<ParsedGpxRoute> = _activeRoute.asStateFlow()

    private val _telemetryState = MutableStateFlow(TelemetryFrameState(mode = ScreenMode.TURN_BY_TURN))
    val telemetryState: StateFlow<TelemetryFrameState> = _telemetryState.asStateFlow()

    private val _broadcastMetrics = MutableStateFlow(BroadcastMetrics())
    val broadcastMetrics: StateFlow<BroadcastMetrics> = _broadcastMetrics.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _useLiveHardwareGps = MutableStateFlow(false)
    val useLiveHardwareGps: StateFlow<Boolean> = _useLiveHardwareGps.asStateFlow()

    private val _simulateOffRouteDetour = MutableStateFlow(false)
    val simulateOffRouteDetour: StateFlow<Boolean> = _simulateOffRouteDetour.asStateFlow()

    val externalNavSnapshot: StateFlow<ExternalNavSnapshot?> = ExternalNavBridgeRepository.latestSnapshot

    private var streamJob: Job? = null
    private var liveGpsJob: Job? = null
    private var latestHardwareGpsSnapshot: GpsCourseSnapshot? = null

    private var distanceAlongRouteMeters = 0.0
    private var simulatedSpeedKmh = 78
    private var lastValidCogDegrees = 0.0
    private var offRouteDurationSeconds = 0.0
    private var lastSpoken300mManeuverIdx = -1
    private var lastSpoken50mManeuverIdx = -1
    private var rideStartEpochMs = System.currentTimeMillis()

    init {
        messageClient.addListener(this)
        updateTelemetrySnapshot(dtSeconds = 0.0)
    }

    fun loadGpxFromStream(inputStream: InputStream) {
        val parsed = GpxRouteParser.parse(inputStream)
        if (parsed.nodes.size >= 2) {
            ExternalNavBridgeRepository.clearSnapshot()
            _activeRoute.value = parsed
            resetProgress()
        }
    }

    fun loadSampleGpxRoute() {
        ExternalNavBridgeRepository.clearSnapshot()
        _activeRoute.value = GpxRouteParser.createSampleMotoGpxRoute()
        resetProgress()
    }

    /**
     * Injects a simulated Official Beeline App (`co.beeline`) turn notification so the rider can test
     * Ingest Path 2 immediately without starting a live road trip.
     */
    fun triggerSimulatedOfficialBeelineAppPacket() {
        val snapshot = NavigationBridgeParser.parseNotificationTexts(
            packageName = "co.beeline",
            title = "180 m — Sharp right onto Mountain Pass Rd",
            text = "Exit 2 at roundabout, then sharp right",
            subText = "38 min • 26.4 km • ETA 10:15"
        )
        ExternalNavBridgeRepository.publishSnapshot(snapshot)
        updateTelemetrySnapshot(dtSeconds = 0.0)
    }

    fun clearExternalBridgeOverride() {
        ExternalNavBridgeRepository.clearSnapshot()
        updateTelemetrySnapshot(dtSeconds = 0.0)
    }

    fun resetProgress() {
        distanceAlongRouteMeters = 0.0
        offRouteDurationSeconds = 0.0
        lastSpoken300mManeuverIdx = -1
        lastSpoken50mManeuverIdx = -1
        rideStartEpochMs = System.currentTimeMillis()
        updateTelemetrySnapshot(dtSeconds = 0.0)
    }

    fun setUseLiveHardwareGps(enabled: Boolean) {
        _useLiveHardwareGps.value = enabled
        liveGpsJob?.cancel()
        liveGpsJob = null
        if (enabled) {
            liveGpsJob = scope.launch {
                runCatching {
                    gpsTracker.observeGpsTelemetry(intervalMs = 100L).collect { snapshot ->
                        latestHardwareGpsSnapshot = snapshot
                    }
                }
            }
        }
    }

    fun setSimulatedOffRouteDetour(enabled: Boolean) {
        _simulateOffRouteDetour.value = enabled
        if (!enabled) {
            offRouteDurationSeconds = 0.0
        }
    }

    fun setOverrideScreenMode(mode: ScreenMode) {
        _telemetryState.update { it.copy(mode = mode) }
    }

    fun setSimulatedSpeedKmh(speedKmh: Int) {
        simulatedSpeedKmh = speedKmh.coerceIn(0, 180)
    }

    fun startStreaming() {
        if (_isStreaming.value) return
        _isStreaming.value = true
        rideStartEpochMs = System.currentTimeMillis()

        runCatching {
            val sourceLabel = externalNavSnapshot.value?.sourceAppName ?: _activeRoute.value.name
            RideForegroundService.start(
                appContext,
                "Active source: $sourceLabel"
            )
        }

        streamJob = scope.launch {
            while (isActive && _isStreaming.value) {
                val currentFrame = _telemetryState.value
                val isHighFrequencyTurnLeg = currentFrame.distanceToNextMeters <= 400 || currentFrame.isOffRoute
                val intervalMs = if (isHighFrequencyTurnLeg) 100L else 1000L
                val dtSec = intervalMs / 1000.0

                updateTelemetrySnapshot(dtSeconds = dtSec)
                val metrics = broadcaster.broadcastTelemetryState(_telemetryState.value)
                _broadcastMetrics.value = metrics

                delay(intervalMs)
            }
        }
    }

    fun stopStreaming() {
        _isStreaming.value = false
        streamJob?.cancel()
        streamJob = null
        runCatching {
            RideForegroundService.stop(appContext)
        }
    }

    private fun updateTelemetrySnapshot(dtSeconds: Double) {
        val liveSnapshot = if (_useLiveHardwareGps.value) latestHardwareGpsSnapshot else null
        val activeSpeedKmh = liveSnapshot?.speedKmh ?: simulatedSpeedKmh
        if (liveSnapshot != null) {
            lastValidCogDegrees = liveSnapshot.courseOverGroundDegrees
        }

        // Check Ingest Path 2 first: Is an external navigation app (Official Beeline / Google Maps / OsmAnd) active?
        val bridged = externalNavSnapshot.value
        if (bridged != null) {
            val headingInt = lastValidCogDegrees.roundToInt() % 360
            val clockFormat = SimpleDateFormat("hh:mm a", Locale.US)
            val clockStr = clockFormat.format(Date()).lowercase(Locale.US)
            val elapsedMinutes = ((System.currentTimeMillis() - rideStartEpochMs) / 60_000L).toInt()
            val elapsedStr = String.format(Locale.US, "%02d:%02d", elapsedMinutes / 60, elapsedMinutes % 60)

            _telemetryState.update { prev ->
                prev.copy(
                    mode = if (bridged.isRerouting) ScreenMode.FAST_REROUTING else prev.mode,
                    mapVectors = bridged.syntheticMapVectors,
                    nextManeuver = bridged.maneuver,
                    roundExitNumber = bridged.roundaboutExit,
                    distanceToNextMeters = bridged.distanceToNextMeters,
                    legProgressRatio = bridged.legProgressRatio,
                    isOffRoute = bridged.isRerouting,
                    currentSpeedKmh = activeSpeedKmh,
                    speedLimitKmh = 70,
                    avgSpeedKmh = (activeSpeedKmh * 0.9f).roundToInt(),
                    speedLimitApproachRatio = (activeSpeedKmh / 70f).coerceIn(0f, 1f),
                    remainingDurationMin = bridged.remainingDurationMin,
                    elapsedTimeStr = elapsedStr,
                    etaTimeStr = bridged.etaStr,
                    remainingDistance = bridged.remainingDistanceKm,
                    headingDegrees = headingInt,
                    cardinalDirection = LocalVectorProjector.cardinalFromDegrees(headingInt),
                    currentClockStr = clockStr
                )
            }
            return
        }

        // Ingest Path 1: Native GPX Route Engine + Local 2D Vector Projector
        val route = _activeRoute.value
        if (route.nodes.size < 2) return

        var riderGeoPoint: GeoPoint
        if (liveSnapshot != null) {
            riderGeoPoint = liveSnapshot.position
        } else {
            val metersPerSecond = activeSpeedKmh / 3.6
            distanceAlongRouteMeters += metersPerSecond * dtSeconds
            if (distanceAlongRouteMeters >= route.totalDistanceMeters) {
                distanceAlongRouteMeters = 0.0
                lastSpoken300mManeuverIdx = -1
                lastSpoken50mManeuverIdx = -1
            }

            val activeNodeIdx = route.nodes.indexOfLast { it.cumulativeDistanceMeters <= distanceAlongRouteMeters }
                .coerceIn(0, route.nodes.lastIndex - 1)
            val nodeA = route.nodes[activeNodeIdx]
            val nodeB = route.nodes[activeNodeIdx + 1]
            val segFraction = if (nodeA.segmentLengthMeters > 0.1) {
                ((distanceAlongRouteMeters - nodeA.cumulativeDistanceMeters) / nodeA.segmentLengthMeters).coerceIn(0.0, 1.0)
            } else {
                0.0
            }

            riderGeoPoint = GeoPoint(
                lat = nodeA.point.lat + (nodeB.point.lat - nodeA.point.lat) * segFraction,
                lon = nodeA.point.lon + (nodeB.point.lon - nodeA.point.lon) * segFraction,
                elevationMeters = nodeA.point.elevationMeters,
                speedLimitKmh = nodeA.point.speedLimitKmh
            )

            if (activeSpeedKmh > 5) {
                lastValidCogDegrees = nodeA.outgoingBearingDegrees
            }
        }

        if (_simulateOffRouteDetour.value) {
            riderGeoPoint = GpxRouteParser.projectOffsetGeoPoint(
                origin = riderGeoPoint,
                bearingDeg = (lastValidCogDegrees + 90.0) % 360.0,
                distanceMeters = 55.0
            )
        }

        val projectedSlice = LocalVectorProjector.projectRouteWindow(
            route = route,
            riderPosition = riderGeoPoint,
            headingCogDegrees = lastValidCogDegrees
        )

        if (liveSnapshot != null) {
            val matchedNode = route.nodes.getOrNull(projectedSlice.nearestNodeIndex)
            if (matchedNode != null) {
                distanceAlongRouteMeters = matchedNode.cumulativeDistanceMeters
            }
        }

        if (projectedSlice.isOffRoute) {
            offRouteDurationSeconds += dtSeconds
        } else {
            offRouteDurationSeconds = 0.0
        }

        val isReroutingActive = projectedSlice.isOffRoute && offRouteDurationSeconds >= 3.0

        val upcomingManeuver = route.maneuvers.firstOrNull {
            it.cumulativeDistanceMeters >= distanceAlongRouteMeters
        } ?: route.maneuvers.last()
        val prevManeuverDist = route.maneuvers
            .lastOrNull { it.cumulativeDistanceMeters < distanceAlongRouteMeters }
            ?.cumulativeDistanceMeters ?: 0.0

        val distToNextMeters = (upcomingManeuver.cumulativeDistanceMeters - distanceAlongRouteMeters)
            .roundToInt()
            .coerceAtLeast(0)
        val legTotalMeters = (upcomingManeuver.cumulativeDistanceMeters - prevManeuverDist).coerceAtLeast(1.0)
        val legProgress = ((distanceAlongRouteMeters - prevManeuverDist) / legTotalMeters)
            .toFloat()
            .coerceIn(0f, 1f)

        if (_isStreaming.value && !isReroutingActive) {
            if (distToNextMeters in 285..315 && lastSpoken300mManeuverIdx != upcomingManeuver.nodeIndex) {
                lastSpoken300mManeuverIdx = upcomingManeuver.nodeIndex
                broadcaster.speakTurnPrompt(upcomingManeuver.maneuver, 300)
            } else if (distToNextMeters in 40..60 && lastSpoken50mManeuverIdx != upcomingManeuver.nodeIndex) {
                lastSpoken50mManeuverIdx = upcomingManeuver.nodeIndex
                broadcaster.speakTurnPrompt(upcomingManeuver.maneuver, 50)
            }
        }

        val elapsedMinutes = ((System.currentTimeMillis() - rideStartEpochMs) / 60_000L).toInt()
        val elapsedStr = String.format(Locale.US, "%02d:%02d", elapsedMinutes / 60, elapsedMinutes % 60)
        val remainingMeters = (route.totalDistanceMeters - distanceAlongRouteMeters).coerceAtLeast(0.0)
        val remainingMin = ((remainingMeters / 1000.0) / (activeSpeedKmh.coerceAtLeast(20) / 60.0)).roundToInt()
        val clockFormat = SimpleDateFormat("hh:mm a", Locale.US)
        val clockStr = clockFormat.format(Date()).lowercase(Locale.US)
        val headingInt = lastValidCogDegrees.roundToInt() % 360

        _telemetryState.update { prev ->
            val resolvedMode = when {
                isReroutingActive -> ScreenMode.FAST_REROUTING
                prev.mode == ScreenMode.FAST_REROUTING && !isReroutingActive -> ScreenMode.TURN_BY_TURN
                else -> prev.mode
            }
            prev.copy(
                mode = resolvedMode,
                mapVectors = projectedSlice.mapVectors,
                rerouteBreadcrumbs = projectedSlice.rerouteBreadcrumbs,
                nextManeuver = upcomingManeuver.maneuver,
                distanceToNextMeters = distToNextMeters,
                legProgressRatio = legProgress,
                isOffRoute = isReroutingActive,
                currentSpeedKmh = activeSpeedKmh,
                speedLimitKmh = riderGeoPoint.speedLimitKmh,
                avgSpeedKmh = (activeSpeedKmh * 0.9f).roundToInt(),
                speedLimitApproachRatio = (activeSpeedKmh.toFloat() / riderGeoPoint.speedLimitKmh.coerceAtLeast(1))
                    .coerceIn(0f, 1f),
                remainingDurationMin = remainingMin.coerceAtLeast(1),
                elapsedTimeStr = elapsedStr,
                etaTimeStr = "09:45",
                remainingDistance = (remainingMeters / 1000.0).toFloat(),
                routeCompletionRatio = (distanceAlongRouteMeters / route.totalDistanceMeters.coerceAtLeast(1.0))
                    .toFloat()
                    .coerceIn(0f, 1f),
                headingDegrees = headingInt,
                cardinalDirection = LocalVectorProjector.cardinalFromDegrees(headingInt),
                odometerKm = (99.9f + (distanceAlongRouteMeters / 1000.0).toFloat()),
                currentClockStr = clockStr,
                totalWaypoints = route.waypoints.size.coerceAtLeast(1)
            )
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == TelemetryPaths.WATCH_COMMAND_PATH) {
            val command = String(messageEvent.data, Charsets.UTF_8)
            when {
                command == "REPLAY_VOICE_PROMPT" -> {
                    val current = _telemetryState.value
                    broadcaster.speakTurnPrompt(current.nextManeuver, current.distanceToNextMeters)
                }
                command == "FORCE_RECALCULATE" -> {
                    setSimulatedOffRouteDetour(false)
                }
                command.startsWith("SKIP_WAYPOINT:") -> {
                    val targetWp = command.substringAfter("SKIP_WAYPOINT:").toIntOrNull() ?: 1
                    _telemetryState.update {
                        it.copy(
                            currentWaypointIdx = targetWp.coerceIn(1, it.totalWaypoints),
                            mode = ScreenMode.TURN_BY_TURN
                        )
                    }
                }
            }
        }
    }

    fun cleanup() {
        stopStreaming()
        liveGpsJob?.cancel()
        messageClient.removeListener(this)
        broadcaster.shutdown()
    }
}
