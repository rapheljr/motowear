package com.motowear.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import java.util.Locale

/**
 * Mode 8 (`JOURNEY_SUMMARY`) Renderer.
 * Matches `blk_ride_summary.webp` pixel-for-pixel:
 * - Three Left-Aligned Telemetry Rows (centered vertically as a block):
 *   1. Distance Row: BeelineAmber route-pin-and-squiggle icon + `98.2` (26sp Bold white) + `km` (24sp SemiBold grey).
 *   2. Duration Row: BeelineAmber clock icon + `2` (22sp Bold white) + `hr` (20sp grey) + `32` (22sp Bold white) + `min` (20sp grey).
 *   3. Average Speed Row: BeelineAmber speedometer gauge icon + `38.8` (22sp Bold white) + `km/h` (20sp grey).
 * - 6 o'clock Confirm Dome: BeelineAmber dome with black checkmark at bottom center to dismiss and save the ride.
 */
@Composable
fun JourneySummaryScreen(
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

        withTransform({
            translate(state.burnInOffsetPxX, state.burnInOffsetPxY)
        }) {
            val iconColCenterX = w * 0.25f
            val textStartX = w * 0.34f

            // Row 1: Distance Row (98.2 km)
            val row1Y = h * 0.24f
            val row1CenterY = row1Y + 14.dp.toPx()

            // BeelineAmber route-pin-and-squiggle icon
            drawCircle(
                color = BeelineColors.BeelineAmber,
                radius = 4.dp.toPx(),
                center = Offset(iconColCenterX, row1CenterY - 5.dp.toPx()),
                style = Stroke(width = 1.8.dp.toPx())
            )
            val routeSquiggle = Path().apply {
                moveTo(iconColCenterX, row1CenterY - 1.dp.toPx())
                lineTo(iconColCenterX, row1CenterY + 3.dp.toPx())
                quadraticTo(
                    iconColCenterX - 6.dp.toPx(),
                    row1CenterY + 5.dp.toPx(),
                    iconColCenterX + 5.dp.toPx(),
                    row1CenterY + 8.dp.toPx()
                )
            }
            drawPath(
                path = routeSquiggle,
                color = BeelineColors.BeelineAmber,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val distStr = String.format(Locale.US, "%.1f", state.summaryDistanceKm)
            val distUnit = if (state.useImperialUnits) "mi" else "km"
            val row1Annotated = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BeelineColors.BeelineWhite)) {
                    append("$distStr ")
                }
                withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = BeelineColors.BeelineGrey)) {
                    append(distUnit)
                }
            }
            val row1Layout = textMeasurer.measure(
                text = row1Annotated,
                style = BeelineTypography.TitleBold26
            )
            drawText(
                textLayoutResult = row1Layout,
                topLeft = Offset(textStartX, row1Y)
            )

            // Row 2: Duration Row (2 hr 32 min)
            val row2Y = h * 0.43f
            val row2CenterY = row2Y + 13.dp.toPx()

            // BeelineAmber clock icon
            val clockR = 8.dp.toPx()
            drawCircle(
                color = BeelineColors.BeelineAmber,
                radius = clockR,
                center = Offset(iconColCenterX, row2CenterY),
                style = Stroke(width = 1.8.dp.toPx())
            )
            drawLine(
                color = BeelineColors.BeelineAmber,
                start = Offset(iconColCenterX, row2CenterY),
                end = Offset(iconColCenterX, row2CenterY - 4.5.dp.toPx()),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = BeelineColors.BeelineAmber,
                start = Offset(iconColCenterX, row2CenterY),
                end = Offset(iconColCenterX + 4.dp.toPx(), row2CenterY + 1.5.dp.toPx()),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )

            val row2Annotated = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BeelineColors.BeelineWhite)) {
                    append("${state.summaryDurationHours} ")
                }
                withStyle(SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Normal, color = BeelineColors.BeelineGrey)) {
                    append("hr ")
                }
                withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BeelineColors.BeelineWhite)) {
                    append("${state.summaryDurationMinutes} ")
                }
                withStyle(SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Normal, color = BeelineColors.BeelineGrey)) {
                    append("min")
                }
            }
            val row2Layout = textMeasurer.measure(
                text = row2Annotated,
                style = BeelineTypography.MetricBold22
            )
            drawText(
                textLayoutResult = row2Layout,
                topLeft = Offset(textStartX, row2Y)
            )

            // Row 3: Average Speed Row (38.8 km/h)
            val row3Y = h * 0.61f
            val row3CenterY = row3Y + 13.dp.toPx()

            // BeelineAmber speedometer gauge icon
            val gaugeR = 8.5.dp.toPx()
            drawArc(
                color = BeelineColors.BeelineAmber,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = Offset(iconColCenterX - gaugeR, row3CenterY - gaugeR),
                size = Size(gaugeR * 2f, gaugeR * 2f),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )
            // Gauge needle
            drawLine(
                color = BeelineColors.BeelineAmber,
                start = Offset(iconColCenterX, row3CenterY + 1.dp.toPx()),
                end = Offset(iconColCenterX + 4.5.dp.toPx(), row3CenterY - 4.5.dp.toPx()),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )

            val avgSpeedStr = String.format(Locale.US, "%.1f", state.summaryAvgSpeedKmh)
            val speedUnit = if (state.useImperialUnits) "mph" else "km/h"
            val row3Annotated = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BeelineColors.BeelineWhite)) {
                    append("$avgSpeedStr ")
                }
                withStyle(SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Normal, color = BeelineColors.BeelineGrey)) {
                    append(speedUnit)
                }
            }
            val row3Layout = textMeasurer.measure(
                text = row3Annotated,
                style = BeelineTypography.MetricBold22
            )
            drawText(
                textLayoutResult = row3Layout,
                topLeft = Offset(textStartX, row3Y)
            )

            // 4. 6 o'clock Confirm Dome
            drawBottomConfirmDome(
                canvasWidth = w,
                canvasHeight = h,
                isAmbient = state.isAmbientMode
            )
        }
    }
}
