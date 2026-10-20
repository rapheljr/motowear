package com.motowear.wear.data

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.motowear.telemetry.ProtobufTelemetryMapper
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.telemetry.TelemetryPaths
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await

/**
 * Singleton Dual-Ingest bridge holding live 10Hz telemetry frames received either from:
 * 1. The MotoWear Android Companion Host via `MessageClient` (`/motowear/telemetry/frame_v1`), OR
 * 2. Direct BLE GATT Peripheral writes on `BeelineBleGattPeripheralServer` (`0000bee1-...`).
 */
object WearTelemetryRepository {

    private val _incomingFrames = MutableSharedFlow<TelemetryFrameState>(extraBufferCapacity = 16)
    val incomingFrames: SharedFlow<TelemetryFrameState> = _incomingFrames.asSharedFlow()

    fun onProtobufPacketReceived(bytes: ByteArray) {
        runCatching {
            val decoded = ProtobufTelemetryMapper.fromProtoByteArray(bytes)
            _incomingFrames.tryEmit(decoded)
        }
    }

    fun onExternalFrameDecoded(frame: TelemetryFrameState) {
        _incomingFrames.tryEmit(frame)
    }

    suspend fun sendCommandToPhoneHost(context: Context, command: String) {
        val appContext = context.applicationContext
        val nodeClient = Wearable.getNodeClient(appContext)
        val messageClient = Wearable.getMessageClient(appContext)
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        val payload = command.toByteArray(Charsets.UTF_8)
        for (node in nodes) {
            runCatching {
                messageClient.sendMessage(
                    node.id,
                    TelemetryPaths.WATCH_COMMAND_PATH,
                    payload
                ).await()
            }
        }
    }
}
