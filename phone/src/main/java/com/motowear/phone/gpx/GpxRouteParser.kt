package com.motowear.phone.gpx

import com.motowear.telemetry.ManeuverKind
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(
    val lat: Double,
    val lon: Double,
    val elevationMeters: Double = 0.0,
    val speedLimitKmh: Int = 70,
    val isWaypoint: Boolean = false,
    val name: String? = null
)

data class RouteNode(
    val index: Int,
    val point: GeoPoint,
    val cumulativeDistanceMeters: Double,
    val segmentLengthMeters: Double,
    val outgoingBearingDegrees: Double
)

data class RouteManeuverPoint(
    val nodeIndex: Int,
    val point: GeoPoint,
    val maneuver: ManeuverKind,
    val cumulativeDistanceMeters: Double,
    val turnAngleDeltaDeg: Double
)

data class ParsedGpxRoute(
    val name: String,
    val nodes: List<RouteNode>,
    val maneuvers: List<RouteManeuverPoint>,
    val waypoints: List<GeoPoint>,
    val sideStreets: List<List<GeoPoint>>,
    val totalDistanceMeters: Double
)

/**
 * Parses standard GPX 1.1 files (`<trkpt>`, `<rtept>`, `<wpt>`) into a navigable `ParsedGpxRoute`
 * with cumulative distances, segment bearings, and inferred turn-by-turn maneuvers.
 * Uses `XmlPullParserFactory` so it runs identically on Android and in JVM unit tests.
 */
object GpxRouteParser {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun parse(inputStream: InputStream): ParsedGpxRoute {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)
        return parseXml(parser)
    }

    fun parseFromString(gpxXml: String): ParsedGpxRoute {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(gpxXml))
        return parseXml(parser)
    }

    private fun parseXml(parser: XmlPullParser): ParsedGpxRoute {
        var routeName = "GPX Moto Route"
        val rawTrackPoints = mutableListOf<GeoPoint>()
        val rawWaypoints = mutableListOf<GeoPoint>()

        var currentLat: Double? = null
        var currentLon: Double? = null
        var currentEle = 0.0
        var currentPointName: String? = null
        var currentTag: String? = null
        var insideTrackOrRoutePoint = false
        var insideWaypoint = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "trkpt", "rtept" -> {
                            insideTrackOrRoutePoint = true
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            currentEle = 0.0
                            currentPointName = null
                        }
                        "wpt" -> {
                            insideWaypoint = true
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            currentEle = 0.0
                            currentPointName = null
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "name" -> {
                                if (insideTrackOrRoutePoint || insideWaypoint) {
                                    currentPointName = text
                                } else {
                                    routeName = text
                                }
                            }
                            "ele" -> {
                                currentEle = text.toDoubleOrNull() ?: 0.0
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "trkpt", "rtept" -> {
                            val lat = currentLat
                            val lon = currentLon
                            if (lat != null && lon != null) {
                                rawTrackPoints.add(
                                    GeoPoint(
                                        lat = lat,
                                        lon = lon,
                                        elevationMeters = currentEle,
                                        name = currentPointName
                                    )
                                )
                            }
                            insideTrackOrRoutePoint = false
                        }
                        "wpt" -> {
                            val lat = currentLat
                            val lon = currentLon
                            if (lat != null && lon != null) {
                                rawWaypoints.add(
                                    GeoPoint(
                                        lat = lat,
                                        lon = lon,
                                        elevationMeters = currentEle,
                                        isWaypoint = true,
                                        name = currentPointName
                                    )
                                )
                            }
                            insideWaypoint = false
                        }
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }

        if (rawTrackPoints.size < 2 && rawWaypoints.size >= 2) {
            rawTrackPoints.addAll(rawWaypoints)
        }

        return buildRouteGraph(routeName, rawTrackPoints, rawWaypoints)
    }

    fun buildRouteGraph(
        name: String,
        points: List<GeoPoint>,
        waypoints: List<GeoPoint>
    ): ParsedGpxRoute {
        if (points.isEmpty()) {
            return ParsedGpxRoute(name, emptyList(), emptyList(), emptyList(), emptyList(), 0.0)
        }

        val nodes = mutableListOf<RouteNode>()
        var cumulativeMeters = 0.0

        for (i in points.indices) {
            val current = points[i]
            val next = points.getOrNull(i + 1)
            val segLen = if (next != null) haversineMeters(current, next) else 0.0
            val bearing = if (next != null) bearingDegrees(current, next) else {
                nodes.lastOrNull()?.outgoingBearingDegrees ?: 0.0
            }
            nodes.add(
                RouteNode(
                    index = i,
                    point = current,
                    cumulativeDistanceMeters = cumulativeMeters,
                    segmentLengthMeters = segLen,
                    outgoingBearingDegrees = bearing
                )
            )
            cumulativeMeters += segLen
        }

        // Extract turn maneuvers where bearing delta exceeds 22 degrees
        val maneuvers = mutableListOf<RouteManeuverPoint>()
        val sideStreets = mutableListOf<List<GeoPoint>>()

        for (i in 1 until nodes.size - 1) {
            val prevBearing = nodes[i - 1].outgoingBearingDegrees
            val nextBearing = nodes[i].outgoingBearingDegrees
            val delta = normalizeSignedAngleDeg(nextBearing - prevBearing)

            val kind = classifyTurnDelta(delta)
            if (kind != ManeuverKind.STRAIGHT) {
                maneuvers.add(
                    RouteManeuverPoint(
                        nodeIndex = i,
                        point = nodes[i].point,
                        maneuver = kind,
                        cumulativeDistanceMeters = nodes[i].cumulativeDistanceMeters,
                        turnAngleDeltaDeg = delta
                    )
                )
                val continuationStub = projectOffsetGeoPoint(
                    origin = nodes[i].point,
                    bearingDeg = prevBearing,
                    distanceMeters = 95.0
                )
                val oppositeBranchStub = projectOffsetGeoPoint(
                    origin = nodes[i].point,
                    bearingDeg = (nextBearing + 180.0) % 360.0,
                    distanceMeters = 85.0
                )
                sideStreets.add(listOf(nodes[i].point, continuationStub))
                sideStreets.add(listOf(nodes[i].point, oppositeBranchStub))
            }
        }

        val lastNode = nodes.last()
        maneuvers.add(
            RouteManeuverPoint(
                nodeIndex = lastNode.index,
                point = lastNode.point,
                maneuver = ManeuverKind.STRAIGHT,
                cumulativeDistanceMeters = lastNode.cumulativeDistanceMeters,
                turnAngleDeltaDeg = 0.0
            )
        )

        val resolvedWaypoints = waypoints.ifEmpty {
            listOfNotNull(
                nodes.getOrNull(nodes.size / 3)?.point,
                nodes.getOrNull((nodes.size * 2) / 3)?.point,
                nodes.lastOrNull()?.point
            )
        }

        return ParsedGpxRoute(
            name = name,
            nodes = nodes,
            maneuvers = maneuvers,
            waypoints = resolvedWaypoints,
            sideStreets = sideStreets,
            totalDistanceMeters = cumulativeMeters
        )
    }

    private fun classifyTurnDelta(deltaDeg: Double): ManeuverKind {
        return when {
            deltaDeg >= 150.0 || deltaDeg <= -150.0 -> ManeuverKind.UTURN
            deltaDeg >= 115.0 -> ManeuverKind.SHARP_RIGHT
            deltaDeg >= 45.0 -> ManeuverKind.TURN_RIGHT
            deltaDeg >= 22.0 -> ManeuverKind.SLIGHT_RIGHT
            deltaDeg <= -115.0 -> ManeuverKind.SHARP_LEFT
            deltaDeg <= -45.0 -> ManeuverKind.TURN_LEFT
            deltaDeg <= -22.0 -> ManeuverKind.SLIGHT_LEFT
            else -> ManeuverKind.STRAIGHT
        }
    }

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)

        val sinLat = sin(dLat / 2.0).pow(2)
        val sinLon = sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(sinLat + cos(lat1) * cos(lat2) * sinLon), sqrt(1.0 - (sinLat + cos(lat1) * cos(lat2) * sinLon)))
        return EARTH_RADIUS_METERS * c
    }

    fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun normalizeSignedAngleDeg(angle: Double): Double {
        var a = (angle + 180.0) % 360.0
        if (a < 0.0) a += 360.0
        return a - 180.0
    }

    fun projectOffsetGeoPoint(
        origin: GeoPoint,
        bearingDeg: Double,
        distanceMeters: Double
    ): GeoPoint {
        val distRad = distanceMeters / EARTH_RADIUS_METERS
        val brgRad = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(origin.lat)
        val lon1 = Math.toRadians(origin.lon)

        val lat2 = kotlin.math.asin(
            sin(lat1) * cos(distRad) + cos(lat1) * sin(distRad) * cos(brgRad)
        )
        val lon2 = lon1 + atan2(
            sin(brgRad) * sin(distRad) * cos(lat1),
            cos(distRad) - sin(lat1) * sin(lat2)
        )
        return GeoPoint(
            lat = Math.toDegrees(lat2),
            lon = Math.toDegrees(lon2),
            elevationMeters = origin.elevationMeters,
            speedLimitKmh = origin.speedLimitKmh
        )
    }

    /**
     * Built-in sample GPX loop ("Nandi Hills Hairpin & Highway Loop") for zero-setup Phase 2 testing.
     */
    fun createSampleMotoGpxRoute(): ParsedGpxRoute {
        val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="MotoWearOS">
              <metadata><name>Nandi Hills Twisty Moto Loop</name></metadata>
              <wpt lat="13.3702" lon="77.6835"><name>Foothills Checkpoint</name></wpt>
              <wpt lat="13.3748" lon="77.6872"><name>Hairpin Bend 4</name></wpt>
              <wpt lat="13.3785" lon="77.6910"><name>Summit Viewpoint</name></wpt>
              <trk>
                <name>Nandi Hills Twisty Moto Loop</name>
                <trkseg>
                  <trkpt lat="13.3660" lon="77.6800"><ele>920</ele></trkpt>
                  <trkpt lat="13.3682" lon="77.6800"><ele>945</ele></trkpt>
                  <trkpt lat="13.3702" lon="77.6800"><ele>980</ele></trkpt>
                  <trkpt lat="13.3702" lon="77.6835"><ele>1020</ele></trkpt>
                  <trkpt lat="13.3725" lon="77.6852"><ele>1090</ele></trkpt>
                  <trkpt lat="13.3748" lon="77.6835"><ele>1160</ele></trkpt>
                  <trkpt lat="13.3748" lon="77.6872"><ele>1240</ele></trkpt>
                  <trkpt lat="13.3770" lon="77.6872"><ele>1330</ele></trkpt>
                  <trkpt lat="13.3785" lon="77.6910"><ele>1450</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()
        return parseFromString(sampleXml)
    }
}
