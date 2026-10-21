package com.motowear.phone

import com.motowear.phone.bridge.NavigationBridgeParser
import com.motowear.telemetry.ManeuverKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for `NavigationBridgeParser` verifying notification scraping from the
 * Official Beeline App (`co.beeline` v4.2.8704 `TurnNotificationType`), Google Maps, and OsmAnd.
 */
class NavigationBridgeParserTest {

    @Test
    fun `parseNotificationTexts extracts maneuver, roundabout exit, distance, and 2D vectors from Official Beeline notification`() {
        val snapshot = NavigationBridgeParser.parseNotificationTexts(
            packageName = "co.beeline",
            title = "240 m — Turn right onto Old Madras Rd",
            text = "Take 2nd exit at roundabout",
            subText = "1 hr 15 min • 48 km • 10:30"
        )

        assertEquals("Beeline Official App", snapshot.sourceAppName)
        assertEquals(240, snapshot.distanceToNextMeters)
        assertEquals(2, snapshot.roundaboutExit)
        assertEquals(75, snapshot.remainingDurationMin)
        assertEquals("10:30", snapshot.etaStr)
        assertFalse(snapshot.useImperialUnits)
        assertTrue(snapshot.syntheticMapVectors.any { it.isPrimaryRoute })
    }

    @Test
    fun `parseNotificationTexts detects imperial units and arrival notifications from co_beeline`() {
        val imperialSnapshot = NavigationBridgeParser.parseNotificationTexts(
            packageName = "co.beeline",
            title = "200 yd — Fork left onto Ridge Hwy",
            text = "Keep left at fork",
            subText = "25 min • 14 mi • 11:05"
        )
        assertTrue(imperialSnapshot.useImperialUnits)
        assertEquals(ManeuverKind.SLIGHT_LEFT, imperialSnapshot.maneuver)
        assertEquals(182, imperialSnapshot.distanceToNextMeters)

        val arrivedSnapshot = NavigationBridgeParser.parseNotificationTexts(
            packageName = "co.beeline",
            title = "Destination reached",
            text = "You have arrived at Summit Viewpoint"
        )
        assertTrue(arrivedSnapshot.hasArrived)
        assertEquals(0, arrivedSnapshot.distanceToNextMeters)
    }

    @Test
    fun `extractPrimaryDistanceMeters converts km, yd, and mi accurately to meters`() {
        assertEquals(300, NavigationBridgeParser.extractPrimaryDistanceMeters("In 300 m turn left"))
        assertEquals(1500, NavigationBridgeParser.extractPrimaryDistanceMeters("1.5 km to destination"))
        assertEquals(182, NavigationBridgeParser.extractPrimaryDistanceMeters("200 yd ahead"))
    }

    @Test
    fun `classifyManeuverFromText distinguishes sharp, slight, u-turn, and roundabout maneuvers`() {
        assertEquals(ManeuverKind.SHARP_LEFT, NavigationBridgeParser.classifyManeuverFromText("sharp left onto hairpin"))
        assertEquals(ManeuverKind.SLIGHT_RIGHT, NavigationBridgeParser.classifyManeuverFromText("keep right at fork"))
        assertEquals(ManeuverKind.UTURN, NavigationBridgeParser.classifyManeuverFromText("make a u-turn when possible"))
        assertEquals(ManeuverKind.ROUNDABOUT_RIGHT, NavigationBridgeParser.classifyManeuverFromText("enter roundabout and take exit 3"))
    }
}
