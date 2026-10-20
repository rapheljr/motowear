package com.motowear.wear.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.motowear.wear.theme.BeelineColors

/**
 * Vector reproductions of the official Beeline Logo Kit (`beeline-logo-roundel`, `beeline-logo-icon`,
 * `classic_edition_lockup_yellow`, and `beeline-logo-wordmark`).
 */
object BeelineBrandLogo {

    /**
     * Draws the official Beeline Roundel (`beeline-logo-roundel` / `beeline-logo-roundel B&W`):
     * Circular ring housing the geometric bee wings, route chevron body, and lower stripe.
     */
    fun DrawScope.drawBeelineRoundel(
        center: Offset,
        radius: Float,
        monochrome: Boolean = false
    ) {
        val primaryColor = if (monochrome) BeelineColors.BeelineWhite else BeelineColors.BeelineAmber
        val secondaryColor = BeelineColors.BeelineWhite
        val ringStroke = (radius * 0.14f).coerceAtLeast(2f)

        // Outer Roundel Ring
        drawCircle(
            color = primaryColor,
            radius = radius,
            center = center,
            style = Stroke(width = ringStroke)
        )

        // Geometric Bee Wings
        val wingsPath = Path().apply {
            moveTo(center.x - radius * 0.42f, center.y - radius * 0.14f)
            cubicTo(
                center.x - radius * 0.65f, center.y - radius * 0.34f,
                center.x - radius * 0.62f, center.y - radius * 0.62f,
                center.x - radius * 0.36f, center.y - radius * 0.62f
            )
            cubicTo(
                center.x - radius * 0.16f, center.y - radius * 0.62f,
                center.x - radius * 0.05f, center.y - radius * 0.42f,
                center.x, center.y - radius * 0.28f
            )
            cubicTo(
                center.x + radius * 0.05f, center.y - radius * 0.42f,
                center.x + radius * 0.16f, center.y - radius * 0.62f,
                center.x + radius * 0.36f, center.y - radius * 0.62f
            )
            cubicTo(
                center.x + radius * 0.62f, center.y - radius * 0.62f,
                center.x + radius * 0.65f, center.y - radius * 0.34f,
                center.x + radius * 0.42f, center.y - radius * 0.14f
            )
        }
        drawPath(
            path = wingsPath,
            color = secondaryColor,
            style = Stroke(
                width = (radius * 0.13f).coerceAtLeast(1.8f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Navigation Chevron Bee Body
        val chevronBody = Path().apply {
            moveTo(center.x, center.y - radius * 0.36f)
            lineTo(center.x + radius * 0.38f, center.y + radius * 0.44f)
            lineTo(center.x, center.y + radius * 0.22f)
            lineTo(center.x - radius * 0.38f, center.y + radius * 0.44f)
            close()
        }
        drawPath(
            path = chevronBody,
            color = primaryColor
        )

        // Lower Abdomen Stripe
        drawLine(
            color = secondaryColor,
            start = Offset(center.x - radius * 0.25f, center.y + radius * 0.58f),
            end = Offset(center.x + radius * 0.25f, center.y + radius * 0.58f),
            strokeWidth = (radius * 0.11f).coerceAtLeast(1.5f),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Cold-boot Beeline Moto 2 Splash Screen (`classic_edition_lockup_yellow` style)
 * displayed briefly on initial launch before transitioning into `QUICK_START`.
 */
@Composable
fun BeelineBootSplashOverlay(
    diameter: Dp = 74.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BeelineColors.BeelineBlack),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val r = (size.minDimension / 2f) * 0.88f
            BeelineBrandLogo.apply {
                drawBeelineRoundel(
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = r,
                    monochrome = false
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "BEELINE",
            color = BeelineColors.BeelineWhite,
            fontSize = 18.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 3.sp
        )
        Text(
            text = "MOTO II",
            color = BeelineColors.BeelineAmber,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}
