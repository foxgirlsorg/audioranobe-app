package com.audioranobe.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioranobe.app.R

/** Design tokens mirroring app/globals.css of the web frontend. */
object Ar {
    val accent = Color(0xFFDE6161)
    val accentHover = Color(0xFFEC7D7D)
    val accentSoft = Color(0x1FDE6161)
    val accentOn = Color(0xFF2B0F09)

    val bg = Color(0xFF161616)
    val surface = Color(0x99141416)
    val surfaceStrong = Color(0xEB101012)
    val surfaceSolid = Color(0xFF1A1A1D)
    val surfaceRaised = Color(0xFF232326)

    val border = Color(0x14FFFFFF)
    val borderStrong = Color(0x33FFFFFF)
    val borderHover = Color(0x66DE6161)

    val text = Color(0xFFE8E8E8)
    val textSecondary = Color(0xFFA6ACB2)
    val textMuted = Color(0xFF7B8087)
    val white = Color(0xFFFFFFFF)

    val ok = Color(0xFF58A878)
    val danger = Color(0xFFC53434)
    val amber = Color(0xFFE0A84A)
    val blue = Color(0xFF5B9BD5)

    val fill04 = Color(0x0AFFFFFF)
    val fill06 = Color(0x0FFFFFFF)
    val fill08 = Color(0x14FFFFFF)

    val radius = 12.dp
    val radiusSm = 8.dp
    val gutter = 16.dp
    val dockHeight = 64.dp
    val dockHeightCompact = 46.dp
    val miniPlayerHeight = 58.dp
    val miniPlayerHeightCompact = 6.dp
}

val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
)

private val scheme: ColorScheme = darkColorScheme(
    primary = Ar.accent,
    onPrimary = Ar.accentOn,
    primaryContainer = Ar.accentSoft,
    onPrimaryContainer = Ar.accentHover,
    secondary = Ar.textSecondary,
    onSecondary = Ar.bg,
    background = Ar.bg,
    onBackground = Ar.text,
    surface = Ar.bg,
    onSurface = Ar.text,
    surfaceVariant = Ar.surfaceSolid,
    onSurfaceVariant = Ar.textSecondary,
    surfaceContainer = Ar.surfaceSolid,
    surfaceContainerHigh = Ar.surfaceRaised,
    surfaceContainerHighest = Ar.surfaceRaised,
    surfaceContainerLow = Ar.surfaceSolid,
    surfaceContainerLowest = Ar.bg,
    outline = Ar.border,
    outlineVariant = Ar.border,
    error = Ar.danger,
    onError = Ar.white,
    tertiary = Ar.ok,
)

private fun base(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Float = 1.45f) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    color = Ar.text,
)

val ArTypography = Typography(
    displayLarge = base(34, FontWeight.Light, 1.2f),
    displayMedium = base(28, FontWeight.Light, 1.2f),
    displaySmall = base(24, FontWeight.Light, 1.25f),
    headlineLarge = base(26, FontWeight.Medium, 1.25f),
    headlineMedium = base(22, FontWeight.Medium, 1.25f),
    headlineSmall = base(19, FontWeight.Medium, 1.3f),
    titleLarge = base(18, FontWeight.SemiBold, 1.3f),
    titleMedium = base(16, FontWeight.SemiBold, 1.35f),
    titleSmall = base(14, FontWeight.SemiBold, 1.35f),
    bodyLarge = base(16, lineHeight = 1.55f),
    bodyMedium = base(15, lineHeight = 1.55f),
    bodySmall = base(13, lineHeight = 1.5f),
    labelLarge = base(13, FontWeight.SemiBold),
    labelMedium = base(12, FontWeight.SemiBold),
    labelSmall = base(11, FontWeight.Bold),
)

val ArShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(Ar.radiusSm),
    medium = RoundedCornerShape(Ar.radius),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun AudioRanobeTheme(content: @Composable () -> Unit) {
    // The site is dark only ("color-scheme: dark"), so the system setting is ignored.
    @Suppress("UNUSED_VARIABLE") val ignored = isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, typography = ArTypography, shapes = ArShapes, content = content)
}
