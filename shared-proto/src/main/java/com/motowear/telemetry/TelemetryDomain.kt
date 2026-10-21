package com.motowear.telemetry

/**
 * Immutable Kotlin domain mirrors of the Protobuf telemetry contract (`telemetry.proto`)
 * for zero-allocation 60fps Jetpack Compose rendering on Wear OS and Companion serialization.
 */
enum class ScreenMode {
    QUICK_START,
    TURN_BY_TURN,
    FAST_REROUTING,
    SPEEDO,
    JOURNEY_PROGRESS,
    JOURNEY_TRACKING,
    WAYPOINT_SKIPPING,
    JOURNEY_SUMMARY,
    DEVICE_SETTINGS,
    QR_ONBOARDING,
    DEBUG_TELEMETRY
}

enum class ManeuverKind {
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    UTURN,
    ROUNDABOUT_LEFT,
    ROUNDABOUT_RIGHT
}

data class Vec2(
    val x: Float, // Normalized [-1.0, 1.0] relative to rider chevron origin (X=50%, Y=63%)
    val y: Float  // Normalized [-1.0, 1.0] rotated to rider heading-up
)

data class RoutePolyline(
    val points: List<Vec2>,
    val isPrimaryRoute: Boolean
)

data class TelemetryFrameState(
    val mode: ScreenMode = ScreenMode.QUICK_START,

    // Navigation & Mini-Map (Modes 1 & 2)
    val mapVectors: List<RoutePolyline> = emptyList(),
    val rerouteBreadcrumbs: List<Vec2> = emptyList(),
    val nextManeuver: ManeuverKind = ManeuverKind.TURN_RIGHT,
    val roundExitNumber: Int = 2,
    val distanceToNextMeters: Int = 300,
    val legProgressRatio: Float = 0.65f,
    val isOffRoute: Boolean = false,

    // Speed & Limits (Modes 1 & 3)
    val currentSpeedKmh: Int = 80,
    val speedLimitKmh: Int = 100,
    val avgSpeedKmh: Int = 72,
    val speedLimitApproachRatio: Float = 0.80f,

    // Journey Progress & Tracking (Modes 4, 5, 7, 8)
    val remainingDurationMin: Int = 145, // 2hr 25m
    val elapsedTimeStr: String = "07:20",
    val etaTimeStr: String = "09:45",
    val remainingDistance: Float = 158.0f,
    val useImperialUnits: Boolean = false,
    val routeCompletionRatio: Float = 0.68f,
    val headingDegrees: Int = 330,
    val cardinalDirection: String = "NW",
    val odometerKm: Float = 99.9f,
    val currentClockStr: String = "12:55 am",
    val odometerSubPage: Int = 1, // 0 or 1 for (○ ●) pager

    // Waypoints (Mode 7)
    val currentWaypointIdx: Int = 1,
    val totalWaypoints: Int = 3,

    // Summary Stats (Mode 8)
    val summaryDistanceKm: Float = 98.2f,
    val summaryDurationHours: Int = 2,
    val summaryDurationMinutes: Int = 32,
    val summaryAvgSpeedKmh: Float = 38.8f,

    // Device Settings & Debug Telemetry State
    val settingsSelectedIndex: Int = 0, // 0="About", 1="Unpair", 2="Reset", 3="Legal"
    val lastPacketMsAgo: Long = -1L,
    val packetsReceivedCount: Long = 0L,

    // Hardware & Power state
    val isTouchLocked: Boolean = false,
    val isAmbientMode: Boolean = false,
    val burnInOffsetPxX: Float = 0f,
    val burnInOffsetPxY: Float = 0f
)

object TelemetryPaths {
    const val TELEMETRY_FRAME_PATH = "/motowear/telemetry/frame_v1"
    const val WATCH_COMMAND_PATH = "/motowear/command/action_v1"
}
