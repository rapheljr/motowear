package com.motowear.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.tooling.preview.Preview
import com.motowear.telemetry.ScreenMode
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.wear.state.EdgeTapZone
import com.motowear.wear.state.RideViewModel
import com.motowear.wear.theme.MotoWearTheme
import com.motowear.wear.ui.components.BeelineBootSplashOverlay
import com.motowear.wear.ui.screens.DebugTelemetryScreen
import com.motowear.wear.ui.screens.DeviceSettingsScreen
import com.motowear.wear.ui.screens.JourneyProgressScreen
import com.motowear.wear.ui.screens.JourneySummaryScreen
import com.motowear.wear.ui.screens.JourneyTrackingScreen
import com.motowear.wear.ui.screens.QrOnboardingScreen
import com.motowear.wear.ui.screens.QuickStartScreen
import com.motowear.wear.ui.screens.SpeedoScreen
import com.motowear.wear.ui.screens.TurnByTurnAndRerouteScreen
import com.motowear.wear.ui.screens.WaypointSkippingScreen
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val SWIPE_THRESHOLD_PX = 42f

/**
 * Root Wear OS Composable routing all 8 Beeline Moto 2 Ride Modes.
 * Supports:
 * - Brief 550ms Beeline Roundel & Wordmark cold-boot splash (`BeelineBootSplashOverlay`, well within <1.2s SLA).
 * - Horizontal & vertical touch swipe gestures to cycle across all 8 Ride Modes.
 * - Interactive tap zones on the 9 o'clock (`<`), 3 o'clock (`>`), and 6 o'clock confirmation dome prompts.
 * - Rotary crown scrolling and physical stem buttons.
 */
@Composable
fun MotoWearApp(
    viewModel: RideViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var showBootSplash by remember { mutableStateOf(true) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        delay(550L)
        showBootSplash = false
    }

    MotoWearTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                // Programmatic Capacitive Touch Lock (only active when explicitly locked)
                .pointerInput(state.isTouchLocked) {
                    if (state.isTouchLocked) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                // Swipe gestures (Left/Right and Up/Down) to switch ride modes on the watch screen
                .pointerInput(state.isTouchLocked) {
                    if (!state.isTouchLocked) {
                        detectDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                                totalDragY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y
                            },
                            onDragEnd = {
                                if (abs(totalDragX) >= abs(totalDragY)) {
                                    if (abs(totalDragX) >= SWIPE_THRESHOLD_PX) {
                                        val dir = if (totalDragX < 0f) 1 else -1
                                        viewModel.onSwipeRideMode(dir)
                                    }
                                } else {
                                    if (abs(totalDragY) >= SWIPE_THRESHOLD_PX) {
                                        val dir = if (totalDragY < 0f) 1 else -1
                                        viewModel.onVerticalSwipe(dir)
                                    }
                                }
                            }
                        )
                    }
                }
                // Tap gestures for 9 o'clock (<), 3 o'clock (>), and 6 o'clock confirm dome prompts
                .pointerInput(state.isTouchLocked) {
                    if (!state.isTouchLocked) {
                        detectTapGestures(
                            onTap = { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                when {
                                    offset.y >= h * 0.78f && offset.x in (w * 0.30f)..(w * 0.70f) -> {
                                        viewModel.onEdgeZoneTap(EdgeTapZone.BOTTOM_6_OCLOCK)
                                    }
                                    offset.x <= w * 0.24f && offset.y in (h * 0.30f)..(h * 0.70f) -> {
                                        viewModel.onEdgeZoneTap(EdgeTapZone.LEFT_9_OCLOCK)
                                    }
                                    offset.x >= w * 0.76f && offset.y in (h * 0.30f)..(h * 0.70f) -> {
                                        viewModel.onEdgeZoneTap(EdgeTapZone.RIGHT_3_OCLOCK)
                                    }
                                }
                            }
                        )
                    }
                }
                .onRotaryScrollEvent { rotaryEvent ->
                    val dir = if (rotaryEvent.verticalScrollPixels >= 0f) 1 else -1
                    viewModel.onRotaryOrTopShortPress(dir)
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            MotoWearScreenContent(
                state = state,
                lastPacketMsAgo = viewModel.lastPacketMsAgo,
                totalPacketsCount = viewModel.packetsReceivedCount
            )

            AnimatedVisibility(
                visible = showBootSplash,
                exit = fadeOut()
            ) {
                BeelineBootSplashOverlay()
            }
        }
    }
}

@Composable
fun MotoWearScreenContent(
    state: TelemetryFrameState,
    modifier: Modifier = Modifier,
    lastPacketMsAgo: Long = 16L,
    totalPacketsCount: Long = 142L
) {
    when (state.mode) {
        ScreenMode.TURN_BY_TURN,
        ScreenMode.FAST_REROUTING -> TurnByTurnAndRerouteScreen(state = state, modifier = modifier)
        ScreenMode.SPEEDO -> SpeedoScreen(state = state, modifier = modifier)
        ScreenMode.JOURNEY_PROGRESS -> JourneyProgressScreen(state = state, modifier = modifier)
        ScreenMode.JOURNEY_TRACKING -> JourneyTrackingScreen(state = state, modifier = modifier)
        ScreenMode.QUICK_START -> QuickStartScreen(state = state, modifier = modifier)
        ScreenMode.WAYPOINT_SKIPPING -> WaypointSkippingScreen(state = state, modifier = modifier)
        ScreenMode.JOURNEY_SUMMARY -> JourneySummaryScreen(state = state, modifier = modifier)
        ScreenMode.DEVICE_SETTINGS -> DeviceSettingsScreen(state = state, modifier = modifier)
        ScreenMode.QR_ONBOARDING -> QrOnboardingScreen(state = state, modifier = modifier)
        ScreenMode.DEBUG_TELEMETRY -> DebugTelemetryScreen(
            state = state,
            lastPacketMsAgo = lastPacketMsAgo,
            totalPacketsCount = totalPacketsCount,
            modifier = modifier
        )
    }
}

// --- Wear OS 450x450 Previews for all Ride Modes + Brand Boot Splash ---

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewBeelineBootSplash() {
    BeelineBootSplashOverlay()
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewQrOnboardingScreen() {
    val state = RideViewModel.createReferenceBaselineState().copy(mode = ScreenMode.QR_ONBOARDING)
    MotoWearScreenContent(state = state)
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewDeviceSettingsScreen() {
    val state = RideViewModel.createReferenceBaselineState().copy(mode = ScreenMode.DEVICE_SETTINGS)
    MotoWearScreenContent(state = state)
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewMode1TurnByTurn() {
    val state = RideViewModel.createReferenceBaselineState().copy(mode = ScreenMode.TURN_BY_TURN)
    MotoWearScreenContent(state = state)
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewMode2FastRerouting() {
    val state = RideViewModel.createReferenceBaselineState().copy(
        mode = ScreenMode.FAST_REROUTING,
        isOffRoute = true
    )
    MotoWearScreenContent(state = state)
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewMode3Speedo() {
    val state = RideViewModel.createReferenceBaselineState().copy(mode = ScreenMode.SPEEDO)
    MotoWearScreenContent(state = state)
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewMode4JourneyProgress() {
    val state = RideViewModel.createReferenceBaselineState().copy(mode = ScreenMode.JOURNEY_PROGRESS)
    MotoWearScreenContent(state = state)
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewMode5JourneyTracking() {
    val state = RideViewModel.createReferenceBaselineState().copy(mode = ScreenMode.JOURNEY_TRACKING)
    MotoWearScreenContent(state = state)
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewMode6QuickStart() {
    val state = RideViewModel.createReferenceBaselineState().copy(mode = ScreenMode.QUICK_START)
    MotoWearScreenContent(state = state)
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewMode7WaypointSkipping() {
    val state = RideViewModel.createReferenceBaselineState().copy(mode = ScreenMode.WAYPOINT_SKIPPING)
    MotoWearScreenContent(state = state)
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewMode8JourneySummary() {
    val state = RideViewModel.createReferenceBaselineState().copy(mode = ScreenMode.JOURNEY_SUMMARY)
    MotoWearScreenContent(state = state)
}
