package com.motowear.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.wear.theme.BeelineColors
import com.motowear.wear.theme.BeelineTypography
import com.motowear.wear.ui.components.BeelineCanvasPrimitives.drawSpeedLimitBadge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mode 3 (`SPEEDO`) Renderer.
 * Matches `blk_speedo_*.webp` pixel-for-pixel:
 * - Left Radial Bar Gauge (140° to 255°): 30 discrete white rounded-rectangle tick segments (3dp x 16dp)
 *   arranged radially along the left bezel.
 * - Top & Right Bezel Dots (270° to 40°): Sparse 2dp BeelineGrey circular dots marking increments above the speed limit,
 *   anchored by a 4dp BeelineRed dot at 12 o'clock (270°).
 * - Center Stack:
 *   1. Circular Speed Limit badge (`100`) at top center (50dp diameter).
 *   2. Current GPS speed (`80`) in 44sp Bold white numerals with `km/h` (15sp) centered below.
 *   3. Pill-shaped rounded border (1.2dp white stroke) at bottom center displaying average trip speed (`72 km/h`).
 */
@Composable
fun SpeedoScreen(
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
        val cy = h / 2f

        withTransform({
            translate(state.burnInOffsetPxX, state.burnInOffsetPxY)
        }) {
            // 1. Left Radial Bar Gauge (30 discrete white rounded-rectangle tick segments from ~130° to 258°)
            val totalBars = 30
            val startAngleDeg = 132f
            val endAngleDeg = 258f
            val speedRatio = (state.currentSpeedKmh.toFloat() / state.speedLimitKmh.coerceAtLeast(1).toFloat())
                .coerceIn(0f, 1f)
            val activeBarCount = (totalBars * speedRatio).toInt()

            val barLength = 15.dp.toPx()
            val barThickness = 2.8.dp.toPx()
            val outerRadius = (w / 2f) - 8.dp.toPx()

            for (i in 0 until totalBars) {
                val fraction = i.toFloat() / (totalBars - 1).toFloat()
                val angleDeg = startAngleDeg + fraction * (endAngleDeg - startAngleDeg)
                val isIlluminated = i <= activeBarCount

                withTransform({
                    rotate(degrees = angleDeg, pivot = Offset(cx, cy))
                }) {
                    drawRoundRect(
                        color = if (isIlluminated) BeelineColors.BeelineWhite else BeelineColors.BeelineDarkGrey,
                        topLeft = Offset(cx + outerRadius - barLength, cy - barThickness / 2f),
                        size = Size(barLength, barThickness),
                        cornerRadius = CornerRadius(barThickness / 2f, barThickness / 2f)
                    )
                }
            }

            // 2. 12 o'clock (270°) BeelineRed dot + Top/Right Bezel Grey Dots (292°, 315°, 338°, 0°, 22°, 45°)
            val dotRadiusOrbit = outerRadius - barLength / 2f
            val redRad = 270.0 * PI / 180.0
            drawCircle(
                color = BeelineColors.BeelineRed,
                radius = 3.5.dp.toPx(),
                center = Offset(
                    x = cx + (dotRadiusOrbit * cos(redRad)).toFloat(),
                    y = cy + (dotRadiusOrbit * sin(redRad)).toFloat()
                )
            )

            val overLimitAngles = listOf(292f, 315f, 338f, 360f, 22f, 45f)
            for (angle in overLimitAngles) {
                val rad = angle.toDouble() * PI / 180.0
                drawCircle(
                    color = BeelineColors.BeelineGrey,
                    radius = 2.dp.toPx(),
                    center = Offset(
                        x = cx + (dotRadiusOrbit * cos(rad)).toFloat(),
                        y = cy + (dotRadiusOrbit * sin(rad)).toFloat()
                    )
                )
            }

            // 3. Center Stack — Top: Speed Limit Badge (100, 50dp diameter)
            drawSpeedLimitBadge(
                center = Offset(cx, h * 0.24f),
                diameterDp = 50f,
                speedLimitValue = state.speedLimitKmh,
                textMeasurer = textMeasurer,
                showApproachArc = false,
                isAmbient = state.isAmbientMode
            )

            // 4. Center Stack — Middle: Current GPS Speed (80 in 44sp Bold White) + unit (km/h in 15sp)
            val speedLayout = textMeasurer.measure(
                text = state.currentSpeedKmh.toString(),
                style = BeelineTypography.SpeedHero44
            )
            val unitLabel = if (state.useImperialUnits) "mph" else "km/h"
            val unitLayout = textMeasurer.measure(
                text = unitLabel,
                style = BeelineTypography.UnitSmall15.copy(color = BeelineColors.BeelineWhite)
            )

            val speedTopY = cy - speedLayout.size.height * 0.42f
            drawText(
                textLayoutResult = speedLayout,
                topLeft = Offset(cx - speedLayout.size.width / 2f, speedTopY)
            )
            drawText(
                textLayoutResult = unitLayout,
                topLeft = Offset(
                    cx - unitLayout.size.width / 2f,
                    speedTopY + speedLayout.size.height - 4.dp.toPx()
                )
            )

            // 5. Center Stack — Bottom: Pill-shaped rounded border (1.2dp white stroke) with average speed (72 km/h)
            val pillWidth = 76.dp.toPx()
            val pillHeight = 44.dp.toPx()
            val pillTop = h * 0.72f
            val pillLeft = cx - pillWidth / 2f

            drawRoundRect(
                color = BeelineColors.BeelineWhite,
                topLeft = Offset(pillLeft, pillTop),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(pillHeight / 2f, pillHeight / 2f),
                style = Stroke(width = 1.2.dp.toPx())
            )

            val avgValLayout = textMeasurer.measure(
                text = state.avgSpeedKmh.toString(),
                style = BeelineTypography.DistanceRemaining17
            )
            val avgUnitLayout = textMeasurer.measure(
                text = unitLabel,
                style = BeelineTypography.UnitSmall15.copy(color = BeelineColors.BeelineGrey)
            )

            drawText(
                textLayoutResult = avgValLayout,
                topLeft = Offset(
                    cx - avgValLayout.size.width / 2f,
                    pillTop + 4.dp.toPx()
                )
            )
            drawText(
                textLayoutResult = avgUnitLayout,
                topLeft = Offset(
                    cx - avgUnitLayout.size.width / 2f,
                    pillTop + 4.dp.toPx() + avgValLayout.size.height - 3.dp.toPx()
                )
            )
        }
    }
}
