package com.motowear.wear.data

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.motowear.telemetry.TelemetryPaths

/**
 * Wearable Data Layer listener receiving 10Hz binary Protobuf `RideTelemetryFrame` packets
 * from the Android companion host on `/motowear/telemetry/frame_v1` and forwarding them
 * to `WearTelemetryRepository`.
 */
class WearTelemetryListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == TelemetryPaths.TELEMETRY_FRAME_PATH) {
            WearTelemetryRepository.onProtobufPacketReceived(messageEvent.data)
        }
    }
}
