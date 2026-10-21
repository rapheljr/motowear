package com.motowear.wear.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.BatteryManager
import android.os.Build
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
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Section 2: Direct BLE GATT Peripheral Receiver (`BeelineBleGattPeripheralServer`).
 *
 * Implements the verified BLE GATT profile extracted from official `co.beeline` v4.2.8704:
 * - Primary Beeline Moto II GATT Service (`C5D70001-C45D-4F12-8693-7EF838E96446`)
 *   - `C5D70002-C45D-4F12-8693-7EF838E96446`: Host-to-Device Command & Telemetry Write Characteristic
 *   - `C5D70003-C45D-4F12-8693-7EF838E96446`: Device-to-Host `ButtonPress` / Notification Characteristic (+ CCCD `0x2902`)
 * - Extended Beeline Telemetry Service (`258EAFA5-E914-47DA-95CA-C5AB0DC85B11`)
 *   - `5ED8C8FD-B47C-4F04-A53F-AEF2176F334C`, `95ED6082-B8E9-46E8-A73F-FF56F00F5D9D`, `D861B25A-1EDF-11EB-ADC1-0242AC120002`
 * - Standard Bluetooth SIG Services required during `co.beeline` pairing:
 *   - Battery Service (`0x180F`) + Battery Level (`0x2A19`)
 *   - Device Information Service (`0x180A`) + Manufacturer (`0x2A29`), Model (`0x2A24`), Firmware (`0x2A26`)
 *   - Current Time Service (`0x1805`) + Current Time (`0x2A2B`) & Local Time Info (`0x2A0F`)
 */
class BeelineBleGattPeripheralServer(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private var gattServer: BluetoothGattServer? = null
    private var notifyCharacteristicC5D70003: BluetoothGattCharacteristic? = null
    private val subscribedDevices = CopyOnWriteArraySet<BluetoothDevice>()
    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Log.d("BeelineBleGattServer", "BLE peripheral advertising started (C5D70001)")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.w("BeelineBleGattServer", "BLE peripheral advertising failed with code: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (device == null) return
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                subscribedDevices.add(device)
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                subscribedDevices.remove(device)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (device == null || characteristic == null) return
            val responseBytes: ByteArray = when (characteristic.uuid) {
                CHAR_BATTERY_LEVEL_UUID -> {
                    val pct = (batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85)
                        .coerceIn(0, 100)
                    byteArrayOf(pct.toByte())
                }
                CHAR_MANUFACTURER_NAME_UUID -> "Beeline".toByteArray(Charsets.UTF_8)
                CHAR_MODEL_NUMBER_UUID -> "Beeline Moto II".toByteArray(Charsets.UTF_8)
                CHAR_FIRMWARE_REV_UUID -> "4.2.8704".toByteArray(Charsets.UTF_8)
                CHAR_HARDWARE_REV_UUID -> "2.0".toByteArray(Charsets.UTF_8)
                else -> byteArrayOf(0x00)
            }
            val sliced = if (offset < responseBytes.size) {
                responseBytes.copyOfRange(offset, responseBytes.size)
            } else {
                byteArrayOf()
            }
            runCatching {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, sliced)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (device != null && descriptor?.uuid == CCCD_UUID) {
                subscribedDevices.add(device)
            }
            if (responseNeeded && device != null) {
                runCatching {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

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
            val uuid = characteristic?.uuid
            if (value != null && uuid in WRITABLE_TELEMETRY_UUIDS) {
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
        WearTelemetryRepository.registerBlePeripheralServer(this)
        val manager = bluetoothManager ?: return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        runCatching {
            gattServer = manager.openGattServer(appContext, gattCallback)?.also { server ->
                // 1. Primary Official Beeline Service (C5D70001-C45D-4F12-8693-7EF838E96446)
                val primaryBeelineService = BluetoothGattService(
                    BEELINE_OFFICIAL_SERVICE_UUID,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
                )
                val commandWriteChar = BluetoothGattCharacteristic(
                    BEELINE_OFFICIAL_WRITE_CHAR_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                )
                val notifyChar = BluetoothGattCharacteristic(
                    BEELINE_OFFICIAL_NOTIFY_CHAR_UUID,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_INDICATE or
                        BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                ).apply {
                    addDescriptor(
                        BluetoothGattDescriptor(
                            CCCD_UUID,
                            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                        )
                    )
                }
                notifyCharacteristicC5D70003 = notifyChar
                primaryBeelineService.addCharacteristic(commandWriteChar)
                primaryBeelineService.addCharacteristic(notifyChar)
                server.addService(primaryBeelineService)

                // 2. Secondary Official Beeline Telemetry Service (258EAFA5-E914-47DA-95CA-C5AB0DC85B11)
                val extBeelineService = BluetoothGattService(
                    BEELINE_EXT_SERVICE_UUID,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
                )
                listOf(
                    BEELINE_EXT_CHAR_1_UUID,
                    BEELINE_EXT_CHAR_2_UUID,
                    BEELINE_EXT_CHAR_3_UUID,
                    CHAR_TELEMETRY_WRITE_UUID
                ).forEach { charUuid ->
                    extBeelineService.addCharacteristic(
                        BluetoothGattCharacteristic(
                            charUuid,
                            BluetoothGattCharacteristic.PROPERTY_WRITE or
                                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                                BluetoothGattCharacteristic.PROPERTY_READ or
                                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                            BluetoothGattCharacteristic.PERMISSION_WRITE or
                                BluetoothGattCharacteristic.PERMISSION_READ
                        )
                    )
                }
                server.addService(extBeelineService)

                // 3. Standard Bluetooth SIG Battery Service (0x180F) & Device Information Service (0x180A)
                val batteryService = BluetoothGattService(
                    SERVICE_BATTERY_UUID,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
                ).apply {
                    addCharacteristic(
                        BluetoothGattCharacteristic(
                            CHAR_BATTERY_LEVEL_UUID,
                            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                            BluetoothGattCharacteristic.PERMISSION_READ
                        )
                    )
                }
                server.addService(batteryService)

                val deviceInfoService = BluetoothGattService(
                    SERVICE_DEVICE_INFO_UUID,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
                ).apply {
                    listOf(
                        CHAR_MANUFACTURER_NAME_UUID,
                        CHAR_MODEL_NUMBER_UUID,
                        CHAR_FIRMWARE_REV_UUID,
                        CHAR_HARDWARE_REV_UUID
                    ).forEach { infoUuid ->
                        addCharacteristic(
                            BluetoothGattCharacteristic(
                                infoUuid,
                                BluetoothGattCharacteristic.PROPERTY_READ,
                                BluetoothGattCharacteristic.PERMISSION_READ
                            )
                        )
                    }
                }
                server.addService(deviceInfoService)
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

            // Advertise official C5D70001-C45D-4F12-8693-7EF838E96446 service UUID (21 bytes < 31B limit)
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(BEELINE_OFFICIAL_SERVICE_UUID))
                .build()

            val scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

            advertiser.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
        }
    }

    /**
     * Emits a `BeelineDeviceNotification$UserAction$ButtonPress` Protobuf frame over `C5D70003`
     * to any connected official `co.beeline` central when the rider presses a watch button.
     */
    @SuppressLint("MissingPermission")
    fun notifyUserActionButtonPress(buttonId: Int) {
        val server = gattServer ?: return
        val characteristic = notifyCharacteristicC5D70003 ?: return
        // Wire-Protobuf encoding for UserAction.ButtonPress (field 1 varint = buttonId)
        val payload = byteArrayOf(0x08, (buttonId and 0x7F).toByte())
        for (device in subscribedDevices) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, characteristic, false, payload)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = payload
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, characteristic, false)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopPeripheralServer() {
        WearTelemetryRepository.unregisterBlePeripheralServer(this)
        runCatching {
            if (isAdvertising) {
                bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                isAdvertising = false
            }
            subscribedDevices.clear()
            gattServer?.close()
            gattServer = null
        }
    }

    companion object {
        // Official Beeline v4.2.8704 GATT UUIDs
        val BEELINE_OFFICIAL_SERVICE_UUID: UUID =
            UUID.fromString("c5d70001-c45d-4f12-8693-7ef838e96446")
        val BEELINE_OFFICIAL_WRITE_CHAR_UUID: UUID =
            UUID.fromString("c5d70002-c45d-4f12-8693-7ef838e96446")
        val BEELINE_OFFICIAL_NOTIFY_CHAR_UUID: UUID =
            UUID.fromString("c5d70003-c45d-4f12-8693-7ef838e96446")

        val BEELINE_EXT_SERVICE_UUID: UUID =
            UUID.fromString("258eafa5-e914-47da-95ca-c5ab0dc85b11")
        val BEELINE_EXT_CHAR_1_UUID: UUID =
            UUID.fromString("5ed8c8fd-b47c-4f04-a53f-aef2176f334c")
        val BEELINE_EXT_CHAR_2_UUID: UUID =
            UUID.fromString("95ed6082-b8e9-46e8-a73f-ff56f00f5d9d")
        val BEELINE_EXT_CHAR_3_UUID: UUID =
            UUID.fromString("d861b25a-1edf-11eb-adc1-0242ac120002")

        // Custom MotoWear BLE UUIDs
        val BEELINE_MOTO_SERVICE_UUID: UUID =
            UUID.fromString("0000bee0-0000-1000-8000-00805f9b34fb")
        val CHAR_TELEMETRY_WRITE_UUID: UUID =
            UUID.fromString("0000bee1-0000-1000-8000-00805f9b34fb")
        val CHAR_BUTTON_NOTIFY_UUID: UUID =
            UUID.fromString("0000bee2-0000-1000-8000-00805f9b34fb")

        // Standard Bluetooth SIG UUIDs
        val SERVICE_BATTERY_UUID: UUID =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val CHAR_BATTERY_LEVEL_UUID: UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val SERVICE_DEVICE_INFO_UUID: UUID =
            UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val CHAR_MANUFACTURER_NAME_UUID: UUID =
            UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        val CHAR_MODEL_NUMBER_UUID: UUID =
            UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        val CHAR_FIRMWARE_REV_UUID: UUID =
            UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        val CHAR_HARDWARE_REV_UUID: UUID =
            UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        val WRITABLE_TELEMETRY_UUIDS = setOf(
            BEELINE_OFFICIAL_WRITE_CHAR_UUID,
            BEELINE_EXT_CHAR_1_UUID,
            BEELINE_EXT_CHAR_2_UUID,
            BEELINE_EXT_CHAR_3_UUID,
            CHAR_TELEMETRY_WRITE_UUID
        )

        private const val COMPACT_BLE_MAGIC_BYTE: Byte = 0xB2.toByte()

        /**
         * Decodes:
         * 1. Compact 10-byte Beeline BLE GATT telemetry frame (`0xB2` magic header),
         * 2. Standard MotoWear binary Protobuf `RideTelemetryFrame` packet, OR
         * 3. Official `co.beeline.proto.Command` / `BeelineDeviceMessage` Wire-Protobuf TLV frames.
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

                return buildSyntheticFrame(
                    maneuverIdx = maneuverIdx,
                    roundExit = roundExit,
                    distanceMeters = distanceMeters,
                    speedKmh = speedKmh,
                    speedLimitKmh = speedLimitKmh,
                    headingDeg = headingDeg,
                    isOffRoute = isOffRoute
                )
            }

            // Attempt full MotoWear RideTelemetryFrame Protobuf parse first
            val protoResult = runCatching {
                val parsed = ProtobufTelemetryMapper.fromProtoByteArray(bytes)
                if (parsed.mapVectors.isNotEmpty() || parsed.distanceToNextMeters > 0 || parsed.currentSpeedKmh > 0) {
                    parsed
                } else {
                    null
                }
            }.getOrNull()
            if (protoResult != null) return protoResult

            // Fallback: Decode official co.beeline.proto.Command Wire TLV varint fields
            return decodeBeelineWireCommand(bytes)
        }

        /**
         * Parses Wire-Protobuf TLV fields from `co.beeline.proto.Command` / `BeelineDeviceMessage`:
         * Extracts command/screen type (field 1), maneuver/turn angle (field 2), distance in meters (field 3),
         * speed km/h (field 4), speed limit km/h (field 5), and heading degrees (field 6).
         */
        fun decodeBeelineWireCommand(bytes: ByteArray): TelemetryFrameState? {
            var idx = 0
            var commandType = 1
            var maneuverIdx = 0
            var distanceMeters = 200
            var speedKmh = 60
            var speedLimitKmh = 70
            var headingDeg = 0
            var roundExit = 0
            var fieldsRead = 0

            while (idx < bytes.size) {
                val tag = readVarint(bytes, idx) ?: break
                idx = tag.second
                val fieldNumber = (tag.first ushr 3).toInt()
                val wireType = (tag.first and 0x07L).toInt()

                when (wireType) {
                    0 -> { // Varint
                        val v = readVarint(bytes, idx) ?: break
                        idx = v.second
                        val intVal = v.first.toInt()
                        fieldsRead++
                        when (fieldNumber) {
                            1 -> commandType = intVal
                            2 -> maneuverIdx = intVal
                            3 -> distanceMeters = intVal.coerceIn(0, 50_000)
                            4 -> speedKmh = intVal.coerceIn(0, 300)
                            5 -> speedLimitKmh = intVal.coerceIn(10, 200)
                            6 -> headingDeg = ((intVal % 360) + 360) % 360
                            7 -> roundExit = intVal.coerceIn(0, 12)
                        }
                    }
                    2 -> { // Length-delimited nested message
                        val lenPair = readVarint(bytes, idx) ?: break
                        idx = lenPair.second
                        val len = lenPair.first.toInt()
                        if (len < 0 || idx + len > bytes.size) break
                        val nested = bytes.copyOfRange(idx, idx + len)
                        idx += len
                        val nestedState = decodeBeelineWireCommand(nested)
                        if (nestedState != null) return nestedState
                    }
                    5 -> idx += 4 // 32-bit float/fixed32
                    1 -> idx += 8 // 64-bit double/fixed64
                    else -> break
                }
            }

            if (fieldsRead == 0) return null
            val isOffRoute = commandType == 2
            return buildSyntheticFrame(
                maneuverIdx = maneuverIdx,
                roundExit = roundExit,
                distanceMeters = distanceMeters,
                speedKmh = speedKmh,
                speedLimitKmh = speedLimitKmh,
                headingDeg = headingDeg,
                isOffRoute = isOffRoute
            )
        }

        private fun readVarint(bytes: ByteArray, startIdx: Int): Pair<Long, Int>? {
            var result = 0L
            var shift = 0
            var i = startIdx
            while (i < bytes.size && shift < 64) {
                val b = bytes[i].toInt()
                i++
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) {
                    return result to i
                }
                shift += 7
            }
            return null
        }

        private fun buildSyntheticFrame(
            maneuverIdx: Int,
            roundExit: Int,
            distanceMeters: Int,
            speedKmh: Int,
            speedLimitKmh: Int,
            headingDeg: Int,
            isOffRoute: Boolean
        ): TelemetryFrameState {
            val maneuver = ManeuverKind.entries.getOrElse(maneuverIdx) { ManeuverKind.STRAIGHT }
            val turnY = (-0.15f - (distanceMeters.coerceIn(0, 350) / 350f) * 0.40f)
            val turnEndX = when (maneuver) {
                ManeuverKind.TURN_LEFT, ManeuverKind.SLIGHT_LEFT, ManeuverKind.SHARP_LEFT, ManeuverKind.ROUNDABOUT_LEFT -> -0.65f
                ManeuverKind.TURN_RIGHT, ManeuverKind.SLIGHT_RIGHT, ManeuverKind.SHARP_RIGHT, ManeuverKind.ROUNDABOUT_RIGHT -> 0.65f
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
                speedLimitApproachRatio = (speedKmh.toFloat() / speedLimitKmh.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                headingDegrees = headingDeg
            )
        }
    }
}
