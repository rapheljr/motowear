package com.motowear.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
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
import kotlin.math.abs

/**
 * Options & Settings Screen Renderer.
 * Matches official Beeline Moto II Options / Settings menu pixel-for-pixel:
 * - Left 9 o'clock D-button with return/back arrow glyph (`EdgeGlyphType.RETURN_BACK`).
 * - Right 3 o'clock D-button with chevron right glyph (`EdgeGlyphType.CHEVRON_RIGHT`).
 * - Centered vertical rotary wheel menu list (`About`, `Unpair`, `Reset`, `Legal`):
 *   - Active selected item (`state.settingsSelectedIndex`) is anchored at `Y = 48%` in 30sp Bold pure white.
 *   - Adjacent items shift vertically by `(index - selectedIndex) * lineSpacing` with graduated opacity (`70%`, `45%`, `25%`).
 */
@Composable
fun DeviceSettingsScreen(
    state: TelemetryFrameState,
    modifier: Modifier = Modifier,
    selectedIndex: Int = state.settingsSelectedIndex
) {
    val textMeasurer = rememberTextMeasurer()
    val options = listOf("About", "Unpair", "Reset", "Legal")
    val safeSelected = selectedIndex.coerceIn(0, options.lastIndex)

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

            // 3. Centered scrollable Options Menu list
            val anchorCenterY = h * 0.48f
            val lineSpacing = h * 0.13f

            options.forEachIndexed { index, optionName ->
                val distanceFromSelected = abs(index - safeSelected)
                val isSelected = distanceFromSelected == 0
                val fontColor = when (distanceFromSelected) {
                    0 -> BeelineColors.BeelineWhite
                    1 -> Color(0xB3FFFFFF) // 70% opacity white
                    2 -> Color(0x73FFFFFF) // 45% opacity white
                    else -> Color(0x40FFFFFF) // 25% opacity white
                }

                val fontStyle = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = if (isSelected) 30.sp else 25.sp,
                    color = fontColor,
                    textAlign = TextAlign.Center
                )

                val itemLayout = textMeasurer.measure(
                    text = optionName,
                    style = fontStyle
                )

                val itemY = anchorCenterY + (index - safeSelected) * lineSpacing
                if (itemY in (h * 0.12f)..(h * 0.88f)) {
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
}

@Preview(device = "id:wearos_large_round", showBackground = true)
@Composable
private fun PreviewDeviceSettingsScreen() {
    DeviceSettingsScreen(state = TelemetryFrameState())
}
