package com.motowear.telemetry

import com.motowear.telemetry.proto.ActiveScreenMode
import com.motowear.telemetry.proto.ManeuverType
import com.motowear.telemetry.proto.Point2D
import com.motowear.telemetry.proto.PolylineSegment
import com.motowear.telemetry.proto.RideTelemetryFrame

/**
 * Bidirectional serializer between Kotlin domain state (`TelemetryFrameState`)
 * and the compact binary Protobuf contract (`RideTelemetryFrame`) transmitted at 10Hz over `MessageClient`.
 */
object ProtobufTelemetryMapper {

    fun TelemetryFrameState.toProtoFrame(): RideTelemetryFrame {
        val builder = RideTelemetryFrame.newBuilder()
            .setMode(mode.toProtoMode())
            .setNextManeuver(nextManeuver.toProtoManeuver())
            .setRoundExitNumber(roundExitNumber)
            .setDistanceToNextM(distanceToNextMeters.coerceAtLeast(0))
            .setLegProgressRatio(legProgressRatio.coerceIn(0f, 1f))
            .setIsOffRoute(isOffRoute)
            .setCurrentSpeedKmh(currentSpeedKmh.coerceAtLeast(0))
            .setSpeedLimitKmh(speedLimitKmh.coerceAtLeast(0))
            .setAvgSpeedKmh(avgSpeedKmh.coerceAtLeast(0))
            .setSpeedLimitApproachRatio(speedLimitApproachRatio.coerceIn(0f, 1f))
            .setRemainingDurationMin(remainingDurationMin.coerceAtLeast(0))
            .setElapsedTimeStr(elapsedTimeStr)
            .setEtaTimeStr(etaTimeStr)
            .setRemainingDistance(remainingDistance.coerceAtLeast(0f))
            .setUseImperialUnits(useImperialUnits)
            .setRouteCompletionRatio(routeCompletionRatio.coerceIn(0f, 1f))
            .setHeadingDegrees(((headingDegrees % 360) + 360) % 360)
            .setCardinalDirection(cardinalDirection)
            .setOdometerKm(odometerKm.coerceAtLeast(0f))
            .setCurrentClockStr(currentClockStr)
            .setCurrentWaypointIdx(currentWaypointIdx.coerceAtLeast(1))
            .setTotalWaypoints(totalWaypoints.coerceAtLeast(1))

        mapVectors.forEach { poly ->
            val segBuilder = PolylineSegment.newBuilder()
                .setIsPrimaryRoute(poly.isPrimaryRoute)
            poly.points.forEach { pt ->
                segBuilder.addPoints(
                    Point2D.newBuilder()
                        .setX(pt.x)
                        .setY(pt.y)
                        .build()
                )
            }
            builder.addMapVectors(segBuilder.build())
        }

        rerouteBreadcrumbs.forEach { crumb ->
            builder.addRerouteBreadcrumbs(
                Point2D.newBuilder()
                    .setX(crumb.x)
                    .setY(crumb.y)
                    .build()
            )
        }

        return builder.build()
    }

    fun TelemetryFrameState.toProtoByteArray(): ByteArray = toProtoFrame().toByteArray()

    fun RideTelemetryFrame.toDomainState(
        previous: TelemetryFrameState = TelemetryFrameState()
    ): TelemetryFrameState {
        return previous.copy(
            mode = mode.toDomainMode(),
            mapVectors = mapVectorsList.map { seg ->
                RoutePolyline(
                    points = seg.pointsList.map { pt -> Vec2(pt.x, pt.y) },
                    isPrimaryRoute = seg.isPrimaryRoute
                )
            },
            rerouteBreadcrumbs = rerouteBreadcrumbsList.map { pt -> Vec2(pt.x, pt.y) },
            nextManeuver = nextManeuver.toDomainManeuver(),
            roundExitNumber = roundExitNumber,
            distanceToNextMeters = distanceToNextM,
            legProgressRatio = legProgressRatio,
            isOffRoute = isOffRoute,
            currentSpeedKmh = currentSpeedKmh,
            speedLimitKmh = speedLimitKmh,
            avgSpeedKmh = avgSpeedKmh,
            speedLimitApproachRatio = speedLimitApproachRatio,
            remainingDurationMin = remainingDurationMin,
            elapsedTimeStr = elapsedTimeStr,
            etaTimeStr = etaTimeStr,
            remainingDistance = remainingDistance,
            useImperialUnits = useImperialUnits,
            routeCompletionRatio = routeCompletionRatio,
            headingDegrees = headingDegrees,
            cardinalDirection = cardinalDirection,
            odometerKm = odometerKm,
            currentClockStr = currentClockStr,
            currentWaypointIdx = currentWaypointIdx.coerceAtLeast(1),
            totalWaypoints = totalWaypoints.coerceAtLeast(1)
        )
    }

    fun fromProtoByteArray(
        bytes: ByteArray,
        previous: TelemetryFrameState = TelemetryFrameState()
    ): TelemetryFrameState {
        return RideTelemetryFrame.parseFrom(bytes).toDomainState(previous)
    }

    private fun ScreenMode.toProtoMode(): ActiveScreenMode = when (this) {
        ScreenMode.QUICK_START -> ActiveScreenMode.QUICK_START
        ScreenMode.TURN_BY_TURN -> ActiveScreenMode.TURN_BY_TURN
        ScreenMode.FAST_REROUTING -> ActiveScreenMode.FAST_REROUTING
        ScreenMode.SPEEDO -> ActiveScreenMode.SPEEDO
        ScreenMode.JOURNEY_PROGRESS -> ActiveScreenMode.JOURNEY_PROGRESS
        ScreenMode.JOURNEY_TRACKING -> ActiveScreenMode.JOURNEY_TRACKING
        ScreenMode.WAYPOINT_SKIPPING -> ActiveScreenMode.WAYPOINT_SKIPPING
        ScreenMode.JOURNEY_SUMMARY -> ActiveScreenMode.JOURNEY_SUMMARY
    }

    private fun ActiveScreenMode.toDomainMode(): ScreenMode = when (this) {
        ActiveScreenMode.QUICK_START -> ScreenMode.QUICK_START
        ActiveScreenMode.TURN_BY_TURN -> ScreenMode.TURN_BY_TURN
        ActiveScreenMode.FAST_REROUTING -> ScreenMode.FAST_REROUTING
        ActiveScreenMode.SPEEDO -> ScreenMode.SPEEDO
        ActiveScreenMode.JOURNEY_PROGRESS -> ScreenMode.JOURNEY_PROGRESS
        ActiveScreenMode.JOURNEY_TRACKING -> ScreenMode.JOURNEY_TRACKING
        ActiveScreenMode.WAYPOINT_SKIPPING -> ScreenMode.WAYPOINT_SKIPPING
        ActiveScreenMode.JOURNEY_SUMMARY -> ScreenMode.JOURNEY_SUMMARY
        ActiveScreenMode.UNRECOGNIZED -> ScreenMode.TURN_BY_TURN
    }

    private fun ManeuverKind.toProtoManeuver(): ManeuverType = when (this) {
        ManeuverKind.STRAIGHT -> ManeuverType.STRAIGHT
        ManeuverKind.TURN_LEFT -> ManeuverType.TURN_LEFT
        ManeuverKind.TURN_RIGHT -> ManeuverType.TURN_RIGHT
        ManeuverKind.SLIGHT_LEFT -> ManeuverType.SLIGHT_LEFT
        ManeuverKind.SLIGHT_RIGHT -> ManeuverType.SLIGHT_RIGHT
        ManeuverKind.SHARP_LEFT -> ManeuverType.SHARP_LEFT
        ManeuverKind.SHARP_RIGHT -> ManeuverType.SHARP_RIGHT
        ManeuverKind.UTURN -> ManeuverType.UTURN
        ManeuverKind.ROUNDABOUT_LEFT -> ManeuverType.ROUNDABOUT_LEFT
        ManeuverKind.ROUNDABOUT_RIGHT -> ManeuverType.ROUNDABOUT_RIGHT
    }

    private fun ManeuverType.toDomainManeuver(): ManeuverKind = when (this) {
        ManeuverType.STRAIGHT -> ManeuverKind.STRAIGHT
        ManeuverType.TURN_LEFT -> ManeuverKind.TURN_LEFT
        ManeuverType.TURN_RIGHT -> ManeuverKind.TURN_RIGHT
        ManeuverType.SLIGHT_LEFT -> ManeuverKind.SLIGHT_LEFT
        ManeuverType.SLIGHT_RIGHT -> ManeuverKind.SLIGHT_RIGHT
        ManeuverType.SHARP_LEFT -> ManeuverKind.SHARP_LEFT
        ManeuverType.SHARP_RIGHT -> ManeuverKind.SHARP_RIGHT
        ManeuverType.UTURN -> ManeuverKind.UTURN
        ManeuverType.ROUNDABOUT_LEFT -> ManeuverKind.ROUNDABOUT_LEFT
        ManeuverType.ROUNDABOUT_RIGHT -> ManeuverKind.ROUNDABOUT_RIGHT
        ManeuverType.UNRECOGNIZED -> ManeuverKind.STRAIGHT
    }
}
