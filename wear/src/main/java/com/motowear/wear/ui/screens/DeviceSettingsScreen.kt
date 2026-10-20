package com.motowear.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.motowear.telemetry.TelemetryFrameState
import com.motowear.wear.theme.BeelineColors
import com.motowear.wear.ui.components.BeelineCanvasPrimitives.drawSideEdgeDButton
import com.motowear.wear.ui.components.EdgeGlyphType

/**
 * Options & Settings Screen Renderer.
 * Matches official Beeline Moto II Options / Settings menu pixel-for-pixel (Image 2):
 * - Left 9 o'clock D-button with return/back arrow glyph (`EdgeGlyphType.RETURN_BACK`).
 * - Right 3 o'clock D-button with chevron right glyph (`EdgeGlyphType.CHEVRON_RIGHT`).
 * - Centered vertical menu list:
 *   - Active selected item highlighted in 30sp ExtraBold pure white (`About`).
 *   - Unselected items in smooth gradient opacity (`Unpair` at 70%, `Reset` at 45%, `Legal` at 25%).
 */
@Composable
fun DeviceSettingsScreen(
    state: TelemetryFrameState,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0
) {
    val textMeasurer = rememberTextMeasurer()
    val options = listOf("About", "Unpair", "Reset", "Legal")

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
            // 1. Left D-button (9 o'clock) with Return/Back arrow glyph
            drawSideEdgeDButton(
                isRightEdge = false,
                borderColor = BeelineColors.BeelineWhite,
                glyphType = EdgeGlyphType.RETURN_BACK
            )

            // 2. Right D-button (3 o'clock) with Chevron Right glyph
            drawSideEdgeDButton(
                isRightEdge = true,
                borderColor = BeelineColors.BeelineWhite,
                glyphType = EdgeGlyphType.CHEVRON_RIGHT
            )

            // 3. Centered Options Menu list matching Image 2 pixel-for-pixel
            val startY = h * 0.48f
            val lineSpacing = h * 0.12f

            options.forEachIndexed { index, optionName ->
                val isSelected = index == selectedIndex
                val fontColor = when {
                    isSelected -> BeelineColors.BeelineWhite
                    index == 1 -> Color(0xB3FFFFFF) // 70% opacity white
                    index == 2 -> Color(0x73FFFFFF) // 45% opacity white
                    else -> Color(0x40FFFFFF)       // 25% opacity white
                }

                val fontStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = if (isSelected) 30.sp else 26.sp,
                    color = fontColor,
                    textAlign = TextAlign.Center
                )

                val itemLayout = textMeasurer.measure(
                    text = optionName,
                    style = fontStyle
                )

                val itemY = startY + index * lineSpacing
                drawText(
                    textLayoutResult = itemLayout,
                    topLeft = Offset(
                        cx - itemLayout.size.width / 2f,
                        itemY - itemLayout.size.height / 2f
                    )
                )
            }
        }
    }
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewDeviceSettingsScreen() {
    DeviceSettingsScreen(state = TelemetryFrameState())
}
