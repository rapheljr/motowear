package com.motowear.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motowear.phone.service.RideHostEngine
import com.motowear.phone.ui.BeelineBrandHeader
import com.motowear.phone.ui.theme.BeelinePhoneTheme
import com.motowear.telemetry.ScreenMode

/**
 * Dual-Ingest Android Companion Host Activity:
 * - Ingest Path 1: Native GPX Route Engine + 10Hz `FusedLocationProviderClient` GPS Course-Over-Ground.
 * - Ingest Path 2: Official Beeline App (`co.beeline`), Google Maps (`com.google.android.apps.maps`),
 *   and OsmAnd (`net.osmand`) Notification Bridge + BLE GATT Peripheral compatibility.
 * Refactored for 100% Material 3 Expressive Design compliance.
 */
class MainActivity : ComponentActivity() {

    private lateinit var hostEngine: RideHostEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hostEngine = RideHostEngine(this)

        setContent {
            BeelinePhoneTheme {
                val gpxPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            hostEngine.loadGpxFromStream(stream)
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    val hasLocation = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (hasLocation) {
                        hostEngine.setUseLiveHardwareGps(true)
                    }
                }

                CompanionHostDashboard(
                    engine = hostEngine,
                    onPickGpxFile = { gpxPicker.launch("*/*") },
                    onOpenNotificationListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onToggleLiveGps = { enable ->
                        if (enable) {
                            val perms = buildList {
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }.toTypedArray()
                            permissionLauncher.launch(perms)
                        } else {
                            hostEngine.setUseLiveHardwareGps(false)
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hostEngine.cleanup()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionHostDashboard(
    engine: RideHostEngine,
    onPickGpxFile: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onToggleLiveGps: (Boolean) -> Unit
) {
    val route by engine.activeRoute.collectAsState()
    val state by engine.telemetryState.collectAsState()
    val metrics by engine.broadcastMetrics.collectAsState()
    val isStreaming by engine.isStreaming.collectAsState()
    val useLiveGps by engine.useLiveHardwareGps.collectAsState()
    val isOffRouteDetour by engine.simulateOffRouteDetour.collectAsState()
    val externalBridgeSnapshot by engine.externalNavSnapshot.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Official Beeline Roundel + Wordmark + Moto II Lockup Header
            BeelineBrandHeader()

            // 1. Ingest Path 2: Official Beeline App / Google Maps / OsmAnd Bridge Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Official Beeline & External App Bridge (Ingest Path 2)",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val bridgeStatus = externalBridgeSnapshot?.let {
                        "BRIDGED: ${it.sourceAppName} (${it.maneuver} in ${it.distanceToNextMeters}m • ETA ${it.etaStr})"
                    } ?: "Waiting for Official Beeline App (co.beeline), Google Maps, or OsmAnd notification"
                    Text(
                        text = bridgeStatus,
                        color = if (externalBridgeSnapshot != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = onOpenNotificationListenerSettings
                        ) {
                            Text("Grant Notification Access", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                if (externalBridgeSnapshot == null) {
                                    engine.triggerSimulatedOfficialBeelineAppPacket()
                                } else {
                                    engine.clearExternalBridgeOverride()
                                }
                            }
                        ) {
                            Text(
                                text = if (externalBridgeSnapshot == null) "Simulate Beeline App" else "Use Native GPX",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // 2. Ingest Path 1: Native GPX Route Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Native GPX Route (Ingest Path 1): ${route.name}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Track Nodes: ${route.nodes.size} • Turn Maneuvers: ${route.maneuvers.size} • Waypoints: ${route.waypoints.size} • Total: ${"%.2f".format(route.totalDistanceMeters / 1000.0)} km",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onPickGpxFile) {
                            Text("Import .GPX File", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(onClick = { engine.loadSampleGpxRoute() }) {
                            Text("Reset Sample Loop", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // 3. 10Hz / 1Hz MessageClient Streaming & Hardware GPS Controls
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isStreaming) "FOREGROUND STREAM ACTIVE (10Hz / 1Hz)" else "STREAM PAUSED",
                                color = if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Packets: ${metrics.packetsSent} • Payload: ${metrics.lastPayloadSizeBytes} B • Latency: ${metrics.lastRoundTripMs} ms • Watches: ${metrics.connectedWatchCount}",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                        Button(
                            onClick = {
                                if (isStreaming) engine.stopStreaming() else engine.startStreaming()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                contentColor = if (isStreaming) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = if (isStreaming) "Stop" else "Start 10Hz",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use Hardware GPS (FusedLocationProviderClient)",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "10Hz GPS Course-Over-Ground (>5 km/h lock, rejects magnetometer)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = useLiveGps,
                            onCheckedChange = onToggleLiveGps
                        )
                    }

                    if (!useLiveGps) {
                        Text(
                            text = "Simulated GPS Speed: ${state.currentSpeedKmh} km/h (COG Lock < 5 km/h)",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = state.currentSpeedKmh.toFloat(),
                            onValueChange = { engine.setSimulatedSpeedKmh(it.toInt()) },
                            valueRange = 0f..140f
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Simulate >35m Off-Route Detour",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Triggers FAST_REROUTING + 4 breadcrumb dots after 3s",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = isOffRouteDetour,
                            onCheckedChange = { engine.setSimulatedOffRouteDetour(it) }
                        )
                    }
                }
            }

            // 4. Live Projected Telemetry Readout & Remote Mode Selector
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Live 2D Vector Projection & Telemetry Frame",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Active Mode: ${state.mode} • Next Maneuver: ${state.nextManeuver} in ${state.distanceToNextMeters}m\n" +
                            "GPS COG: ${state.headingDegrees}° (${state.cardinalDirection}) • Polylines: ${state.mapVectors.size} • Breadcrumbs: ${state.rerouteBreadcrumbs.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScreenMode.entries.forEach { mode ->
                            val isSelected = state.mode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { engine.setOverrideScreenMode(mode) },
                                label = {
                                    Text(
                                        text = mode.name,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
