package com.motowear.wear

import com.motowear.telemetry.ManeuverKind
import com.motowear.telemetry.ProtobufTelemetryMapper
import com.motowear.telemetry.ProtobufTelemetryMapper.toProtoByteArray
import com.motowear.telemetry.ScreenMode
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.wear.ble.BeelineBleGattPeripheralServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit test suite for `:wear` verifying:
 * 1. Compact `0xB2` BLE GATT 10-byte telemetry frame decoding (`BeelineBleGattPeripheralServer`).
 * 2. Official `co.beeline.proto.Command` Wire-Protobuf TLV decoding (`decodeBeelineWireCommand`).
 * 3. Full round-trip Protobuf serialization across all 11 `ScreenMode` values (`ProtobufTelemetryMapper`).
 */
class BleAndProtobufDecoderTest {

    @Test
    fun `decodeBleGattPayload decodes compact 10-byte 0xB2 Beeline BLE frame accurately`() {
        val packet = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0xB2.toByte())                      // Magic byte
            put(ManeuverKind.ROUNDABOUT_RIGHT.ordinal.toByte())
            put(2.toByte())                         // Roundabout exit #2
            putShort(185.toShort())                 // 185m to turn
            put(64.toByte())                        // 64 km/h current speed
            put(80.toByte())                        // 80 km/h speed limit
            putShort(275.toShort())                 // 275° heading
            put(0x00.toByte())                      // On-route
        }.array()

        val state = BeelineBleGattPeripheralServer.decodeBleGattPayload(packet)
        assertNotNull(state)
        assertEquals(ScreenMode.TURN_BY_TURN, state!!.mode)
        assertEquals(ManeuverKind.ROUNDABOUT_RIGHT, state.nextManeuver)
        assertEquals(2, state.roundExitNumber)
        assertEquals(185, state.distanceToNextMeters)
        assertEquals(64, state.currentSpeedKmh)
        assertEquals(80, state.speedLimitKmh)
        assertEquals(275, state.headingDegrees)
    }

    @Test
    fun `decodeBeelineWireCommand decodes official co_beeline Wire TLV varint fields`() {
        // TLV varints: field 1 (commandType=1), field 2 (maneuver=SHARP_LEFT=5), field 3 (distance=120m), field 4 (speed=55), field 7 (exit=3)
        val wirePacket = byteArrayOf(
            0x08, 0x01,             // field 1 = 1 (TURN_BY_TURN)
            0x10, 0x05,             // field 2 = 5 (SHARP_LEFT)
            0x18, 0x78,             // field 3 = 120 (120m)
            0x20, 0x37,             // field 4 = 55 (55 km/h)
            0x38, 0x03              // field 7 = 3 (roundabout exit 3)
        )

        val state = BeelineBleGattPeripheralServer.decodeBeelineWireCommand(wirePacket)
        assertNotNull(state)
        assertEquals(ManeuverKind.SHARP_LEFT, state!!.nextManeuver)
        assertEquals(120, state.distanceToNextMeters)
        assertEquals(55, state.currentSpeedKmh)
        assertEquals(3, state.roundExitNumber)
        assertTrue(state.mapVectors.any { it.isPrimaryRoute })
    }

    @Test
    fun `ProtobufTelemetryMapper preserves all 11 ScreenMode values across serialization round-trip`() {
        for (mode in ScreenMode.entries) {
            val original = TelemetryFrameState(
                mode = mode,
                nextManeuver = ManeuverKind.UTURN,
                distanceToNextMeters = 420
            )
            val bytes = original.toProtoByteArray()
            val restored = ProtobufTelemetryMapper.fromProtoByteArray(bytes)
            assertEquals("Mode $mode failed round-trip", mode, restored.mode)
            assertEquals(ManeuverKind.UTURN, restored.nextManeuver)
            assertEquals(420, restored.distanceToNextMeters)
        }
    }
}
