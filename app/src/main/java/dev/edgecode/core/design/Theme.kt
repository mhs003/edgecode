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
 * The default family is deliberately monochrome: a warm canvas, a small set of
 * tonal fills, and text that carries every hierarchy cue. Optional families
 * replace the neutral and accent anchors while keeping the same semantic roles,
 * so layout and contrast behave identically whichever palette is selected.
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
    /** Tonal fill for code and other inset surfaces. */
    val subtleFill: Color,
    val isDark: Boolean,
)

internal val DarkColors: EdgeColors = EdgePalette.edge(dark = true).colors()
internal val LightColors: EdgeColors = EdgePalette.edge(dark = false).colors()

val LocalEdgeColors = staticCompositionLocalOf { DarkColors }

internal val DarkScheme = EdgePalette.edge(dark = true).scheme()
internal val LightScheme = EdgePalette.edge(dark = false).scheme()

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
    displaySmall = edgeStyle(26f, 32f, FontWeight.SemiBold),
    headlineMedium = edgeStyle(20f, 26f, FontWeight.Medium),
    headlineSmall = edgeStyle(17f, 23f, FontWeight.Medium),
    titleLarge = edgeStyle(17f, 23f, FontWeight.Medium),
    titleMedium = edgeStyle(15f, 21f, FontWeight.Medium),
    titleSmall = edgeStyle(13f, 18f, FontWeight.Medium),
    bodyLarge = edgeStyle(14f, 21f),
    bodyMedium = edgeStyle(13f, 20f),
    bodySmall = edgeStyle(12f, 18f),
    labelLarge = edgeStyle(13f, 18f, FontWeight.Medium),
    labelMedium = edgeStyle(11f, 15f, FontWeight.Medium),
    labelSmall = edgeStyle(10f, 14f, FontWeight.Medium),
)

/** Extended text styles that sit outside the Material scale. */
object EdgeText {
    val chat = monoStyle(size = 14, lineHeight = 22)
    val composer = monoStyle(size = 13, lineHeight = 20)
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
    themeMode: ThemeMode = ThemeMode.System,
    themeFamily: ThemeFamily = ThemeFamily.Edge,
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

    val palette = paletteFor(themeFamily, dark)
    CompositionLocalProvider(LocalEdgeColors provides palette.colors()) {
        MaterialTheme(
            colorScheme = palette.scheme(),
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

enum class ThemeMode { System, Light, Dark }
