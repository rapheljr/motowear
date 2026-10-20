package com.motowear.wear.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motowear.telemetry.ManeuverKind
import com.motowear.wear.theme.BeelineColors

/**
 * Reusable pixel-accurate Beeline Moto 2 Canvas primitives shared across the 8 Ride Modes.
 */
object BeelineCanvasPrimitives {

    /**
     * Draws the Rider Position Chevron at `(centerX, splitY)` with a white fill and a 5dp black outer stroke
     * (or hollow outline in Ambient Mode).
     */
    fun DrawScope.drawRiderChevron(
        center: Offset,
        isAmbient: Boolean = false
    ) {
        val widthPx = 28.dp.toPx()
        val heightPx = 30.dp.toPx()
        val path = Path().apply {
            moveTo(center.x, center.y - heightPx * 0.55f)
            lineTo(center.x + widthPx * 0.5f, center.y + heightPx * 0.45f)
            lineTo(center.x, center.y + heightPx * 0.18f)
            lineTo(center.x - widthPx * 0.5f, center.y + heightPx * 0.45f)
            close()
        }

        if (!isAmbient) {
            // 5dp black outline stroke to separate chevron cleanly from underlying white route
            drawPath(
                path = path,
                color = BeelineColors.BeelineBlack,
                style = Stroke(
                    width = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = path,
                color = BeelineColors.BeelineWhite
            )
        } else {
            drawPath(
                path = path,
                color = BeelineColors.BeelineWhite,
                style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
            )
        }
    }

    /**
     * Draws the circular Speed Limit Sign (`SpeedLimitBadge`):
     * White fill, BeelineRed border, bold black numerals, and optional concentric outer white approach arc.
     * Converts to hollow vector outline in Ambient Mode (Section 6.2).
     */
    fun DrawScope.drawSpeedLimitBadge(
        center: Offset,
        diameterDp: Float,
        speedLimitValue: Int,
        textMeasurer: TextMeasurer,
        approachRatio: Float = 0f,
        showApproachArc: Boolean = false,
        isAmbient: Boolean = false
    ) {
        val radius = (diameterDp / 2f).dp.toPx()
        val borderStroke = 4.5.dp.toPx()

        if (showApproachArc && approachRatio > 0f) {
            val outerArcRadius = radius + 5.dp.toPx()
            val sweep = 110f * approachRatio.coerceIn(0f, 1f)
            drawArc(
                color = BeelineColors.BeelineWhite,
                startAngle = 115f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - outerArcRadius, center.y - outerArcRadius),
                size = Size(outerArcRadius * 2f, outerArcRadius * 2f),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        if (!isAmbient) {
            drawCircle(
                color = BeelineColors.BeelineWhite,
                radius = radius,
                center = center
            )
            drawCircle(
                color = BeelineColors.BeelineRed,
                radius = radius - borderStroke / 2f,
                center = center,
                style = Stroke(width = borderStroke)
            )
        } else {
            drawCircle(
                color = BeelineColors.BeelineRed,
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        val fontSizeSp = if (speedLimitValue >= 100) 15.sp else 17.sp
        val textLayout = textMeasurer.measure(
            text = speedLimitValue.toString(),
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSizeSp,
                fontFeatureSettings = "tnum",
                color = if (isAmbient) BeelineColors.BeelineWhite else BeelineColors.BeelineBlack
            )
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(
                center.x - textLayout.size.width / 2f,
                center.y - textLayout.size.height / 2f
            )
        )
    }

    /**
     * Draws the 6 o'clock BeelineAmber (#FFCC00) D-shaped confirmation dome with a 2.8dp black checkmark
     * (used on Waypoint Skipping and Journey Summary screens).
     */
    fun DrawScope.drawBottomConfirmDome(
        canvasWidth: Float,
        canvasHeight: Float,
        isAmbient: Boolean = false
    ) {
        val domeWidth = 42.dp.toPx()
        val domeHeight = 34.dp.toPx()
        val left = (canvasWidth - domeWidth) / 2f
        val top = canvasHeight - domeHeight

        val domePath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, left + domeWidth, top + domeHeight + 10.dp.toPx()),
                    topLeft = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    topRight = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    bottomLeft = CornerRadius.Zero,
                    bottomRight = CornerRadius.Zero
                )
            )
        }

        if (!isAmbient) {
            drawPath(path = domePath, color = BeelineColors.BeelineAmber)
        } else {
            drawPath(
                path = domePath,
                color = BeelineColors.BeelineAmber,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Checkmark glyph
        val cx = canvasWidth / 2f
        val cy = top + domeHeight * 0.52f
        val checkPath = Path().apply {
            moveTo(cx - 7.dp.toPx(), cy + 0.5.dp.toPx())
            lineTo(cx - 2.dp.toPx(), cy + 5.5.dp.toPx())
            lineTo(cx + 8.dp.toPx(), cy - 4.5.dp.toPx())
        }
        drawPath(
            path = checkPath,
            color = if (isAmbient) BeelineColors.BeelineAmber else BeelineColors.BeelineBlack,
            style = Stroke(
                width = 2.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    /**
     * Draws a left (9 o'clock) or right (3 o'clock) D-shaped edge button prompt (`<`, `>`, or reroute icon).
     */
    fun DrawScope.drawSideEdgeDButton(
        isRightEdge: Boolean,
        borderColor: Color,
        glyphType: EdgeGlyphType
    ) {
        val btnWidth = 28.dp.toPx()
        val btnHeight = 34.dp.toPx()
        val cy = size.height / 2f
        val top = cy - btnHeight / 2f

        val rect = if (isRightEdge) {
            Rect(size.width - btnWidth, top, size.width + 8.dp.toPx(), top + btnHeight)
        } else {
            Rect(-8.dp.toPx(), top, btnWidth, top + btnHeight)
        }

        val radius = CornerRadius(17.dp.toPx(), 17.dp.toPx())
        val roundRect = if (isRightEdge) {
            RoundRect(
                rect = rect,
                topLeft = radius,
                bottomLeft = radius,
                topRight = CornerRadius.Zero,
                bottomRight = CornerRadius.Zero
            )
        } else {
            RoundRect(
                rect = rect,
                topLeft = CornerRadius.Zero,
                bottomLeft = CornerRadius.Zero,
                topRight = radius,
                bottomRight = radius
            )
        }

        val path = Path().apply { addRoundRect(roundRect) }
        drawPath(
            path = path,
            color = borderColor,
            style = Stroke(width = 1.6.dp.toPx())
        )

        val gx = if (isRightEdge) size.width - btnWidth * 0.48f else btnWidth * 0.48f
        when (glyphType) {
            EdgeGlyphType.CHEVRON_RIGHT -> {
                val chevron = Path().apply {
                    moveTo(gx - 3.dp.toPx(), cy - 5.dp.toPx())
                    lineTo(gx + 3.dp.toPx(), cy)
                    lineTo(gx - 3.dp.toPx(), cy + 5.dp.toPx())
                }
                drawPath(
                    path = chevron,
                    color = borderColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            EdgeGlyphType.CHEVRON_LEFT -> {
                val chevron = Path().apply {
                    moveTo(gx + 3.dp.toPx(), cy - 5.dp.toPx())
                    lineTo(gx - 3.dp.toPx(), cy)
                    lineTo(gx + 3.dp.toPx(), cy + 5.dp.toPx())
                }
                drawPath(
                    path = chevron,
                    color = borderColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            EdgeGlyphType.DETOUR_REJOIN -> {
                // Circular detour/rejoin arrow glyph at 3 o'clock on FAST_REROUTING
                val arcR = 5.5.dp.toPx()
                drawArc(
                    color = borderColor,
                    startAngle = 35f,
                    sweepAngle = 275f,
                    useCenter = false,
                    topLeft = Offset(gx - arcR, cy - arcR),
                    size = Size(arcR * 2f, arcR * 2f),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
                val arrowTip = Path().apply {
                    moveTo(gx + arcR - 2.dp.toPx(), cy - 5.dp.toPx())
                    lineTo(gx + arcR + 2.dp.toPx(), cy - 1.dp.toPx())
                    lineTo(gx + arcR - 3.5.dp.toPx(), cy - 0.5.dp.toPx())
                }
                drawPath(
                    path = arrowTip,
                    color = borderColor,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            EdgeGlyphType.RETURN_BACK -> {
                // Curved return/back arrow glyph (matches Image 2 left D-button)
                val backArc = Path().apply {
                    moveTo(gx + 3.dp.toPx(), cy + 4.dp.toPx())
                    lineTo(gx + 3.dp.toPx(), cy - 1.dp.toPx())
                    quadraticTo(
                        gx + 3.dp.toPx(), cy - 5.dp.toPx(),
                        gx - 1.dp.toPx(), cy - 5.dp.toPx()
                    )
                    lineTo(gx - 4.dp.toPx(), cy - 5.dp.toPx())
                }
                drawPath(
                    path = backArc,
                    color = borderColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                val arrowTip = Path().apply {
                    moveTo(gx - 1.dp.toPx(), cy - 8.dp.toPx())
                    lineTo(gx - 5.dp.toPx(), cy - 5.dp.toPx())
                    lineTo(gx - 1.dp.toPx(), cy - 2.dp.toPx())
                }
                drawPath(
                    path = arrowTip,
                    color = borderColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }

    /**
     * Draws the upcoming maneuver vector arrow inside the Left Zone of the Turn-by-Turn HUD.
     */
    fun DrawScope.drawManeuverArrow(
        center: Offset,
        maneuver: ManeuverKind
    ) {
        val w = 34.dp.toPx()
        val h = 34.dp.toPx()
        val strokeWidth = 5.5.dp.toPx()

        val shaftPath = Path()
        val headPath = Path()

        when (maneuver) {
            ManeuverKind.TURN_LEFT, ManeuverKind.SLIGHT_LEFT, ManeuverKind.SHARP_LEFT -> {
                shaftPath.apply {
                    moveTo(center.x + w * 0.22f, center.y + h * 0.42f)
                    lineTo(center.x + w * 0.22f, center.y - h * 0.05f)
                    quadraticTo(
                        center.x + w * 0.22f,
                        center.y - h * 0.28f,
                        center.x - w * 0.05f,
                        center.y - h * 0.28f
                    )
                    lineTo(center.x - w * 0.32f, center.y - h * 0.28f)
                }
                headPath.apply {
                    moveTo(center.x - w * 0.18f, center.y - h * 0.50f)
                    lineTo(center.x - w * 0.45f, center.y - h * 0.28f)
                    lineTo(center.x - w * 0.18f, center.y - h * 0.06f)
                    close()
                }
            }
            else -> {
                // Default 90-degree right turn arrow with rounded corner and triangular arrowhead (matches blk_nav.webp)
                shaftPath.apply {
                    moveTo(center.x - w * 0.22f, center.y + h * 0.42f)
                    lineTo(center.x - w * 0.22f, center.y - h * 0.05f)
                    quadraticTo(
                        center.x - w * 0.22f,
                        center.y - h * 0.28f,
                        center.x + w * 0.05f,
                        center.y - h * 0.28f
                    )
                    lineTo(center.x + w * 0.30f, center.y - h * 0.28f)
                }
                headPath.apply {
                    moveTo(center.x + w * 0.16f, center.y - h * 0.50f)
                    lineTo(center.x + w * 0.46f, center.y - h * 0.28f)
                    lineTo(center.x + w * 0.16f, center.y - h * 0.06f)
                    close()
                }
            }
        }

        drawPath(
            path = shaftPath,
            color = BeelineColors.BeelineWhite,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = headPath,
            color = BeelineColors.BeelineWhite
        )
    }
}

enum class EdgeGlyphType {
    CHEVRON_LEFT,
    CHEVRON_RIGHT,
    DETOUR_REJOIN,
    RETURN_BACK
}
