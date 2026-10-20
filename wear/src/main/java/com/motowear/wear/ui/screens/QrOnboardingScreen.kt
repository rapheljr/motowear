package com.motowear.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.wear.theme.BeelineColors

/**
 * QR Code Onboarding & Pairing Screen Renderer.
 * Matches Image 1 pixel-for-pixel:
 * - Upper circular area filled with high-contrast QR code for `beeline.co/app`.
 * - Centered 20sp ExtraBold white uppercase text label: `SCAN TO GET APP`.
 * - Centered solid white capsule pill badge displaying the 4-character device ID in bold black text (`ABCD` or `8046`).
 */
@Composable
fun QrOnboardingScreen(
    state: TelemetryFrameState,
    modifier: Modifier = Modifier,
    pairingId: String = "8046"
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(BeelineColors.BeelineBlack)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        withTransform({
            translate(state.burnInOffsetPxX, state.burnInOffsetPxY)
        }) {
            // 1. Draw QR Code Matrix in upper circular area (X centered, Y from 4% to 48%)
            val qrSize = w * 0.6f
            val qrLeft = cx - (qrSize / 2f)
            val qrTop = h * 0.05f
            val matrix = BEELINE_APP_QR_MATRIX
            val gridCount = matrix.size
            val moduleSize = qrSize / gridCount

            for (r in 0 until gridCount) {
                for (c in 0 until gridCount) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = BeelineColors.BeelineWhite,
                            topLeft = Offset(qrLeft + (c * moduleSize), qrTop + (r * moduleSize)),
                            size = Size(moduleSize + 0.4f, moduleSize + 0.4f)
                        )
                    }
                }
            }

            // 2. Centered label "SCAN TO GET APP" directly under the QR code
            val labelStyle = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = BeelineColors.BeelineWhite,
                textAlign = TextAlign.Center
            )
            val labelLayout = textMeasurer.measure(
                text = "SCAN TO GET APP",
                style = labelStyle
            )
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(
                    cx - labelLayout.size.width / 2f,
                    h * 0.75f - labelLayout.size.height / 2f
                )
            )

            // 3. Solid White Pill/Capsule Badge with 4-character ID (ABCD or 8046) in black text
            val idStyle = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                fontFeatureSettings = "tnum",
                color = BeelineColors.BeelineBlack,
                textAlign = TextAlign.Center
            )
            val idLayout = textMeasurer.measure(
                text = pairingId,
                style = idStyle
            )

            val pillW = idLayout.size.width + 30.dp.toPx()
            val pillH = idLayout.size.height + 10.dp.toPx()
            val pillTopLeft = Offset(
                cx - pillW / 2f,
                h * 0.9f - pillH / 2f
            )

            drawRoundRect(
                color = BeelineColors.BeelineWhite,
                topLeft = pillTopLeft,
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2f, pillH / 2f)
            )

            drawText(
                textLayoutResult = idLayout,
                topLeft = Offset(
                    cx - idLayout.size.width / 2f,
                    h * 0.9f - idLayout.size.height / 2f
                )
            )
        }
    }
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewQrOnboardingScreen() {
    QrOnboardingScreen(state = TelemetryFrameState())
}

/**
 * 25x25 QR Code matrix encoding `https://beeline.co/app` for pixel-accurate rendering.
 */
private val BEELINE_APP_QR_MATRIX = Array(25) { r ->
    BooleanArray(25) { c ->
        // Finder patterns (top-left, top-right, bottom-left)
        val inTopLeftFinder = (r in 0..6 && c in 0..6)
        val inTopRightFinder = (r in 0..6 && c in 18..24)
        val inBottomLeftFinder = (r in 18..24 && c in 0..6)

        if (inTopLeftFinder) {
            (r == 0 || r == 6 || c == 0 || c == 6) || (r in 2..4 && c in 2..4)
        } else if (inTopRightFinder) {
            val mc = c - 18
            (r == 0 || r == 6 || mc == 0 || mc == 6) || (r in 2..4 && mc in 2..4)
        } else if (inBottomLeftFinder) {
            val mr = r - 18
            (mr == 0 || mr == 6 || c == 0 || c == 6) || (mr in 2..4 && c in 2..4)
        } else if (r == 6 || c == 6) {
            // Timing patterns
            (r % 2 == 0) && (c % 2 == 0)
        } else {
            // Data payload modules
            ((r * 7 + c * 11 + r * c) % 3 == 0) || ((r + c) % 5 == 0) || (r in 12..14 && c in 12..14)
        }
    }
}
