package com.motowear.phone.bridge

import com.motowear.telemetry.ManeuverKind
import com.motowear.telemetry.RoutePolyline
import com.motowear.telemetry.Vec2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExternalNavSnapshot(
    val sourcePackage: String,
    val sourceAppName: String,
    val maneuver: ManeuverKind,
    val roundaboutExit: Int = 0,
    val distanceToNextMeters: Int,
    val legProgressRatio: Float,
    val etaStr: String = "09:45",
    val remainingDurationMin: Int = 45,
    val remainingDistanceKm: Float = 24.0f,
    val useImperialUnits: Boolean = false,
    val isRerouting: Boolean = false,
    val hasArrived: Boolean = false,
    val syntheticMapVectors: List<RoutePolyline> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
)

object ExternalNavBridgeRepository {
    private val _latestSnapshot = MutableStateFlow<ExternalNavSnapshot?>(null)
    val latestSnapshot: StateFlow<ExternalNavSnapshot?> = _latestSnapshot.asStateFlow()

    fun publishSnapshot(snapshot: ExternalNavSnapshot) {
        _latestSnapshot.value = snapshot
    }

    fun clearSnapshot() {
        _latestSnapshot.value = null
    }
}

/**
 * Pure Kotlin parser aligned with `co.beeline.navigation.notifications.TurnNotificationType`
 * (verified from official `co.beeline` v4.2.8704), Google Maps (`com.google.android.apps.maps`),
 * and OsmAnd (`net.osmand`).
 */
object NavigationBridgeParser {

    val SUPPORTED_NAV_PACKAGES = mapOf(
        "co.beeline" to "Beeline Official App",
        "com.beeline" to "Beeline Official App",
        "com.google.android.apps.maps" to "Google Maps",
        "net.osmand" to "OsmAnd",
        "net.osmand.plus" to "OsmAnd+"
    )

    private val DISTANCE_REGEX =
        Regex("""(\d+(?:[.,]\d+)?)\s*(km|m|mi|yd|ft)\b""", RegexOption.IGNORE_CASE)
    private val EXIT_REGEX =
        Regex("""exit\s*#?\s*(\d+)|(\d+)(?:st|nd|rd|th)\s+exit""", RegexOption.IGNORE_CASE)
    private val DURATION_HR_MIN_REGEX =
        Regex("""(?:(\d+)\s*h(?:r|rs|our|ours)?\s*)?(\d+)\s*m(?:in|ins|inute|inutes)?\b""", RegexOption.IGNORE_CASE)
    private val ETA_TIME_REGEX =
        Regex("""\b(\d{1,2}:\d{2}(?:\s*[ap]m)?)\b""", RegexOption.IGNORE_CASE)

    fun isSupportedPackage(packageName: String): Boolean {
        return SUPPORTED_NAV_PACKAGES.containsKey(packageName) ||
            packageName.contains("beeline", ignoreCase = true)
    }

    fun parseNotificationTexts(
        packageName: String,
        title: String,
        text: String,
        subText: String = "",
        progress: Int = 0,
        progressMax: Int = 0
    ): ExternalNavSnapshot {
        val combined = "$title | $text | $subText"
        val lower = combined.lowercase()

        val isRerouting = lower.contains("rerouting") ||
            lower.contains("recalculating") ||
            lower.contains("off route") ||
            lower.contains("back on route")

        val hasArrived = lower.contains("arrived") ||
            lower.contains("destination reached") ||
            lower.contains("finish")

        val distanceMeters = extractPrimaryDistanceMeters(title)
            ?: extractPrimaryDistanceMeters(text)
            ?: if (hasArrived) 0 else 250

        val usesImperial = DISTANCE_REGEX.find(combined)?.groupValues?.get(2)?.lowercase() in setOf("mi", "yd", "ft")

        val maneuver = classifyManeuverFromText(lower)
        val roundExit = EXIT_REGEX.find(lower)?.let { match ->
            (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull() ?: 0
        } ?: 0

        val etaStr = ETA_TIME_REGEX.find(subText)?.groupValues?.get(1)
            ?: ETA_TIME_REGEX.find(text)?.groupValues?.get(1)
            ?: "09:45"

        val remainingMin = DURATION_HR_MIN_REGEX.find(subText)?.let { m ->
            val hrs = m.groupValues[1].toIntOrNull() ?: 0
            val mins = m.groupValues[2].toIntOrNull() ?: 0
            (hrs * 60) + mins
        } ?: 35

        val legProgress = if (progressMax > 0) {
            (progress.toFloat() / progressMax.toFloat()).coerceIn(0f, 1f)
        } else {
            (1f - (distanceMeters.coerceIn(0, 500) / 500f)).coerceIn(0.08f, 0.96f)
        }

        val appName = SUPPORTED_NAV_PACKAGES[packageName] ?: "Beeline Official App"

        return ExternalNavSnapshot(
            sourcePackage = packageName,
            sourceAppName = appName,
            maneuver = maneuver,
            roundaboutExit = roundExit,
            distanceToNextMeters = distanceMeters,
            legProgressRatio = legProgress,
            etaStr = etaStr,
            remainingDurationMin = remainingMin,
            remainingDistanceKm = (distanceMeters / 1000f).coerceAtLeast(1.2f),
            useImperialUnits = usesImperial,
            isRerouting = isRerouting,
            hasArrived = hasArrived,
            syntheticMapVectors = synthesize2DTurnVectors(maneuver, distanceMeters)
        )
    }

    fun extractPrimaryDistanceMeters(input: String): Int? {
        val match = DISTANCE_REGEX.find(input) ?: return null
        val rawNumber = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()
        val meters = when (unit) {
            "km" -> rawNumber * 1000.0
            "m" -> rawNumber
            "mi" -> rawNumber * 1609.34
            "yd" -> rawNumber * 0.9144
            "ft" -> rawNumber * 0.3048
            else -> rawNumber
        }
        return meters.toInt().coerceAtLeast(0)
    }

    fun classifyManeuverFromText(lowerText: String): ManeuverKind {
        return when {
            lowerText.contains("u-turn") || lowerText.contains("uturn") || lowerText.contains("make a u") ->
                ManeuverKind.UTURN
            (lowerText.contains("roundabout") || lowerText.contains("rotary") || lowerText.contains("traffic circle")) &&
                lowerText.contains("left") ->
                ManeuverKind.ROUNDABOUT_LEFT
            lowerText.contains("roundabout") || lowerText.contains("rotary") || lowerText.contains("traffic circle") ->
                ManeuverKind.ROUNDABOUT_RIGHT
            lowerText.contains("sharp left") || lowerText.contains("hard left") || lowerText.contains("hairpin left") ->
                ManeuverKind.SHARP_LEFT
            lowerText.contains("sharp right") || lowerText.contains("hard right") || lowerText.contains("hairpin right") ->
                ManeuverKind.SHARP_RIGHT
            lowerText.contains("slight left") || lowerText.contains("keep left") ||
                lowerText.contains("bear left") || lowerText.contains("fork left") ->
                ManeuverKind.SLIGHT_LEFT
            lowerText.contains("slight right") || lowerText.contains("keep right") ||
                lowerText.contains("bear right") || lowerText.contains("fork right") ->
                ManeuverKind.SLIGHT_RIGHT
            lowerText.contains("left") ->
                ManeuverKind.TURN_LEFT
            lowerText.contains("right") ->
                ManeuverKind.TURN_RIGHT
            else ->
                ManeuverKind.STRAIGHT
        }
    }

    /**
     * Synthesizes a local 2D heading-up intersection polyline + side streets so external
     * notification-bridged apps render a smooth approaching turn on the Wear OS vector canvas.
     */
    fun synthesize2DTurnVectors(
        maneuver: ManeuverKind,
        distanceToNextMeters: Int
    ): List<RoutePolyline> {
        val turnY = (-0.12f - (distanceToNextMeters.coerceIn(0, 350) / 350f) * 0.42f)
            .coerceIn(-0.62f, -0.10f)

        val endVec = when (maneuver) {
            ManeuverKind.TURN_LEFT -> Vec2(-0.68f, turnY)
            ManeuverKind.SLIGHT_LEFT -> Vec2(-0.48f, turnY - 0.36f)
            ManeuverKind.SHARP_LEFT -> Vec2(-0.62f, turnY + 0.25f)
            ManeuverKind.TURN_RIGHT, ManeuverKind.ROUNDABOUT_RIGHT -> Vec2(0.68f, turnY)
            ManeuverKind.SLIGHT_RIGHT -> Vec2(0.48f, turnY - 0.36f)
            ManeuverKind.SHARP_RIGHT -> Vec2(0.62f, turnY + 0.25f)
            ManeuverKind.UTURN -> Vec2(-0.28f, 0.02f)
            ManeuverKind.ROUNDABOUT_LEFT -> Vec2(-0.68f, turnY)
            ManeuverKind.STRAIGHT -> Vec2(0.0f, -0.95f)
        }

        val primary = RoutePolyline(
            isPrimaryRoute = true,
            points = listOf(
                Vec2(0.0f, 0.05f),
                Vec2(0.0f, turnY),
                endVec
            )
        )

        val forwardContinuation = RoutePolyline(
            isPrimaryRoute = false,
            points = listOf(
                Vec2(0.0f, turnY),
                Vec2(0.0f, -0.98f)
            )
        )

        val crossStreet = RoutePolyline(
            isPrimaryRoute = false,
            points = listOf(
                Vec2(-0.85f, turnY),
                Vec2(0.85f, turnY)
            )
        )

        return listOf(forwardContinuation, crossStreet, primary)
    }
}
