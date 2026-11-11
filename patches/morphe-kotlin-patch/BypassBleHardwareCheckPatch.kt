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
        // Target 1: Public Unobfuscated ViewModel Entry Point
        val viewModelClass = context.findClass("co.beeline.ui.device.PairingViewModel")
        viewModelClass?.findMethod("handleSuccess")?.replaceWith(
            """
            const/4 v0, 0x1
            return v0
            """
        )

        // Target 2: R8 Obfuscated Device Pairing Core Implementation
        val obfuscatedPairingClass = context.findClass("defpackage.gk0")
            ?: context.findClass("co.beeline.device.pairing.BeelineDevicePairing")
        obfuscatedPairingClass?.findMethod("a")?.replaceWith(
            """
            const/4 v0, 0x1
            return v0
            """
        )
    }
}
