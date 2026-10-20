package com.motowear.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme

/**
 * Section 3.2: Typography & Circular Grid Rules
 * Uses tabular numerals (`tnum`) globally so rapidly changing distances and speeds never jitter horizontally.
 */
object BeelineDimens {
    /** 10dp inset margin from the circular screen edge reserved for perimeter rings, compass ticks, speed bars, and edge prompts. */
    val CircularSafeZoneInset: Dp = 10.dp

    /** Horizontal split sits at Y = 0.63 * Height (top 63% rotating 2D vector canvas; bottom 37% telemetry HUD). */
    const val HudSplitRatio: Float = 0.63f

    /** Primary route polyline width (12dp solid BeelineWhite). */
    val PrimaryRouteWidth: Dp = 12.dp

    /** Non-route side street line width (2dp parallel BeelineDarkGrey double-lines). */
    val SideStreetLineWidth: Dp = 2.dp
}

object BeelineTypography {
    private const val TABULAR_FEATURE = "tnum"

    val SpeedHero44 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineWhite
    )

    val WaypointPrimary36 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineWhite
    )

    val DistancePrimary30 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineWhite
    )

    val DurationAmber28 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineAmber
    )

    val TitleBold26 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineWhite
    )

    val UnitSemiBold24 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineGrey
    )

    val MetricBold22 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineWhite
    )

    val MetricAmber22 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineAmber
    )

    val UnitGrey20 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineGrey
    )

    val ClockWhite19 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineWhite
    )

    val LabelWhite18 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineWhite
    )

    val DistanceRemaining17 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineWhite
    )

    val UnitSmall15 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineGrey
    )

    val CardinalAmber15 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        fontFeatureSettings = TABULAR_FEATURE,
        color = BeelineColors.BeelineAmber
    )
}

@Composable
fun MotoWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
}
