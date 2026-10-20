package com.motowear.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.motowear.telemetry.ScreenMode
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.wear.theme.BeelineColors
import com.motowear.wear.theme.BeelineDimens
import com.motowear.wear.theme.BeelineTypography
import com.motowear.wear.ui.components.BeelineCanvasPrimitives.drawManeuverArrow
import com.motowear.wear.ui.components.BeelineCanvasPrimitives.drawRiderChevron
import com.motowear.wear.ui.components.BeelineCanvasPrimitives.drawSideEdgeDButton
import com.motowear.wear.ui.components.BeelineCanvasPrimitives.drawSpeedLimitBadge
import com.motowear.wear.ui.components.EdgeGlyphType
import kotlin.math.hypot

/**
 * Mode 1 (`TURN_BY_TURN`) and Mode 2 (`FAST_REROUTING`) Renderer.
 * Matches `blk_nav.webp` and `blk_rerouting_*.webp` pixel-for-pixel.
 */
@Composable
fun TurnByTurnAndRerouteScreen(
    state: TelemetryFrameState,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val isRerouting = state.mode == ScreenMode.FAST_REROUTING || state.isOffRoute

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(BeelineColors.BeelineBlack)
    ) {
        val w = size.width
        val h = size.height
        val splitY = h * BeelineDimens.HudSplitRatio
        val chevronOrigin = Offset(w * 0.50f, splitY)
        val circleClip = Path().apply {
            addOval(Rect(0f, 0f, w, h))
        }

        withTransform({
            translate(state.burnInOffsetPxX, state.burnInOffsetPxY)
        }) {
            clipPath(circleClip, ClipOp.Intersect) {
                // 1. Bottom HUD Viewport (63% - 100% Y)
                drawRect(
                    color = BeelineColors.BeelineHudBg,
                    topLeft = Offset(0f, splitY),
                    size = Size(w, h - splitY)
                )

                // 8% width vertical center road stripe (#1E1E1E) aligned beneath the rider chevron
                val roadStripeWidth = w * 0.08f
                drawRect(
                    color = BeelineColors.BeelineRoadStripe,
                    topLeft = Offset(chevronOrigin.x - roadStripeWidth / 2f, splitY),
                    size = Size(roadStripeWidth, h - splitY)
                )

                // 2. Top Viewport (0% - 63% Y): 2D Vector Map Canvas
                clipRect(left = 0f, top = 0f, right = w, bottom = splitY) {
                    val mapScale = w * 0.50f

                    val sideStreetStroke = BeelineDimens.SideStreetLineWidth.toPx()
                    val sideStreetGap = 5.dp.toPx()
                    val fadeBrush = Brush.radialGradient(
                        colors = listOf(
                            BeelineColors.BeelineDarkGrey,
                            BeelineColors.BeelineDarkGrey.copy(alpha = 0.85f),
                            BeelineColors.BeelineBlack
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = w * 0.49f
                    )

                    state.mapVectors.filter { !it.isPrimaryRoute }.forEach { segment ->
                        val pts = segment.points.map { pt ->
                            Offset(
                                x = chevronOrigin.x + pt.x * mapScale,
                                y = chevronOrigin.y + pt.y * mapScale
                            )
                        }
                        for (i in 0 until pts.size - 1) {
                            val p1 = pts[i]
                            val p2 = pts[i + 1]
                            val dx = p2.x - p1.x
                            val dy = p2.y - p1.y
                            val len = hypot(dx, dy).coerceAtLeast(1f)
                            val nx = (-dy / len) * (sideStreetGap / 2f)
                            val ny = (dx / len) * (sideStreetGap / 2f)

                            drawLine(
                                brush = fadeBrush,
                                start = Offset(p1.x + nx, p1.y + ny),
                                end = Offset(p2.x + nx, p2.y + ny),
                                strokeWidth = sideStreetStroke,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                brush = fadeBrush,
                                start = Offset(p1.x - nx, p1.y - ny),
                                end = Offset(p2.x - nx, p2.y - ny),
                                strokeWidth = sideStreetStroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Render primary route polyline (12dp solid BeelineWhite with rounded joins)
                    val primaryRouteWidth = BeelineDimens.PrimaryRouteWidth.toPx()
                    state.mapVectors.filter { it.isPrimaryRoute }.forEach { segment ->
                        val routePath = Path()
                        val shiftedPoints = segment.points.mapIndexed { idx, pt ->
                            val xOffset = if (isRerouting) {
                                0.34f + idx * 0.05f
                            } else {
                                pt.x
                            }
                            val yOffset = if (isRerouting && idx == 0) -0.58f else pt.y
                            Offset(
                                x = chevronOrigin.x + xOffset * mapScale,
                                y = chevronOrigin.y + yOffset * mapScale
                            )
                        }
                        if (shiftedPoints.isNotEmpty()) {
                            routePath.moveTo(shiftedPoints.first().x, shiftedPoints.first().y)
                            for (i in 1 until shiftedPoints.size) {
                                routePath.lineTo(shiftedPoints[i].x, shiftedPoints[i].y)
                            }
                            drawPath(
                                path = routePath,
                                color = BeelineColors.BeelineWhite,
                                style = Stroke(
                                    width = primaryRouteWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Mode 2 (`FAST_REROUTING`): 4 evenly spaced white circular breadcrumb dots (4dp radius)
                    if (isRerouting) {
                        val dotRadius = 4.dp.toPx()
                        state.rerouteBreadcrumbs.forEach { crumb ->
                            drawCircle(
                                color = BeelineColors.BeelineWhite,
                                radius = dotRadius,
                                center = Offset(
                                    x = chevronOrigin.x + crumb.x * mapScale,
                                    y = chevronOrigin.y + crumb.y * mapScale
                                )
                            )
                        }
                    }
                }

                // 3. Rider Chevron anchored at (X = 50%, Y = 63%)
                drawRiderChevron(
                    center = chevronOrigin,
                    isAmbient = state.isAmbientMode
                )

                // 4. Left Zone Maneuver Arrow (Mode 1 only; hidden in Mode 2 FAST_REROUTING)
                if (!isRerouting) {
                    drawManeuverArrow(
                        center = Offset(w * 0.25f, h * 0.78f),
                        maneuver = state.nextManeuver
                    )
                }

                // 5. Center Zone Distance Readout (300 m)
                val distText = state.distanceToNextMeters.toString()
                val unitText = if (state.useImperialUnits) "yd" else "m"
                val distLayout = textMeasurer.measure(
                    text = distText,
                    style = BeelineTypography.DistancePrimary30
                )
                val unitLayout = textMeasurer.measure(
                    text = unitText,
                    style = BeelineTypography.UnitSmall15.copy(color = BeelineColors.BeelineWhite)
                )

                val textLeftX = if (isRerouting) {
                    chevronOrigin.x - distLayout.size.width / 2f
                } else {
                    chevronOrigin.x - distLayout.size.width * 0.35f
                }
                val distTopY = h * 0.70f

                drawText(
                    textLayoutResult = distLayout,
                    topLeft = Offset(textLeftX, distTopY)
                )
                drawText(
                    textLayoutResult = unitLayout,
                    topLeft = Offset(textLeftX, distTopY + distLayout.size.height - 4.dp.toPx())
                )

                // 6. Right Split Horizon (X = 80%, Y = 63%): Circular Speed Limit Sign (42dp diameter)
                if (!isRerouting) {
                    drawSpeedLimitBadge(
                        center = Offset(w * 0.80f, splitY),
                        diameterDp = 42f,
                        speedLimitValue = state.speedLimitKmh.let { if (it <= 0) 70 else it },
                        textMeasurer = textMeasurer,
                        approachRatio = state.speedLimitApproachRatio,
                        showApproachArc = true,
                        isAmbient = state.isAmbientMode
                    )
                } else {
                    // Mode 2 FAST_REROUTING: D-shaped right-edge prompt at 3 o'clock with circular detour/rejoin glyph
                    drawSideEdgeDButton(
                        isRightEdge = true,
                        borderColor = BeelineColors.BeelineWhite,
                        glyphType = EdgeGlyphType.DETOUR_REJOIN
                    )
                }

                // 7. Bottom Bezel Progress Arc (52° to 128° along bottom edge)
                val inset = BeelineDimens.CircularSafeZoneInset.toPx()
                val arcRectSize = Size(w - inset * 2f, h - inset * 2f)
                val arcTopLeft = Offset(inset, inset)
                val totalSweep = 128f - 52f

                // 2dp grey background track
                drawArc(
                    color = BeelineColors.BeelineDarkGrey,
                    startAngle = 52f,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcRectSize,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // 4dp white foreground fill bar representing progress along the current maneuver leg
                val progressSweep = totalSweep * state.legProgressRatio.coerceIn(0f, 1f)
                drawArc(
                    color = BeelineColors.BeelineWhite,
                    startAngle = 128f - progressSweep,
                    sweepAngle = progressSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcRectSize,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
