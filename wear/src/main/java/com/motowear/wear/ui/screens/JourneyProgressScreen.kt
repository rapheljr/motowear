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
import com.motowear.wear.theme.BeelineDimens
import com.motowear.wear.theme.BeelineTypography

/**
 * Mode 4 (`JOURNEY_PROGRESS`) Renderer.
 * Matches `blk_journey_eta.webp` pixel-for-pixel:
 * - Perimeter Progress Ring: 5dp thick white circular arc sweeping clockwise from 6 o'clock (102° to 438°)
 *   proportional to `routeCompletionRatio`, over a 2dp dark grey background ring.
 * - 6 o'clock Destination Badge: Dark grey pill housing a white map-pin icon and a white route-dot icon.
 * - Top Metric: Remaining trip duration (`2hr 25m`) rendered in 28sp Bold BeelineAmber (#FFCC00).
 * - Middle Dual Columns:
 *   - Left Column: Elapsed ride time (`07:20`) in 22sp white numerals above a 14dp grey clock icon.
 *   - Right Column: Estimated Time of Arrival (`09:45`) in 22sp white numerals above a 14dp grey checkered-flag icon.
 * - Bottom Metric: Remaining distance (`158 mi`) in 17sp SemiBold white text.
 */
@Composable
fun JourneyProgressScreen(
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
            // 1. Perimeter Progress Ring (102° to 438° -> 336° total span)
            val inset = BeelineDimens.CircularSafeZoneInset.toPx()
            val ringTopLeft = Offset(inset, inset)
            val ringSize = Size(w - inset * 2f, h - inset * 2f)
            val totalSweep = 336f

            // Background track
            drawArc(
                color = BeelineColors.BeelineDarkGrey,
                startAngle = 102f,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Active 5dp white progress arc sweeping clockwise from 102°
            drawArc(
                color = BeelineColors.BeelineWhite,
                startAngle = 102f,
                sweepAngle = totalSweep * state.routeCompletionRatio.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 2. Top Metric: Remaining Trip Duration (2hr 25m in BeelineAmber)
            val hrs = state.remainingDurationMin / 60
            val mins = state.remainingDurationMin % 60
            val durationAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BeelineColors.BeelineAmber)) {
                    append("$hrs")
                }
                withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = BeelineColors.BeelineAmber)) {
                    append("hr ")
                }
                withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BeelineColors.BeelineAmber)) {
                    append("$mins")
                }
                withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = BeelineColors.BeelineAmber)) {
                    append("m")
                }
            }
            val durationLayout = textMeasurer.measure(
                text = durationAnnotated,
                style = BeelineTypography.DurationAmber28
            )
            drawText(
                textLayoutResult = durationLayout,
                topLeft = Offset(cx - durationLayout.size.width / 2f, h * 0.20f)
            )

            // 3. Middle Dual Columns: Left (07:20 + Clock Icon) & Right (09:45 + Checkered Flag Icon)
            val leftColX = w * 0.29f
            val rightColX = w * 0.71f
            val timeRowY = h * 0.43f

            val elapsedLayout = textMeasurer.measure(
                text = state.elapsedTimeStr,
                style = BeelineTypography.MetricBold22
            )
            drawText(
                textLayoutResult = elapsedLayout,
                topLeft = Offset(leftColX - elapsedLayout.size.width / 2f, timeRowY)
            )

            val etaLayout = textMeasurer.measure(
                text = state.etaTimeStr,
                style = BeelineTypography.MetricBold22
            )
            drawText(
                textLayoutResult = etaLayout,
                topLeft = Offset(rightColX - etaLayout.size.width / 2f, timeRowY)
            )

            // Left Icon: 14dp Grey Clock Icon
            val iconCenterY = timeRowY + elapsedLayout.size.height + 11.dp.toPx()
            val clockRadius = 7.dp.toPx()
            drawCircle(
                color = BeelineColors.BeelineGrey,
                radius = clockRadius,
                center = Offset(leftColX, iconCenterY),
                style = Stroke(width = 1.6.dp.toPx())
            )
            drawLine(
                color = BeelineColors.BeelineGrey,
                start = Offset(leftColX, iconCenterY),
                end = Offset(leftColX, iconCenterY - 4.dp.toPx()),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = BeelineColors.BeelineGrey,
                start = Offset(leftColX, iconCenterY),
                end = Offset(leftColX + 3.5.dp.toPx(), iconCenterY + 1.5.dp.toPx()),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Right Icon: 14dp Grey Checkered Flag Icon
            val flagLeft = rightColX - 6.dp.toPx()
            val flagTop = iconCenterY - 6.dp.toPx()
            // Flag pole
            drawLine(
                color = BeelineColors.BeelineGrey,
                start = Offset(flagLeft, flagTop),
                end = Offset(flagLeft, flagTop + 13.dp.toPx()),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Flag banner outline + checker grid
            drawRect(
                color = BeelineColors.BeelineGrey,
                topLeft = Offset(flagLeft, flagTop),
                size = Size(11.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawRect(
                color = BeelineColors.BeelineGrey,
                topLeft = Offset(flagLeft + 5.5.dp.toPx(), flagTop),
                size = Size(5.5.dp.toPx(), 4.dp.toPx())
            )
            drawRect(
                color = BeelineColors.BeelineGrey,
                topLeft = Offset(flagLeft, flagTop + 4.dp.toPx()),
                size = Size(5.5.dp.toPx(), 4.dp.toPx())
            )

            // 4. Bottom Metric: Remaining distance (158 mi or km)
            val distInt = state.remainingDistance.toInt()
            val distUnit = if (state.useImperialUnits) "mi" else "km"
            val remDistAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = BeelineColors.BeelineWhite)) {
                    append("$distInt ")
                }
                withStyle(SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = BeelineColors.BeelineGrey)) {
                    append(distUnit)
                }
            }
            val remDistLayout = textMeasurer.measure(
                text = remDistAnnotated,
                style = BeelineTypography.DistanceRemaining17
            )
            drawText(
                textLayoutResult = remDistLayout,
                topLeft = Offset(cx - remDistLayout.size.width / 2f, h * 0.72f)
            )

            // 5. 6 o'clock Destination Badge (Dark grey pill housing white map-pin icon and white route-dot icon)
            val pillW = 36.dp.toPx()
            val pillH = 20.dp.toPx()
            val pillCenterY = h - inset
            drawRoundRect(
                color = BeelineColors.BeelineDarkGrey,
                topLeft = Offset(cx - pillW / 2f, pillCenterY - pillH / 2f),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2f, pillH / 2f)
            )
            // Left white pin head inside badge
            val pinX = cx - 6.dp.toPx()
            drawCircle(
                color = BeelineColors.BeelineWhite,
                radius = 3.2.dp.toPx(),
                center = Offset(pinX, pillCenterY - 1.5.dp.toPx())
            )
            val pinTip = Path().apply {
                moveTo(pinX - 2.5.dp.toPx(), pillCenterY)
                lineTo(pinX, pillCenterY + 4.2.dp.toPx())
                lineTo(pinX + 2.5.dp.toPx(), pillCenterY)
                close()
            }
            drawPath(path = pinTip, color = BeelineColors.BeelineWhite)

            // Right white route-dot inside badge
            drawCircle(
                color = BeelineColors.BeelineWhite,
                radius = 3.dp.toPx(),
                center = Offset(cx + 6.5.dp.toPx(), pillCenterY)
            )
        }
    }
}
