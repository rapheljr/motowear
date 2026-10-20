package com.motowear.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.wear.theme.BeelineColors
import com.motowear.wear.theme.BeelineTypography
import com.motowear.wear.ui.components.BeelineBrandLogo.drawBeelineRoundel
import com.motowear.wear.ui.components.BeelineCanvasPrimitives.drawSideEdgeDButton
import com.motowear.wear.ui.components.EdgeGlyphType

/**
 * Mode 6 (`QUICK_START`) Renderer.
 * Matches `blk_strart_ride_from_device.webp` pixel-for-pixel while integrating a subtle
 * 12 o'clock Beeline Roundel (`beeline-logo-roundel`) brand crest on the entry screen:
 * - Subtle 18dp Beeline Roundel crest at 12 o'clock (`Y = 14%`).
 * - Centered 38dp concentric target/record icon (1.5dp white outer ring with a 14dp solid white center circle).
 * - Stacked two-line label `Start\nride` in 26sp Bold white text.
 * - Active D-shaped button prompt (`>`) anchored at 3 o'clock (`Alignment.CenterEnd`).
 */
@Composable
fun QuickStartScreen(
    state: TelemetryFrameState,
    modifier: Modifier = Modifier
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
            // 0. Subtle 12 o'clock Beeline Roundel brand badge on the non-riding Quick Start launcher screen
            drawBeelineRoundel(
                center = Offset(cx, h * 0.13f),
                radius = 9.dp.toPx(),
                monochrome = state.isAmbientMode
            )

            // 1. Centered 38dp concentric target/record icon
            val iconCenter = Offset(cx, h * 0.35f)
            val outerRadius = 19.dp.toPx() // 38dp diameter
            val innerRadius = 7.dp.toPx()  // 14dp diameter

            drawCircle(
                color = BeelineColors.BeelineWhite,
                radius = outerRadius,
                center = iconCenter,
                style = Stroke(width = 1.6.dp.toPx())
            )
            drawCircle(
                color = BeelineColors.BeelineWhite,
                radius = innerRadius,
                center = iconCenter
            )

            // 2. Stacked two-line label "Start\nride" in 26sp Bold white text
            val labelLayout = textMeasurer.measure(
                text = "Start\nride",
                style = BeelineTypography.TitleBold26.copy(
                    textAlign = TextAlign.Center
                )
            )
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(
                    cx - labelLayout.size.width / 2f,
                    h * 0.50f
                )
            )

            // 3. Active D-shaped button prompt (>) anchored at 3 o'clock
            drawSideEdgeDButton(
                isRightEdge = true,
                borderColor = BeelineColors.BeelineWhite,
                glyphType = EdgeGlyphType.CHEVRON_RIGHT
            )
        }
    }
}
