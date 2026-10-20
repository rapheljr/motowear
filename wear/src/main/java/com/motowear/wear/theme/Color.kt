package com.motowear.wear.theme

import androidx.compose.ui.graphics.Color

/**
 * Section 3.1: Global Design System & Visual Tokens (Beeline Moto 2 Color Palette)
 */
object BeelineColors {
    /** Global canvas background across all 8 modes for zero OLED light bleed. */
    val BeelineBlack = Color(0xFF000000)

    /** Lower 37% split-screen HUD panel on Turn-by-Turn and Rerouting screens. */
    val BeelineHudBg = Color(0xFF121212)

    /** Vertical center road ribbon inside the lower HUD panel. */
    val BeelineRoadStripe = Color(0xFF1E1E1E)

    /** Active route polyline, primary numbers, active button borders, radial speed bars. */
    val BeelineWhite = Color(0xFFFFFFFF)

    /** Units (km, hr, min), odometer cell borders, leading zero (0), secondary icons. */
    val BeelineGrey = Color(0xFF7A7A7E)

    /** Side-street double lines, inactive < edge button border, minor compass ticks. */
    val BeelineDarkGrey = Color(0xFF3A3A3C)

    /** Remaining time (2hr 25m), compass heading (330° NW), summary icons, 6 o'clock confirmation dome. */
    val BeelineAmber = Color(0xFFFFCC00)

    /** Speed limit sign border, 12 o'clock speed limit dot, 12 o'clock North compass pointer. */
    val BeelineRed = Color(0xFFFF3B30)
}
