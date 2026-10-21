package com.motowear.wear.data

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.motowear.telemetry.ProtobufTelemetryMapper
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.telemetry.TelemetryPaths
import com.motowear.wear.ble.BeelineBleGattPeripheralServer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import java.lang.ref.WeakReference

object BeelineButtonCode {
    const val REPLAY_VOICE: Int = 1
    const val RECALCULATE: Int = 2
    const val SKIP_WAYPOINT: Int = 3
}

/**
 * Singleton Dual-Ingest bridge holding live 10Hz telemetry frames received either from:
 * 1. The MotoWear Android Companion Host via `MessageClient` (`/motowear/telemetry/frame_v1`), OR
 * 2. Official `co.beeline` / BLE GATT Peripheral writes on `BeelineBleGattPeripheralServer` (`C5D70002-...` / `0000bee1-...`).
 */
object WearTelemetryRepository {

    private val _incomingFrames = MutableSharedFlow<TelemetryFrameState>(extraBufferCapacity = 16)
    val incomingFrames: SharedFlow<TelemetryFrameState> = _incomingFrames.asSharedFlow()

    private var activeBleServerRef: WeakReference<BeelineBleGattPeripheralServer>? = null

    fun registerBlePeripheralServer(server: BeelineBleGattPeripheralServer) {
        activeBleServerRef = WeakReference(server)
    }

    fun unregisterBlePeripheralServer(server: BeelineBleGattPeripheralServer) {
        if (activeBleServerRef?.get() === server) {
            activeBleServerRef = null
        }
    }

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
        // Also emit BLE GATT ButtonPress notification on C5D70003 for connected co.beeline centrals
        val buttonCode = when {
            command == "REPLAY_VOICE_PROMPT" -> BeelineButtonCode.REPLAY_VOICE
            command == "FORCE_RECALCULATE" -> BeelineButtonCode.RECALCULATE
            command.startsWith("SKIP_WAYPOINT:") -> BeelineButtonCode.SKIP_WAYPOINT
            else -> BeelineButtonCode.REPLAY_VOICE
        }
        activeBleServerRef?.get()?.notifyUserActionButtonPress(buttonCode)

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
