package com.motowear.phone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Beeline Brand Color Palette (Expressive Dark Accent Palette)
val BeelineYellow = Color(0xFFFFCC00)
val BeelineYellowOn = Color(0xFF000000)
val BeelineYellowContainer = Color(0xFF332900)
val BeelineYellowOnContainer = Color(0xFFFFE580)

val BeelineDarkBackground = Color(0xFF000000)
val BeelineDarkSurface = Color(0xFF121212)
val BeelineDarkSurfaceContainer = Color(0xFF1C1C1E)
val BeelineDarkSurfaceContainerHigh = Color(0xFF2C2C2E)
val BeelineDarkOutline = Color(0xFF3A3A3C)
val BeelineDarkOutlineVariant = Color(0xFF2C2C2E)
val BeelineDarkOnSurface = Color(0xFFFFFFFF)
val BeelineDarkOnSurfaceVariant = Color(0xFFA1A1A6)

val BeelineErrorRed = Color(0xFFFF3B30)

private val DarkColorScheme = darkColorScheme(
    primary = BeelineYellow,
    onPrimary = BeelineYellowOn,
    primaryContainer = BeelineYellowContainer,
    onPrimaryContainer = BeelineYellowOnContainer,
    secondary = Color(0xFFE5E5EA),
    onSecondary = Color(0xFF000000),
    secondaryContainer = BeelineDarkSurfaceContainerHigh,
    onSecondaryContainer = Color(0xFFE5E5EA),
    tertiary = Color(0xFF64D2FF),
    onTertiary = Color(0xFF000000),
    background = BeelineDarkBackground,
    onBackground = BeelineDarkOnSurface,
    surface = BeelineDarkSurface,
    onSurface = BeelineDarkOnSurface,
    surfaceVariant = BeelineDarkSurfaceContainer,
    onSurfaceVariant = BeelineDarkOnSurfaceVariant,
    surfaceContainer = BeelineDarkSurfaceContainer,
    surfaceContainerHigh = BeelineDarkSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = BeelineDarkOutline,
    outlineVariant = BeelineDarkOutlineVariant,
    error = BeelineErrorRed,
    onError = Color(0xFFFFFFFF)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8A6D00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE580),
    onPrimaryContainer = Color(0xFF281D00),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

val BeelineShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val BeelineTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun BeelinePhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = BeelineShapes,
        typography = BeelineTypography,
        content = content
    )
}
