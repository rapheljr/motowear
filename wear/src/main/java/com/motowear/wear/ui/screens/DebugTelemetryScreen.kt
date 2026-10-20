package com.motowear.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.wear.theme.BeelineColors

/**
 * Debug Telemetry Inspector Screen.
 * Renders real-time telemetry metrics received over MessageClient / BLE GATT from the Phone Companion app:
 * - Live packet frequency, payload timestamp latency, and stream connection status.
 * - Current speed, speed limit, COG heading, cardinal direction.
 * - Active maneuver type, roundabout exit, turn distance, leg progress ratio, reroute status.
 * - Odometer, remaining distance, ETA, elapsed time, waypoints.
 * - Vector polyline and breadcrumb node counts.
 */
@Composable
fun DebugTelemetryScreen(
    state: TelemetryFrameState,
    modifier: Modifier = Modifier,
    lastPacketMsAgo: Long = 16L,
    totalPacketsCount: Long = 142L
) {
    val isConnected = lastPacketMsAgo in 0..3000L

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BeelineColors.BeelineBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Header
        Text(
            text = "TELEMETRY DEBUG",
            color = BeelineColors.BeelineAmber,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )

        // 2. Stream Status & Packet Counter Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BeelineColors.BeelineDarkGrey)
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isConnected) "● STREAM LIVE" else "○ STREAM PAUSED",
                        color = if (isConnected) BeelineColors.BeelineWhite else BeelineColors.BeelineGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (lastPacketMsAgo >= 0) "${lastPacketMsAgo}ms" else "N/A",
                        color = BeelineColors.BeelineAmber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "Packets: $totalPacketsCount • ${state.mode.name}",
                    color = BeelineColors.BeelineWhite,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 3. Navigation & Speed State Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BeelineColors.BeelineDarkGrey)
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DebugRow("Speed", "${state.currentSpeedKmh} km/h (Limit: ${state.speedLimitKmh})")
                DebugRow("Heading", "${state.headingDegrees}° ${state.cardinalDirection}")
                DebugRow("Maneuver", "${state.nextManeuver.name} (${state.distanceToNextMeters}m)")
                DebugRow("Exit #", if (state.roundExitNumber > 0) "Exit ${state.roundExitNumber}" else "N/A")
                DebugRow("Off-Route", if (state.isOffRoute) "YES (Rerouting)" else "No")
            }
        }

        // 4. Trip & Progress Metrics Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BeelineColors.BeelineDarkGrey)
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DebugRow("Odometer", "%.1f km".format(state.odometerKm))
                DebugRow("Remaining", "%.1f km (${state.remainingDurationMin}m)".format(state.remainingDistance))
                DebugRow("ETA/Elapsed", "${state.etaTimeStr} / ${state.elapsedTimeStr}")
                DebugRow("Waypoint", "${state.currentWaypointIdx} / ${state.totalWaypoints}")
                DebugRow("Vectors", "${state.mapVectors.size} lines (${state.rerouteBreadcrumbs.size} crumbs)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = BeelineColors.BeelineGrey,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = BeelineColors.BeelineWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewDebugTelemetryScreen() {
    DebugTelemetryScreen(state = TelemetryFrameState())
}
