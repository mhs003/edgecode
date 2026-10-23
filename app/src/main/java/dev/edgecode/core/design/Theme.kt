package dev.edgecode.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

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

private val DarkBackground = Color(0xFF0A0B0C)
private val DarkSurface = Color(0xFF0A0B0C)
private val DarkSurfaceContainerLow = Color(0xFF151619)
private val DarkSurfaceContainer = Color(0xFF26282C)
private val DarkSurfaceContainerHigh = Color(0xFF35383E)
private val DarkOutline = Color(0xFF2A2D31)
private val DarkOutlineVariant = Color(0xFF16181A)
private val DarkOnSurface = Color(0xFFEDEEF0)
private val DarkOnSurfaceVariant = Color(0xFFA6AAB0)

/**
 * The tertiary text tier: timestamps, paths, token counts, section labels.
 * Chosen to clear 4.5:1 against the background *and* against the tonal fill that
 * code blocks and user turns use, rather than merely to look recessive — it
 * carries real information, so it has to be legible.
 */
private val DarkFaint = Color(0xFF8C9198)
private val DarkPrimary = Color(0xFF4FB8AE)
private val DarkOnPrimary = Color(0xFF062220)
private val DarkPrimaryContainer = Color(0xFF16302E)
private val DarkOnPrimaryContainer = Color(0xFFA6E4DD)
private val DarkSecondary = Color(0xFF8A93A2)

private val LightBackground = Color(0xFFFFFFFF)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF7F8F9)
private val LightSurfaceContainer = Color(0xFFF0F1F3)
private val LightSurfaceContainerHigh = Color(0xFFE6E8EB)
private val LightOutline = Color(0xFFE2E4E8)
private val LightOutlineVariant = Color(0xFFEDEEF0)
private val LightOnSurface = Color(0xFF0E1114)
private val LightOnSurfaceVariant = Color(0xFF5B6167)

/** See DarkFaint — held to the same 4.5:1 floor. */
private val LightFaint = Color(0xFF666C73)
private val LightPrimary = Color(0xFF0F6E67)
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

internal val DarkColors = EdgeColors(
    success = Color(0xFF5FB07C),
    warning = Color(0xFFD9A03F),
    info = Color(0xFF6C9EDC),
    faint = DarkFaint,
    diffAdd = Color(0xFF5FB07C),
    diffRemove = Color(0xFFDC6F68),
    diffAddBg = Color(0x1F5FB07C),
    diffRemoveBg = Color(0x1FDC6F68),
    subtleFill = Color(0xFF26282C),
    isDark = true,
)

internal val LightColors = EdgeColors(
    success = Color(0xFF2E7D4F),
    warning = Color(0xFF8A5D0A),
    info = Color(0xFF2F6BB0),
    faint = LightFaint,
    diffAdd = Color(0xFF2E7D4F),
    diffRemove = Color(0xFFB3403A),
    diffAddBg = Color(0x1A2E7D4F),
    diffRemoveBg = Color(0x1AB3403A),
    // Matches the tonal fill for user turns and inline code.
    subtleFill = Color(0xFFF0F1F3),
    isDark = false,
)

val LocalEdgeColors = staticCompositionLocalOf { DarkColors }

internal val DarkScheme = darkColorScheme(
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
    surfaceContainerHighest = DarkSurfaceContainerHigh,
)

internal val LightScheme = lightColorScheme(
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
    surfaceContainerHighest = LightSurfaceContainerHigh,
)

/*
 * Typography
 * ==========
 * Fira Code is the one typeface, declared once in Fonts.kt. Its programming
 * ligatures are enabled through `calt`/`liga`, and tracking is zero because
 * letter spacing breaks the monospace ligature grid. Regular and medium carry
 * the interface; semibold is reserved for the few places hierarchy needs it.
 */
private fun edgeStyle(
    size: Float,
    lineHeight: Float,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = FiraCode,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = LIGATURE_FEATURES,
)

fun monoStyle(
    size: Int = 13,
    lineHeight: Int = 20,
    weight: FontWeight = FontWeight.Normal,
) = edgeStyle(size.toFloat(), lineHeight.toFloat(), weight)

val EdgeTypography = Typography(
    displaySmall = edgeStyle(28f, 34f, FontWeight.SemiBold),
    headlineMedium = edgeStyle(22f, 28f, FontWeight.SemiBold),
    headlineSmall = edgeStyle(19f, 25f, FontWeight.Medium),
    titleLarge = edgeStyle(17f, 23f, FontWeight.Medium),
    titleMedium = edgeStyle(15f, 21f, FontWeight.Medium),
    titleSmall = edgeStyle(13f, 18f, FontWeight.Medium),
    bodyLarge = edgeStyle(16f, 25f),
    bodyMedium = edgeStyle(14f, 21f),
    bodySmall = edgeStyle(12.5f, 18f),
    labelLarge = edgeStyle(14f, 19f, FontWeight.Medium),
    labelMedium = edgeStyle(12f, 16f, FontWeight.Medium),
    labelSmall = edgeStyle(11f, 15f, FontWeight.Medium),
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

    // The window's system-bar appearance is part of the theme, not a separate
    // concern: it must flip with the in-app theme, which is often not the same
    // as the system's night setting. Doing it here means it updates immediately
    // on a theme change and never keeps a stale value from the previous theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
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

/** Unwraps the hosting activity from an arbitrary composition context. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Resolves a theme mode to a concrete light/dark decision outside composition.
 *
 * Used before the first frame — the window background and system-bar appearance
 * both have to be right from the very first draw, when `isSystemInDarkTheme()`
 * is not available.
 */
fun ThemeMode.isDark(context: Context): Boolean = when (this) {
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
    ThemeMode.System ->
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}

enum class ThemeMode { Dark, Light, System }
