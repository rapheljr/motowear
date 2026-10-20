package com.motowear.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.wear.theme.BeelineColors
import com.motowear.wear.theme.BeelineTypography
import com.motowear.wear.ui.components.BeelineCanvasPrimitives.drawBottomConfirmDome
import com.motowear.wear.ui.components.BeelineCanvasPrimitives.drawSideEdgeDButton
import com.motowear.wear.ui.components.EdgeGlyphType

/**
 * Mode 7 (`WAYPOINT_SKIPPING`) Renderer.
 * Matches `blk_skip_waypoiny_*.webp` pixel-for-pixel:
 * - Top Icon: Double map-pin icon with fast-forward chevrons (`>>`) in BeelineGrey.
 * - Center Counter: Large fraction (`1/3`) where current index (`1`) is 36sp Bold white and
 *   denominator (`/3`) is 26sp Bold grey, stacked above `waypoints` (18sp white).
 * - 9 o'clock & 3 o'clock Prompts: Left D-shaped button (`<`) in BeelineDarkGrey when at index 1;
 *   right D-shaped button (`>`) in BeelineWhite.
 * - 6 o'clock Confirm Dome: 38dp x 34dp BeelineAmber (#FFCC00) D-shaped dome anchored to bottom bezel
 *   containing a 2.8dp black checkmark.
 */
@Composable
fun WaypointSkippingScreen(
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
            // 1. Top Icon: Double map-pin icon with fast-forward chevrons (>>) in BeelineGrey
            val iconY = h * 0.23f
            val pinSpacing = 14.dp.toPx()

            listOf(cx - pinSpacing, cx + pinSpacing).forEach { pinX ->
                drawCircle(
                    color = BeelineColors.BeelineGrey,
                    radius = 5.dp.toPx(),
                    center = Offset(pinX, iconY - 3.dp.toPx()),
                    style = Stroke(width = 1.6.dp.toPx())
                )
                val pinTip = Path().apply {
                    moveTo(pinX - 4.dp.toPx(), iconY)
                    lineTo(pinX, iconY + 7.dp.toPx())
                    lineTo(pinX + 4.dp.toPx(), iconY)
                }
                drawPath(
                    path = pinTip,
                    color = BeelineColors.BeelineGrey,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Fast-forward chevrons (>>) between the pins
            listOf(cx - 3.dp.toPx(), cx + 2.dp.toPx()).forEach { chevX ->
                val chev = Path().apply {
                    moveTo(chevX - 2.dp.toPx(), iconY - 4.dp.toPx())
                    lineTo(chevX + 2.dp.toPx(), iconY)
                    lineTo(chevX - 2.dp.toPx(), iconY + 4.dp.toPx())
                }
                drawPath(
                    path = chev,
                    color = BeelineColors.BeelineGrey,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 2. Center Counter: Large fraction (1/3) + "waypoints"
            val fractionAnnotated = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = BeelineColors.BeelineWhite
                    )
                ) {
                    append("${state.currentWaypointIdx}")
                }
                withStyle(
                    SpanStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = BeelineColors.BeelineGrey
                    )
                ) {
                    append("/${state.totalWaypoints}")
                }
            }
            val fractionLayout = textMeasurer.measure(
                text = fractionAnnotated,
                style = BeelineTypography.WaypointPrimary36
            )
            val fractionY = h * 0.35f
            drawText(
                textLayoutResult = fractionLayout,
                topLeft = Offset(cx - fractionLayout.size.width / 2f, fractionY)
            )

            val wpLabelLayout = textMeasurer.measure(
                text = "waypoints",
                style = BeelineTypography.LabelWhite18
            )
            drawText(
                textLayoutResult = wpLabelLayout,
                topLeft = Offset(
                    cx - wpLabelLayout.size.width / 2f,
                    fractionY + fractionLayout.size.height + 2.dp.toPx()
                )
            )

            // 3. 9 o'clock (<) and 3 o'clock (>) D-shaped edge prompts
            val leftActive = state.currentWaypointIdx > 1
            val rightActive = state.currentWaypointIdx < state.totalWaypoints

            drawSideEdgeDButton(
                isRightEdge = false,
                borderColor = if (leftActive) BeelineColors.BeelineWhite else BeelineColors.BeelineDarkGrey,
                glyphType = EdgeGlyphType.CHEVRON_LEFT
            )
            drawSideEdgeDButton(
                isRightEdge = true,
                borderColor = if (rightActive) BeelineColors.BeelineWhite else BeelineColors.BeelineDarkGrey,
                glyphType = EdgeGlyphType.CHEVRON_RIGHT
            )

            // 4. 6 o'clock Confirm Dome (BeelineAmber with black checkmark)
            drawBottomConfirmDome(
                canvasWidth = w,
                canvasHeight = h,
                isAmbient = state.isAmbientMode
            )
        }
    }
}
