package com.motowear.wear.power

import android.view.Window
import android.view.WindowManager
import com.motowear.telemetry.ScreenMode
import com.motowear.telemetry.TelemetryFrameState

/**
 * Section 6.2: Thermal Throttling & OLED Burn-In Protection (`DynamicWakeController`).
 *
 * - During active `TURN_BY_TURN` navigation when `distance_to_next_m > 400` and `!is_off_route`
 *   (after the 5-second post-maneuver hold expires), drops screen brightness to 25% (`0.25f`),
 *   releases the full-brightness wake lock, and enables Active Ambient hollow-vector outline rendering.
 * - When `distance_to_next_m <= 400` (or `is_off_route == true`), acquires a full-brightness wake lock
 *   (`1.0f` brightness + `FLAG_KEEP_SCREEN_ON`) and holds it until 5 seconds after the maneuver is completed.
 */
class DynamicWakeController {

    private var wasInManeuverZone: Boolean = false
    private var maneuverCompletedHoldUntilMs: Long = 0L

    fun evaluateWakeAndBrightness(
        window: Window,
        state: TelemetryFrameState,
        systemAmbientActive: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val isDistanceEligibleForAmbient =
            state.mode == ScreenMode.TURN_BY_TURN &&
                state.distanceToNextMeters > WAKE_DISTANCE_THRESHOLD_METERS &&
                !state.isOffRoute

        val isNearManeuverOrOffRoute =
            (state.mode == ScreenMode.TURN_BY_TURN && state.distanceToNextMeters <= WAKE_DISTANCE_THRESHOLD_METERS) ||
                state.isOffRoute

        if (isNearManeuverOrOffRoute) {
            wasInManeuverZone = true
        } else if (wasInManeuverZone && state.distanceToNextMeters > WAKE_DISTANCE_THRESHOLD_METERS) {
            // Maneuver just completed (distance jumped > 400m for the next leg): hold full brightness for 5 seconds
            wasInManeuverZone = false
            maneuverCompletedHoldUntilMs = nowMs + POST_MANEUVER_WAKE_HOLD_MS
        }

        val isInsidePostManeuverHold = nowMs < maneuverCompletedHoldUntilMs
        val shouldThrottleToAmbient =
            systemAmbientActive || (isDistanceEligibleForAmbient && !isInsidePostManeuverHold)

        val params = window.attributes
        if (!shouldThrottleToAmbient) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            params.screenBrightness = FULL_BRIGHTNESS
            window.attributes = params
            return false // Full active mode (solid fills)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            params.screenBrightness = AMBIENT_BRIGHTNESS_25_PERCENT
            window.attributes = params
            return true // Active Ambient mode (hollow 1.5dp vector outlines + Lissajous shift)
        }
    }

    companion object {
        const val WAKE_DISTANCE_THRESHOLD_METERS = 400
        const val POST_MANEUVER_WAKE_HOLD_MS = 5_000L
        const val FULL_BRIGHTNESS = 1.0f
        const val AMBIENT_BRIGHTNESS_25_PERCENT = 0.25f
    }
}
