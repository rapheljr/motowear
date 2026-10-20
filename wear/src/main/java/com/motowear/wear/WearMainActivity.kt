package com.motowear.wear

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import com.motowear.wear.ble.BeelineBleGattPeripheralServer
import com.motowear.wear.power.DynamicWakeController
import com.motowear.wear.state.RideViewModel
import com.motowear.wear.ui.MotoWearApp
import kotlinx.coroutines.launch

/**
 * Wear OS Client Entry Activity.
 * - Starts `BeelineBleGattPeripheralServer` so the watch advertises as a BLE GATT `"Beeline Moto II"`
 *   peripheral while simultaneously listening to Wear OS `MessageClient` packets.
 * - Integrates `AmbientLifecycleObserver` & `DynamicWakeController` for Section 6.2 Dynamic Distance Wake.
 * - Intercepts physical watch stem buttons (`KEYCODE_STEM_1`, `KEYCODE_STEM_2`) including 1.5s long-press detection.
 * - Calls `reportFullyDrawn()` for Section 7 Cold Boot SLA (< 1.2s).
 */
class WearMainActivity : ComponentActivity() {

    private val viewModel: RideViewModel by viewModels()
    private val dynamicWakeController = DynamicWakeController()
    private lateinit var blePeripheralServer: BeelineBleGattPeripheralServer
    private var isSystemAmbient = false
    private var topStemDownTimestampMs: Long = 0L
    private var topStemLongPressConsumed: Boolean = false

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isSystemAmbient = true
            viewModel.setAmbientMode(true)
        }

        override fun onExitAmbient() {
            isSystemAmbient = false
            viewModel.setAmbientMode(false)
        }

        override fun onUpdateAmbient() {
            viewModel.setAmbientMode(true)
        }
    }

    private lateinit var ambientObserver: AmbientLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blePeripheralServer = BeelineBleGattPeripheralServer(this)
        blePeripheralServer.startPeripheralServer()

        ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
        lifecycle.addObserver(ambientObserver)

        // Continuously evaluate Section 6.2 Dynamic Distance Wake (400m threshold + 5s post-maneuver hold)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { frame ->
                    val shouldUseHollowAmbient = dynamicWakeController.evaluateWakeAndBrightness(
                        window = window,
                        state = frame,
                        systemAmbientActive = isSystemAmbient
                    )
                    if (frame.isAmbientMode != shouldUseHollowAmbient) {
                        viewModel.setAmbientMode(shouldUseHollowAmbient)
                    }
                }
            }
        }

        setContent {
            MotoWearApp(viewModel = viewModel)
        }

        // Section 7 SLA: Cold Boot to QUICK_START < 1.2 seconds
        reportFullyDrawn()
    }

    override fun onDestroy() {
        super.onDestroy()
        blePeripheralServer.stopPeripheralServer()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_NAVIGATE_NEXT -> {
                if (event?.repeatCount == 0) {
                    topStemDownTimestampMs = System.currentTimeMillis()
                    topStemLongPressConsumed = false
                } else if (!topStemLongPressConsumed &&
                    System.currentTimeMillis() - topStemDownTimestampMs >= 1500L
                ) {
                    topStemLongPressConsumed = true
                    viewModel.onTopButtonLongPress()
                }
                true
            }
            KeyEvent.KEYCODE_STEM_2, KeyEvent.KEYCODE_STEM_PRIMARY, KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> {
                viewModel.onBottomActionButtonPress()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_NAVIGATE_NEXT -> {
                val heldDuration = System.currentTimeMillis() - topStemDownTimestampMs
                if (!topStemLongPressConsumed) {
                    if (heldDuration >= 1500L) {
                        viewModel.onTopButtonLongPress()
                    } else {
                        viewModel.onRotaryOrTopShortPress(direction = 1)
                    }
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
}
