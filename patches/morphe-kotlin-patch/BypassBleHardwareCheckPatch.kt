package software.morphe.patches.beeline

import software.morphe.patcher.annotation.CompatibleWith
import software.morphe.patcher.annotation.Patch
import software.morphe.patcher.patch.BytecodePatch
import software.morphe.patcher.patch.BytecodeContext

/**
 * Morphe Bytecode Patch for co.beeline (compatible with spookyexe/morphe-patches framework).
 * Bypasses hardware signature validation in co.beeline BLE pairing, allowing direct pairing
 * with MotoWear OS smartwatch client and custom BLE peripherals.
 */
@Patch(
    name = "Bypass BLE hardware pairing check",
    description = "Bypasses the physical hardware signature validation in co.beeline BLE pairing, allowing direct pairing with MotoWear OS and custom BLE peripherals.",
    compatibleWith = [CompatibleWith("co.beeline", ["4.2.8014", "4.2.8704"])]
)
object BypassBleHardwareCheckPatch : BytecodePatch() {

    override fun execute(context: BytecodeContext) {
        // Target Beeline Device Pairing & Connection Handshake Validation
        val pairingClass = context.findClass("co.beeline.device.pairing.BeelineDevicePairing")
        pairingClass?.findMethod("handleSuccess")?.replaceWith(
            """
            const/4 v0, 0x1
            return v0
            """
        )

        val blePeripheralClass = context.findClass("co.beeline.bluetooth.device.BlePeripheral")
        blePeripheralClass?.findMethod("setPickedDevice")?.replaceWith(
            """
            return-void
            """
        )
    }
}
