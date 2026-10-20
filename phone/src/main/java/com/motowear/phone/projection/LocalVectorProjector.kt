package com.motowear.phone.projection

import com.motowear.phone.gpx.GeoPoint
import com.motowear.phone.gpx.GpxRouteParser
import com.motowear.phone.gpx.ParsedGpxRoute
import com.motowear.telemetry.RoutePolyline
import com.motowear.telemetry.Vec2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class ProjectedMapSlice(
    val mapVectors: List<RoutePolyline>,
    val rerouteBreadcrumbs: List<Vec2>,
    val isOffRoute: Boolean,
    val crossTrackDistanceMeters: Double,
    val nearestNodeIndex: Int
)

/**
 * Section 2 & Section 4 (Modes 1 & 2): Local 2D Vector Projection & Off-Route Breadcrumb Generator.
 *
 * Converts WGS84 `(lat, lon)` route geometry around the rider into normalized `[-1.0, 1.0]`
 * coordinates anchored at the rider chevron origin and rotated by `-headingCogDegrees`
 * so the rider's Course-Over-Ground is always Straight Up `(0, -Y)`.
 */
object LocalVectorProjector {

    private const val METERS_PER_DEG_LAT = 111_132.92
    private const val DEFAULT_VIEWPORT_RADIUS_METERS = 180.0
    const val OFF_ROUTE_THRESHOLD_METERS = 35.0

    fun projectRouteWindow(
        route: ParsedGpxRoute,
        riderPosition: GeoPoint,
        headingCogDegrees: Double,
        viewportRadiusMeters: Double = DEFAULT_VIEWPORT_RADIUS_METERS
    ): ProjectedMapSlice {
        if (route.nodes.size < 2) {
            return ProjectedMapSlice(emptyList(), emptyList(), false, 0.0, 0)
        }

        // 1. Find nearest point on the route polyline and cross-track distance in meters
        var minDistanceMeters = Double.MAX_VALUE
        var nearestIdx = 0
        var nearestReentryPoint = route.nodes.first().point

        for (i in 0 until route.nodes.size - 1) {
            val a = route.nodes[i].point
            val b = route.nodes[i + 1].point
            val closest = closestPointOnSegment(riderPosition, a, b)
            val d = GpxRouteParser.haversineMeters(riderPosition, closest)
            if (d < minDistanceMeters) {
                minDistanceMeters = d
                nearestIdx = i
                nearestReentryPoint = closest
            }
        }

        val isOffRoute = minDistanceMeters > OFF_ROUTE_THRESHOLD_METERS

        // 2. Project active primary route window (from slightly behind rider to ~260m ahead)
        val startIdx = (nearestIdx - 1).coerceAtLeast(0)
        val endIdx = (nearestIdx + 6).coerceAtMost(route.nodes.lastIndex)

        val primaryPoints = mutableListOf<Vec2>()
        for (i in startIdx..endIdx) {
            val projected = latLonToHeadingUpVec2(
                origin = riderPosition,
                target = route.nodes[i].point,
                headingCogDegrees = headingCogDegrees,
                viewportRadiusMeters = viewportRadiusMeters
            )
            primaryPoints.add(projected)
        }

        val polylines = mutableListOf<RoutePolyline>()

        // 3. Project nearby side streets into 2dp grey double-line segments
        route.sideStreets.forEach { street ->
            if (street.size >= 2) {
                val distToIntersection = GpxRouteParser.haversineMeters(riderPosition, street.first())
                if (distToIntersection <= viewportRadiusMeters * 1.4) {
                    val pts = street.map { pt ->
                        latLonToHeadingUpVec2(
                            origin = riderPosition,
                            target = pt,
                            headingCogDegrees = headingCogDegrees,
                            viewportRadiusMeters = viewportRadiusMeters
                        )
                    }
                    polylines.add(RoutePolyline(points = pts, isPrimaryRoute = false))
                }
            }
        }

        polylines.add(RoutePolyline(points = primaryPoints, isPrimaryRoute = true))

        // 4. If off-route (> 35m), synthesize 4 evenly spaced circular breadcrumb dots
        // projecting from the rider chevron (0, 0) toward the nearest re-entry point on the primary polyline.
        val breadcrumbs = if (isOffRoute) {
            val targetVec = latLonToHeadingUpVec2(
                origin = riderPosition,
                target = nearestReentryPoint,
                headingCogDegrees = headingCogDegrees,
                viewportRadiusMeters = viewportRadiusMeters
            )
            (1..4).map { step ->
                val t = step / 5f
                Vec2(
                    x = (targetVec.x * t).coerceIn(-0.95f, 0.95f),
                    y = (targetVec.y * t).coerceIn(-0.95f, 0.95f)
                )
            }
        } else {
            emptyList()
        }

        return ProjectedMapSlice(
            mapVectors = polylines,
            rerouteBreadcrumbs = breadcrumbs,
            isOffRoute = isOffRoute,
            crossTrackDistanceMeters = minDistanceMeters,
            nearestNodeIndex = nearestIdx
        )
    }

    /**
     * Projects `target` relative to `origin` into local East-North meters, rotates by `headingCogDegrees`
     * so the rider's Course-Over-Ground points along `-Y` (upward on the Wear OS Canvas),
     * and normalizes by `viewportRadiusMeters` into `[-1.0, 1.0]`.
     */
    fun latLonToHeadingUpVec2(
        origin: GeoPoint,
        target: GeoPoint,
        headingCogDegrees: Double,
        viewportRadiusMeters: Double
    ): Vec2 {
        val latRad = Math.toRadians(origin.lat)
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(latRad)

        val eastMeters = (target.lon - origin.lon) * metersPerDegLon
        val northMeters = (target.lat - origin.lat) * METERS_PER_DEG_LAT

        // Rotate by +headingCogDegrees so that a point along the heading vector lies on (x = 0, y < 0)
        val rad = Math.toRadians(headingCogDegrees)
        val cosH = cos(rad)
        val sinH = sin(rad)

        val rotatedRightMeters = eastMeters * cosH - northMeters * sinH
        val rotatedForwardMeters = eastMeters * sinH + northMeters * cosH

        val normX = (rotatedRightMeters / viewportRadiusMeters).toFloat().coerceIn(-1.25f, 1.25f)
        val normY = (-rotatedForwardMeters / viewportRadiusMeters).toFloat().coerceIn(-1.25f, 1.25f)

        return Vec2(x = normX, y = normY)
    }

    private fun closestPointOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): GeoPoint {
        val latRad = Math.toRadians(p.lat)
        val lonScale = cos(latRad)

        val ax = a.lon * lonScale
        val ay = a.lat
        val bx = b.lon * lonScale
        val by = b.lat
        val px = p.lon * lonScale
        val py = p.lat

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-12) return a

        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        return GeoPoint(
            lat = a.lat + t * (b.lat - a.lat),
            lon = a.lon + t * (b.lon - a.lon),
            elevationMeters = a.elevationMeters + t * (b.elevationMeters - a.elevationMeters),
            speedLimitKmh = a.speedLimitKmh
        )
    }

    fun cardinalFromDegrees(deg: Int): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val normalized = ((deg % 360) + 360) % 360
        val idx = ((normalized + 22) / 45) % 8
        return dirs[idx]
    }
}
