package com.motowear.phone

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.google.android.gms.wearable.Wearable
import com.motowear.telemetry.ManeuverKind
import com.motowear.telemetry.ProtobufTelemetryMapper.toProtoByteArray
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.telemetry.TelemetryPaths
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class BroadcastMetrics(
    val packetsSent: Long = 0L,
    val lastPayloadSizeBytes: Int = 0,
    val lastRoundTripMs: Long = 0L,
    val connectedWatchCount: Int = 0
)

/**
 * Section 2 & Section 6.3:
 * - Serializes `TelemetryFrameState` into compact Protobuf binary frames and dispatches via
 *   `Wearable.getMessageClient` at 10Hz during active turns and 1Hz on straight legs.
 * - Routes turn alerts at 300m and 50m over Bluetooth Helmet Intercom using
 *   `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` and `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.
 */
class TelemetryBroadcaster(context: Context) {

    private val appContext = context.applicationContext
    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val navigationAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(navigationAudioAttributes)
        .build()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var packetsSentCount = 0L

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setAudioAttributes(navigationAudioAttributes)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }
                })
                isTtsReady = true
            }
        }
    }

    suspend fun broadcastTelemetryState(state: TelemetryFrameState): BroadcastMetrics {
        val startNs = System.nanoTime()
        val payload = state.toProtoByteArray()
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())

        for (node in nodes) {
            runCatching {
                messageClient.sendMessage(
                    node.id,
                    TelemetryPaths.TELEMETRY_FRAME_PATH,
                    payload
                ).await()
            }
        }
        packetsSentCount++
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        return BroadcastMetrics(
            packetsSent = packetsSentCount,
            lastPayloadSizeBytes = payload.size,
            lastRoundTripMs = elapsedMs,
            connectedWatchCount = nodes.size
        )
    }

    /**
     * Announces upcoming maneuver over helmet intercom at 300m and 50m thresholds while ducking music.
     */
    fun speakTurnPrompt(maneuver: ManeuverKind, distanceMeters: Int) {
        if (!isTtsReady) return
        val actionText = when (maneuver) {
            ManeuverKind.TURN_LEFT -> "turn left"
            ManeuverKind.TURN_RIGHT -> "turn right"
            ManeuverKind.SLIGHT_LEFT -> "keep left"
            ManeuverKind.SLIGHT_RIGHT -> "keep right"
            ManeuverKind.SHARP_LEFT -> "sharp left"
            ManeuverKind.SHARP_RIGHT -> "sharp right"
            ManeuverKind.UTURN -> "make a U-turn"
            ManeuverKind.ROUNDABOUT_LEFT, ManeuverKind.ROUNDABOUT_RIGHT -> "enter roundabout"
            ManeuverKind.STRAIGHT -> "continue straight"
        }
        val phrase = if (distanceMeters <= 60) {
            "In $distanceMeters meters, $actionText."
        } else {
            "In $distanceMeters meters, prepare to $actionText."
        }

        val focusResult = audioManager.requestAudioFocus(focusRequest)
        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            val params = Bundle()
            tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "motowear_turn_${System.currentTimeMillis()}")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
