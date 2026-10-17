package dev.edgecode.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Colour
 * ======
 * A neutral base carries the interface; a single restrained accent marks
 * interactive and selected states. Semantic colours (success/warning/error and
 * diff add/remove) are kept off the accent hue so status never reads as brand.
 *
 * The palette is desaturated on purpose. The interface should still feel
 * designed with the accent removed — accent is emphasis, not identity.
 */

private val DarkBackground = Color(0xFF0F1216)
private val DarkSurface = Color(0xFF161A20)
private val DarkSurfaceContainerLow = Color(0xFF141820)
private val DarkSurfaceContainer = Color(0xFF1B2027)
private val DarkSurfaceContainerHigh = Color(0xFF20262E)
private val DarkOutline = Color(0xFF262C35)
private val DarkOutlineVariant = Color(0xFF1E242C)
private val DarkOnSurface = Color(0xFFE8ECF1)
private val DarkOnSurfaceVariant = Color(0xFF9BA5B4)
private val DarkFaint = Color(0xFF6C7585)
private val DarkPrimary = Color(0xFF4FB8AE)
private val DarkOnPrimary = Color(0xFF04211F)
private val DarkPrimaryContainer = Color(0xFF16302E)
private val DarkOnPrimaryContainer = Color(0xFFA6E4DD)
private val DarkSecondary = Color(0xFF8A93A2)

private val LightBackground = Color(0xFFFAFBFC)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF6F7F9)
private val LightSurfaceContainer = Color(0xFFF1F3F6)
private val LightSurfaceContainerHigh = Color(0xFFE8EBEF)
private val LightOutline = Color(0xFFE3E6EA)
private val LightOutlineVariant = Color(0xFFEDEFF2)
private val LightOnSurface = Color(0xFF14181D)
private val LightOnSurfaceVariant = Color(0xFF565E69)
private val LightFaint = Color(0xFF868E9A)
private val LightPrimary = Color(0xFF116F68)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFD6EDEA)
private val LightOnPrimaryContainer = Color(0xFF042A27)
private val LightSecondary = Color(0xFF5E6671)

/**
 * Colours the Material scheme has no slot for. Kept in one object so they are
 * defined once and consumed through [LocalEdgeColors].
 */
@Immutable
data class EdgeColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val faint: Color,
    val diffAdd: Color,
    val diffRemove: Color,
    val diffAddBg: Color,
    val diffRemoveBg: Color,
    /** Tonal fill for user turns and inline code. */
    val subtleFill: Color,
    val isDark: Boolean,
)

private val DarkColors = EdgeColors(
    success = Color(0xFF5FB07C),
    warning = Color(0xFFD9A03F),
    info = Color(0xFF6C9EDC),
    faint = DarkFaint,
    diffAdd = Color(0xFF5FB07C),
    diffRemove = Color(0xFFDC6F68),
    diffAddBg = Color(0x1F5FB07C),
    diffRemoveBg = Color(0x1FDC6F68),
    subtleFill = Color(0xFF1E242C),
    isDark = true,
)

private val LightColors = EdgeColors(
    success = Color(0xFF2E7D4F),
    warning = Color(0xFF8A5D0A),
    info = Color(0xFF2F6BB0),
    faint = LightFaint,
    diffAdd = Color(0xFF2E7D4F),
    diffRemove = Color(0xFFB3403A),
    diffAddBg = Color(0x1A2E7D4F),
    diffRemoveBg = Color(0x1AB3403A),
    subtleFill = Color(0xFFF1F3F6),
    isDark = false,
)

val LocalEdgeColors = staticCompositionLocalOf { DarkColors }

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnPrimary,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceVariant = DarkSurfaceContainer,
    onError = Color(0xFF2B0E0C),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF252C35),
)

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = Color.White,
    surface = LightSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceVariant = LightSurfaceContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFE1E5EA),
)

/*
 * Typography
 * ==========
 * One platform typeface, one monospace for code. Regular and medium carry the
 * interface; semibold is reserved for the few places hierarchy needs it.
 */
private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

fun monoStyle(
    size: Int = 13,
    lineHeight: Int = 20,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = Mono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)

val EdgeTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.1).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp,
    ),
)

/** Extended text styles that sit outside the Material scale. */
object EdgeText {
    val code = monoStyle()
    val codeSmall = monoStyle(size = 12, lineHeight = 18)
    val meta = monoStyle(size = 12, lineHeight = 16)
    val monoButton = monoStyle(size = 13, lineHeight = 18, weight = FontWeight.Medium)
}

/**
 * Shape scale, wired from [Radius] so component shapes stay systematic. A
 * control, a surface and a container — nothing is a pill by default.
 */
val EdgeShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

@Composable
fun EdgeTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalEdgeColors provides if (dark) DarkColors else LightColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = EdgeTypography,
            shapes = EdgeShapes,
            content = content,
        )
    }
}

enum class ThemeMode { Dark, Light, System }
