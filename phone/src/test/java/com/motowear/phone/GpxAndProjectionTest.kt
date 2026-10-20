package com.motowear.phone

import com.motowear.phone.gpx.GeoPoint
import com.motowear.phone.gpx.GpxRouteParser
import com.motowear.phone.projection.LocalVectorProjector
import com.motowear.telemetry.ManeuverKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite for Phase 2 & 3 GPX route geometry, turn maneuver inference,
 * heading-up 2D local vector projection, and >35m off-route breadcrumb generation.
 */
class GpxAndProjectionTest {

    @Test
    fun `haversineMeters and bearingDegrees compute accurate geodesic distance and heading`() {
        val origin = GeoPoint(lat = 13.3660, lon = 77.6800)
        // Due North ~222.4 meters
        val northTarget = GeoPoint(lat = 13.3680, lon = 77.6800)

        val distance = GpxRouteParser.haversineMeters(origin, northTarget)
        val bearing = GpxRouteParser.bearingDegrees(origin, northTarget)

        assertEquals(222.4, distance, 2.0)
        assertEquals(0.0, bearing, 0.5)
    }

    @Test
    fun `buildRouteGraph extracts right and left turn maneuvers from GPX track vertices`() {
        // L-shaped track: North for ~220m, then 90-degree East right turn for ~220m
        val points = listOf(
            GeoPoint(lat = 13.3660, lon = 77.6800),
            GeoPoint(lat = 13.3680, lon = 77.6800),
            GeoPoint(lat = 13.3680, lon = 77.6820)
        )
        val route = GpxRouteParser.buildRouteGraph(
            name = "Right Turn Test",
            points = points,
            waypoints = emptyList()
        )

        assertEquals(3, route.nodes.size)
        assertTrue(route.totalDistanceMeters > 400.0)
        // First maneuver at node index 1 should be a ~90° TURN_RIGHT
        val firstTurn = route.maneuvers.first()
        assertEquals(1, firstTurn.nodeIndex)
        assertEquals(ManeuverKind.TURN_RIGHT, firstTurn.maneuver)
        assertEquals(90.0, firstTurn.turnAngleDeltaDeg, 2.0)
    }

    @Test
    fun `latLonToHeadingUpVec2 rotates forward point along Course-Over-Ground to negative Y axis`() {
        val origin = GeoPoint(lat = 13.3660, lon = 77.6800)
        // Target 90m due East (bearing 90°)
        val eastPoint = GpxRouteParser.projectOffsetGeoPoint(origin, bearingDeg = 90.0, distanceMeters = 90.0)

        // When rider's Course-Over-Ground is also 90° (heading East), eastPoint is straight ahead:
        // x should be ~0.0, y should be -0.5 (90m / 180m viewport radius)
        val vecAhead = LocalVectorProjector.latLonToHeadingUpVec2(
            origin = origin,
            target = eastPoint,
            headingCogDegrees = 90.0,
            viewportRadiusMeters = 180.0
        )
        assertEquals(0.0f, vecAhead.x, 0.02f)
        assertEquals(-0.5f, vecAhead.y, 0.02f)

        // When rider's Course-Over-Ground is 0° (heading North), eastPoint is 90° to the right:
        // x should be +0.5, y should be ~0.0
        val vecRight = LocalVectorProjector.latLonToHeadingUpVec2(
            origin = origin,
            target = eastPoint,
            headingCogDegrees = 0.0,
            viewportRadiusMeters = 180.0
        )
        assertEquals(0.5f, vecRight.x, 0.02f)
        assertEquals(0.0f, vecRight.y, 0.02f)
    }

    @Test
    fun `projectRouteWindow detects off-route deviation exceeding 35m and generates 4 breadcrumbs`() {
        val points = listOf(
            GeoPoint(lat = 13.3660, lon = 77.6800),
            GeoPoint(lat = 13.3680, lon = 77.6800),
            GeoPoint(lat = 13.3700, lon = 77.6800)
        )
        val route = GpxRouteParser.buildRouteGraph("Straight North", points, emptyList())

        // On-route rider (0m deviation)
        val onRouteSlice = LocalVectorProjector.projectRouteWindow(
            route = route,
            riderPosition = GeoPoint(lat = 13.3670, lon = 77.6800),
            headingCogDegrees = 0.0
        )
        assertFalse(onRouteSlice.isOffRoute)
        assertTrue(onRouteSlice.rerouteBreadcrumbs.isEmpty())

        // Off-route rider shifted 55m East (> 35m threshold)
        val offRouteRider = GpxRouteParser.projectOffsetGeoPoint(
            origin = GeoPoint(lat = 13.3670, lon = 77.6800),
            bearingDeg = 90.0,
            distanceMeters = 55.0
        )
        val offRouteSlice = LocalVectorProjector.projectRouteWindow(
            route = route,
            riderPosition = offRouteRider,
            headingCogDegrees = 0.0
        )
        assertTrue(offRouteSlice.isOffRoute)
        assertEquals(55.0, offRouteSlice.crossTrackDistanceMeters, 1.5)
        assertEquals(4, offRouteSlice.rerouteBreadcrumbs.size)
        // Since the route lies to the West (left of a North-facing rider), breadcrumb X coordinates must be negative
        assertTrue(offRouteSlice.rerouteBreadcrumbs.all { it.x < 0f })
    }
}
