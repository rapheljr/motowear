package com.motowear.wear.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.motowear.telemetry.ManeuverKind
import com.motowear.telemetry.ProtobufTelemetryMapper
import com.motowear.telemetry.RoutePolyline
import com.motowear.telemetry.ScreenMode
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.telemetry.Vec2
import com.motowear.wear.data.WearTelemetryRepository
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Section 2: Direct BLE GATT Peripheral Receiver (`BeelineBleGattPeripheralServer`).
 *
 * Advertises the Wear OS watch over Bluetooth Low Energy as a `"Beeline Moto II"` peripheral
 * and hosts a GATT Server accepting both:
 * 1. Compact binary Beeline BLE GATT packets (`10-byte` fixed header telemetry frame), AND
 * 2. Full binary Protobuf `RideTelemetryFrame` payloads.
 */
class BeelineBleGattPeripheralServer(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var gattServer: BluetoothGattServer? = null
    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Log.d("BeelineBleGattServer", "BLE peripheral advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.w("BeelineBleGattServer", "BLE peripheral advertising failed with code: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic?.uuid == CHAR_TELEMETRY_WRITE_UUID && value != null) {
                val frame = decodeBleGattPayload(value)
                if (frame != null) {
                    WearTelemetryRepository.onExternalFrameDecoded(frame)
                }
            }
            if (responseNeeded && device != null) {
                runCatching {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startPeripheralServer() {
        val manager = bluetoothManager ?: return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        runCatching {
            gattServer = manager.openGattServer(appContext, gattCallback)?.also { server ->
                val service = BluetoothGattService(
                    BEELINE_MOTO_SERVICE_UUID,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
                )

                val telemetryWriteChar = BluetoothGattCharacteristic(
                    CHAR_TELEMETRY_WRITE_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                )

                val buttonActionNotifyChar = BluetoothGattCharacteristic(
                    CHAR_BUTTON_NOTIFY_UUID,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )

                service.addCharacteristic(telemetryWriteChar)
                service.addCharacteristic(buttonActionNotifyChar)
                server.addService(service)
            }

            runCatching {
                adapter.name = "Beeline Moto II"
            }

            val advertiser = adapter.bluetoothLeAdvertiser ?: return@runCatching
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

            // Keep advertiseData strictly under the 31-byte legacy BLE limit:
            // 128-bit UUID (18B) + Flags (3B) = 21B in advertiseData; device name goes in scanResponseData.
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(BEELINE_MOTO_SERVICE_UUID))
                .build()

            val scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

            advertiser.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopPeripheralServer() {
        runCatching {
            if (isAdvertising) {
                bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                isAdvertising = false
            }
            gattServer?.close()
            gattServer = null
        }
    }

    companion object {
        val BEELINE_MOTO_SERVICE_UUID: UUID =
            UUID.fromString("0000bee0-0000-1000-8000-00805f9b34fb")
        val CHAR_TELEMETRY_WRITE_UUID: UUID =
            UUID.fromString("0000bee1-0000-1000-8000-00805f9b34fb")
        val CHAR_BUTTON_NOTIFY_UUID: UUID =
            UUID.fromString("0000bee2-0000-1000-8000-00805f9b34fb")

        private const val COMPACT_BLE_MAGIC_BYTE: Byte = 0xB2.toByte()

        /**
         * Decodes either a compact 10-byte Beeline BLE GATT telemetry frame:
         * `[0xB2, maneuverOrd, roundExit, distLo, distHi, speedKmh, speedLimitKmh, headingLo, headingHi, flags]`
         * OR a standard binary Protobuf `RideTelemetryFrame` packet.
         */
        fun decodeBleGattPayload(bytes: ByteArray): TelemetryFrameState? {
            if (bytes.isEmpty()) return null

            if (bytes.size >= 10 && bytes[0] == COMPACT_BLE_MAGIC_BYTE) {
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                buf.get() // 0xB2 magic byte
                val maneuverIdx = (buf.get().toInt() and 0xFF)
                val roundExit = (buf.get().toInt() and 0xFF)
                val distanceMeters = (buf.short.toInt() and 0xFFFF)
                val speedKmh = (buf.get().toInt() and 0xFF)
                val speedLimitKmh = (buf.get().toInt() and 0xFF).let { if (it == 0) 70 else it }
                val headingDeg = (buf.short.toInt() and 0xFFFF) % 360
                val flags = (buf.get().toInt() and 0xFF)
                val isOffRoute = (flags and 0x01) != 0

                val maneuver = ManeuverKind.entries.getOrElse(maneuverIdx) { ManeuverKind.STRAIGHT }
                val turnY = (-0.15f - (distanceMeters.coerceIn(0, 350) / 350f) * 0.40f)
                val turnEndX = when (maneuver) {
                    ManeuverKind.TURN_LEFT, ManeuverKind.SLIGHT_LEFT, ManeuverKind.SHARP_LEFT -> -0.65f
                    ManeuverKind.TURN_RIGHT, ManeuverKind.SLIGHT_RIGHT, ManeuverKind.SHARP_RIGHT -> 0.65f
                    else -> 0.0f
                }

                return TelemetryFrameState(
                    mode = if (isOffRoute) ScreenMode.FAST_REROUTING else ScreenMode.TURN_BY_TURN,
                    mapVectors = listOf(
                        RoutePolyline(
                            points = listOf(Vec2(0f, turnY), Vec2(0f, -0.96f)),
                            isPrimaryRoute = false
                        ),
                        RoutePolyline(
                            points = listOf(Vec2(0f, 0.05f), Vec2(0f, turnY), Vec2(turnEndX, turnY)),
                            isPrimaryRoute = true
                        )
                    ),
                    rerouteBreadcrumbs = if (isOffRoute) {
                        listOf(
                            Vec2(0.06f, -0.14f),
                            Vec2(0.14f, -0.28f),
                            Vec2(0.22f, -0.42f),
                            Vec2(0.30f, -0.56f)
                        )
                    } else {
                        emptyList()
                    },
                    nextManeuver = maneuver,
                    roundExitNumber = roundExit,
                    distanceToNextMeters = distanceMeters,
                    legProgressRatio = (1f - (distanceMeters.coerceIn(0, 400) / 400f)).coerceIn(0.1f, 0.95f),
                    isOffRoute = isOffRoute,
                    currentSpeedKmh = speedKmh,
                    speedLimitKmh = speedLimitKmh,
                    avgSpeedKmh = (speedKmh * 0.9f).toInt(),
                    speedLimitApproachRatio = (speedKmh.toFloat() / speedLimitKmh.toFloat()).coerceIn(0f, 1f),
                    headingDegrees = headingDeg
                )
            }

            return runCatching {
                ProtobufTelemetryMapper.fromProtoByteArray(bytes)
            }.getOrNull()
        }
    }
}
