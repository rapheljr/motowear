package com.motowear.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Official Beeline Logo Kit Header (`beeline-logo-roundel` + `beeline-logo-wordmark` +
 * `classic_edition_lockup_yellow` pill) for the Android Companion Host app.
 */
@Composable
fun BeelineBrandHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF3A3A3C), RoundedCornerShape(14.dp))
            .background(Color(0xFF121212), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Beeline Roundel (`beeline-logo-roundel.png`)
            Canvas(modifier = Modifier.size(44.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = (size.minDimension / 2f) * 0.88f

                drawCircle(
                    color = Color(0xFFFFCC00),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = radius * 0.14f)
                )

                val wingsPath = Path().apply {
                    moveTo(cx - radius * 0.42f, cy - radius * 0.14f)
                    cubicTo(
                        cx - radius * 0.65f, cy - radius * 0.34f,
                        cx - radius * 0.62f, cy - radius * 0.62f,
                        cx - radius * 0.36f, cy - radius * 0.62f
                    )
                    cubicTo(
                        cx - radius * 0.16f, cy - radius * 0.62f,
                        cx - radius * 0.05f, cy - radius * 0.42f,
                        cx, cy - radius * 0.28f
                    )
                    cubicTo(
                        cx + radius * 0.05f, cy - radius * 0.42f,
                        cx + radius * 0.16f, cy - radius * 0.62f,
                        cx + radius * 0.36f, cy - radius * 0.62f
                    )
                    cubicTo(
                        cx + radius * 0.62f, cy - radius * 0.62f,
                        cx + radius * 0.65f, cy - radius * 0.34f,
                        cx + radius * 0.42f, cy - radius * 0.14f
                    )
                }
                drawPath(
                    path = wingsPath,
                    color = Color.White,
                    style = Stroke(
                        width = radius * 0.13f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                val chevronBody = Path().apply {
                    moveTo(cx, cy - radius * 0.36f)
                    lineTo(cx + radius * 0.38f, cy + radius * 0.44f)
                    lineTo(cx, cy + radius * 0.22f)
                    lineTo(cx - radius * 0.38f, cy + radius * 0.44f)
                    close()
                }
                drawPath(path = chevronBody, color = Color(0xFFFFCC00))

                drawLine(
                    color = Color.White,
                    start = Offset(cx - radius * 0.25f, cy + radius * 0.58f),
                    end = Offset(cx + radius * 0.25f, cy + radius * 0.58f),
                    strokeWidth = radius * 0.11f,
                    cap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "BEELINE",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "MotoWear OS • Dual-Ingest Host",
                    color = Color(0xFF7A7A7E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Classic Edition Yellow Lockup Badge (`classic_edition_lockup_yellow`)
        Box(
            modifier = Modifier
                .background(Color(0xFFFFCC00), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "MOTO II",
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }
}
