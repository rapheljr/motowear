package com.motowear.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.wear.theme.BeelineColors
import com.motowear.wear.theme.BeelineTypography
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Mode 5 (`JOURNEY_TRACKING`) Renderer.
 * Matches `blk_Journey_tracking.webp` pixel-for-pixel:
 * - Rotating Compass Bezel: Full 360° ring of 6° minor ticks (BeelineDarkGrey, 6dp length) and
 *   30° major ticks (BeelineWhite, 10dp length). Cardinal points (`N`, `E`, `S`, `W`) replace ticks
 *   at 90° intervals. Rotates counter to `headingDegrees` while a fixed BeelineRed downward-pointing
 *   triangle stays locked at 12 o'clock.
 * - Heading Readout: Numerical bearing (`330°`) in 22sp Bold BeelineAmber stacked above cardinal (`NW`)
 *   in 15sp Bold BeelineAmber.
 * - Center Rolling Odometer: Three main digit cells (34dp x 40dp, 1.5dp grey rounded border) displaying
 *   whole km/miles (`0`, `9`, `9`) with leading zeros dimmed to BeelineGrey (#7A7A7E) and active digits
 *   in BeelineWhite. Smaller fourth top-aligned cell (22dp x 24dp) displays tenths (`9`) above `km`.
 * - Bottom Clock & Pager: `12:55 am` in 19sp white text above a two-dot page indicator (`○ ●`).
 */
@Composable
fun JourneyTrackingScreen(
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
            val outerRadius = (w / 2f) - 6.dp.toPx()
            val minorTickLen = 6.dp.toPx()
            val majorTickLen = 10.dp.toPx()

            // 1. Rotating Compass Bezel (rotates counter to headingDegrees; 0° North at -90° / 12 o'clock)
            for (deg in 0 until 360 step 6) {
                val relativeDeg = (deg - state.headingDegrees).toFloat()
                val canvasAngleDeg = relativeDeg - 90f

                val isCardinal = deg % 90 == 0
                val isMajor = deg % 30 == 0

                if (isCardinal) {
                    val label = when (deg) {
                        0 -> "N"
                        90 -> "E"
                        180 -> "S"
                        else -> "W"
                    }
                    withTransform({
                        rotate(degrees = relativeDeg, pivot = Offset(cx, cy))
                    }) {
                        val cardinalLayout = textMeasurer.measure(
                            text = label,
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = BeelineColors.BeelineWhite
                            )
                        )
                        drawText(
                            textLayoutResult = cardinalLayout,
                            topLeft = Offset(
                                cx - cardinalLayout.size.width / 2f,
                                cy - outerRadius + 1.dp.toPx()
                            )
                        )
                    }
                } else {
                    val tickLen = if (isMajor) majorTickLen else minorTickLen
                    val tickColor = if (isMajor) BeelineColors.BeelineWhite else BeelineColors.BeelineDarkGrey
                    val tickStroke = if (isMajor) 2.dp.toPx() else 1.3.dp.toPx()

                    withTransform({
                        rotate(degrees = canvasAngleDeg, pivot = Offset(cx, cy))
                    }) {
                        drawLine(
                            color = tickColor,
                            start = Offset(cx + outerRadius - tickLen, cy),
                            end = Offset(cx + outerRadius, cy),
                            strokeWidth = tickStroke,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Fixed 12 o'clock BeelineRed downward-pointing pointer triangle
            val pointerTopY = 2.dp.toPx()
            val pointerPath = Path().apply {
                moveTo(cx - 5.dp.toPx(), pointerTopY)
                lineTo(cx + 5.dp.toPx(), pointerTopY)
                lineTo(cx, pointerTopY + 7.dp.toPx())
                close()
            }
            drawPath(path = pointerPath, color = BeelineColors.BeelineRed)

            // 2. Heading Readout (330° stacked above NW in BeelineAmber)
            val bearingLayout = textMeasurer.measure(
                text = "${state.headingDegrees}°",
                style = BeelineTypography.MetricAmber22
            )
            val cardinalLayout = textMeasurer.measure(
                text = state.cardinalDirection,
                style = BeelineTypography.CardinalAmber15
            )
            val bearingY = h * 0.16f
            drawText(
                textLayoutResult = bearingLayout,
                topLeft = Offset(cx - bearingLayout.size.width / 2f, bearingY)
            )
            drawText(
                textLayoutResult = cardinalLayout,
                topLeft = Offset(
                    cx - cardinalLayout.size.width / 2f,
                    bearingY + bearingLayout.size.height - 2.dp.toPx()
                )
            )

            // 3. Center Rolling Odometer (3 main digit cells + 1 tenths cell + km label)
            val clampedOdo = state.odometerKm.coerceIn(0f, 999.9f)
            val wholeVal = floor(clampedOdo).toInt()
            val tenthsVal = ((clampedOdo - wholeVal) * 10f).roundToInt().coerceIn(0, 9)

            val dHundreds = (wholeVal / 100) % 10
            val dTens = (wholeVal / 10) % 10
            val dOnes = wholeVal % 10
            val digits = listOf(dHundreds, dTens, dOnes)

            val mainCellW = 34.dp.toPx()
            val mainCellH = 40.dp.toPx()
            val tenthsCellW = 22.dp.toPx()
            val tenthsCellH = 24.dp.toPx()
            val cellGap = 4.dp.toPx()

            val totalOdoWidth = (mainCellW * 3f) + (cellGap * 3f) + tenthsCellW
            val startX = cx - totalOdoWidth / 2f
            val mainTopY = cy - mainCellH * 0.45f

            digits.forEachIndexed { idx, digit ->
                val cellLeft = startX + idx * (mainCellW + cellGap)
                drawRoundRect(
                    color = BeelineColors.BeelineGrey,
                    topLeft = Offset(cellLeft, mainTopY),
                    size = Size(mainCellW, mainCellH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                val isLeadingZero = (idx == 0 && dHundreds == 0) ||
                    (idx == 1 && dHundreds == 0 && dTens == 0)

                val digitLayout = textMeasurer.measure(
                    text = digit.toString(),
                    style = BeelineTypography.MetricBold22.copy(
                        fontSize = 24.sp,
                        color = if (isLeadingZero) BeelineColors.BeelineGrey else BeelineColors.BeelineWhite
                    )
                )
                drawText(
                    textLayoutResult = digitLayout,
                    topLeft = Offset(
                        cellLeft + (mainCellW - digitLayout.size.width) / 2f,
                        mainTopY + (mainCellH - digitLayout.size.height) / 2f
                    )
                )
            }

            // Fourth top-aligned cell (22dp x 24dp) for tenths directly above the `km` unit label
            val tenthsLeft = startX + 3f * (mainCellW + cellGap)
            drawRoundRect(
                color = BeelineColors.BeelineGrey,
                topLeft = Offset(tenthsLeft, mainTopY),
                size = Size(tenthsCellW, tenthsCellH),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            val tenthsLayout = textMeasurer.measure(
                text = tenthsVal.toString(),
                style = BeelineTypography.UnitSmall15.copy(
                    fontWeight = FontWeight.Bold,
                    color = BeelineColors.BeelineWhite
                )
            )
            drawText(
                textLayoutResult = tenthsLayout,
                topLeft = Offset(
                    tenthsLeft + (tenthsCellW - tenthsLayout.size.width) / 2f,
                    mainTopY + (tenthsCellH - tenthsLayout.size.height) / 2f
                )
            )

            val unitStr = if (state.useImperialUnits) "mi" else "km"
            val odoUnitLayout = textMeasurer.measure(
                text = unitStr,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = BeelineColors.BeelineGrey
                )
            )
            drawText(
                textLayoutResult = odoUnitLayout,
                topLeft = Offset(
                    tenthsLeft + (tenthsCellW - odoUnitLayout.size.width) / 2f,
                    mainTopY + tenthsCellH + 2.dp.toPx()
                )
            )

            // 4. Bottom Clock ("12:55 am") & Two-dot Page Indicator (○ ●)
            val clockLayout = textMeasurer.measure(
                text = state.currentClockStr,
                style = BeelineTypography.ClockWhite19
            )
            val clockY = h * 0.69f
            drawText(
                textLayoutResult = clockLayout,
                topLeft = Offset(cx - clockLayout.size.width / 2f, clockY)
            )

            val pagerY = h * 0.84f
            val dotRadius = 3.2.dp.toPx()
            val dotSpacing = 6.dp.toPx()

            // Left dot (○ hollow when subPage == 1)
            if (state.odometerSubPage == 0) {
                drawCircle(
                    color = BeelineColors.BeelineWhite,
                    radius = dotRadius,
                    center = Offset(cx - dotSpacing, pagerY)
                )
            } else {
                drawCircle(
                    color = BeelineColors.BeelineGrey,
                    radius = dotRadius,
                    center = Offset(cx - dotSpacing, pagerY),
                    style = Stroke(width = 1.3.dp.toPx())
                )
            }

            // Right dot (● solid when subPage == 1)
            if (state.odometerSubPage == 1) {
                drawCircle(
                    color = BeelineColors.BeelineWhite,
                    radius = dotRadius,
                    center = Offset(cx + dotSpacing, pagerY)
                )
            } else {
                drawCircle(
                    color = BeelineColors.BeelineGrey,
                    radius = dotRadius,
                    center = Offset(cx + dotSpacing, pagerY),
                    style = Stroke(width = 1.3.dp.toPx())
                )
            }
        }
    }
}
