package software.morphe.patches.beeline

import software.morphe.patcher.annotation.CompatibleWith
import software.morphe.patcher.annotation.Patch
import software.morphe.patcher.patch.BytecodePatch
import software.morphe.patcher.patch.BytecodeContext

/**
 * Morphe Bytecode Patch for co.beeline (compatible with spookyexe/morphe-patches framework).
 * Disables OkHttp CertificatePinner SSL pinning checks in co.beeline, allowing HTTPS login
 * requests to api.beeline.co to succeed cleanly when using custom-signed debug APKs.
 */
@Patch(
    name = "Disable SSL Certificate Pinning",
    description = "Disables OkHttp CertificatePinner validation in co.beeline, allowing HTTPS login requests to api.beeline.co on custom-signed APKs.",
    compatibleWith = [CompatibleWith("co.beeline", ["4.2.8014", "4.2.8704"])]
)
object DisableSslPinningPatch : BytecodePatch() {

    override fun execute(context: BytecodeContext) {
        val pinnerClass = context.findClass("okhttp3.CertificatePinner")
        pinnerClass?.findMethods("check")?.forEach { method ->
            method.replaceWith(
                """
                return-void
                """
            )
        }
    }
}
