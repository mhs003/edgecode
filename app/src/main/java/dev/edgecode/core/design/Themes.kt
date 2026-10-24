package dev.edgecode.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The theme families offered by the app.
 *
 * Edge is the default and is deliberately monochrome. The other families keep
 * the same layout, geometry and hierarchy but re-map the neutral and accent
 * roles to a published palette. A family always has a light and a dark variant,
 * so "System" can switch between them without changing the chosen character.
 */
enum class ThemeFamily(
    val label: String,
    val lightVariant: String,
    val darkVariant: String,
) {
    Edge("Edge", "Canvas", "Ink"),
    Catppuccin("Catppuccin", "Latte", "Mocha"),
    TokyoNight("Tokyo Night", "Day", "Night"),
    Gruvbox("Gruvbox", "Light", "Dark"),
    Nord("Nord", "Snow", "Polar"),
    Solarized("Solarized", "Light", "Dark"),
    RosePine("Rosé Pine", "Dawn", "Main"),
    Everforest("Everforest", "Light", "Dark"),
    One("One", "Light", "Dark"),
    ;

    fun variantName(dark: Boolean): String = if (dark) darkVariant else lightVariant

    companion object {
        fun fromStorage(value: String?): ThemeFamily =
            entries.firstOrNull { it.name == value } ?: Edge
    }
}

/**
 * One fully resolved theme.
 *
 * This is the single source for both Material's [ColorScheme] and the app's
 * extended colors. The builder below normalises every information-bearing text
 * tier to the WCAG AA floor, so a palette can be faithful to its published
 * character without becoming unreadable on a small screen.
 */
@Immutable
data class EdgePalette(
    val family: ThemeFamily,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceLow: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val faint: Color,
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val diffAdd: Color,
    val diffRemove: Color,
    val diffAddBg: Color,
    val diffRemoveBg: Color,
    val subtleFill: Color,
) {
    fun scheme(): ColorScheme {
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = onSurfaceVariant,
            onSecondary = onSurface,
            secondaryContainer = surfaceHigh,
            onSecondaryContainer = onSurface,
            tertiary = primary,
            onTertiary = onPrimary,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = onPrimaryContainer,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainer,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = background,
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceHigh,
            surfaceContainerHighest = surfaceHighest,
            outline = outline,
            outlineVariant = outlineVariant,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            inverseSurface = onSurface,
            inverseOnSurface = background,
            inversePrimary = background,
            scrim = Color.Black,
        )
    }

    fun colors(): EdgeColors = EdgeColors(
        success = success,
        warning = warning,
        info = info,
        faint = faint,
        diffAdd = diffAdd,
        diffRemove = diffRemove,
        diffAddBg = diffAddBg,
        diffRemoveBg = diffRemoveBg,
        subtleFill = subtleFill,
        isDark = isDark,
    )

    companion object {
        /** The default monochrome family, exposed for tests and first-frame paint. */
        fun edge(dark: Boolean): EdgePalette = paletteFor(ThemeFamily.Edge, dark)
    }
}

/**
 * The anchors a published palette provides. Surfaces, hairlines and muted text
 * are derived from these so every family keeps the same tonal rhythm.
 */
private data class ThemeSpec(
    val canvas: Color,
    val raised: Color,
    val container: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val red: Color,
    val yellow: Color,
    val green: Color,
    val blue: Color,
)

private fun hex(value: Long): Color = Color(0xFF000000L or value)

private val ThemeSpecs: Map<ThemeFamily, Pair<ThemeSpec, ThemeSpec>> = mapOf(
    ThemeFamily.Edge to (
        ThemeSpec(
            canvas = hex(0x0D0D0D),
            raised = hex(0x141414),
            container = hex(0x181818),
            text = hex(0xF1ECEC),
            muted = hex(0xB7B1B1),
            accent = hex(0xF1ECEC),
            red = hex(0xFF6B63),
            yellow = hex(0xD9A03F),
            green = hex(0x66B58A),
            blue = hex(0x8EA9C9),
        ) to ThemeSpec(
            canvas = hex(0xF8F6F6),
            raised = hex(0xF2EFEF),
            container = hex(0xECE9E9),
            text = hex(0x211E1E),
            muted = hex(0x656363),
            accent = hex(0x211E1E),
            red = hex(0xBA1A1A),
            yellow = hex(0x8A5D0A),
            green = hex(0x2E7D4F),
            blue = hex(0x2F6BB0),
        )
    ),
    ThemeFamily.Catppuccin to (
        ThemeSpec(
            canvas = hex(0x1E1E2E),
            raised = hex(0x181825),
            container = hex(0x313244),
            text = hex(0xCDD6F4),
            muted = hex(0xA6ADC8),
            accent = hex(0xCBA6F7),
            red = hex(0xF38BA8),
            yellow = hex(0xF9E2AF),
            green = hex(0xA6E3A1),
            blue = hex(0x89B4FA),
        ) to ThemeSpec(
            canvas = hex(0xEFF1F5),
            raised = hex(0xE6E9EF),
            container = hex(0xDCE0E8),
            text = hex(0x4C4F69),
            muted = hex(0x6C6F85),
            accent = hex(0x8839EF),
            red = hex(0xD20F39),
            yellow = hex(0xDF8E1D),
            green = hex(0x40A02B),
            blue = hex(0x1E66F5),
        )
    ),
    ThemeFamily.TokyoNight to (
        ThemeSpec(
            canvas = hex(0x1A1B26),
            raised = hex(0x1F2335),
            container = hex(0x292E42),
            text = hex(0xC0CAF5),
            muted = hex(0xA9B1D6),
            accent = hex(0x7AA2F7),
            red = hex(0xF7768E),
            yellow = hex(0xE0AF68),
            green = hex(0x9ECE6A),
            blue = hex(0x7DCFFF),
        ) to ThemeSpec(
            canvas = hex(0xE6E7ED),
            raised = hex(0xDCDDE3),
            container = hex(0xCFD1D9),
            text = hex(0x3760BF),
            muted = hex(0x6172B0),
            accent = hex(0x2E7DE9),
            red = hex(0xF52A65),
            yellow = hex(0x8C6C3E),
            green = hex(0x587539),
            blue = hex(0x007197),
        )
    ),
    ThemeFamily.Gruvbox to (
        ThemeSpec(
            canvas = hex(0x282828),
            raised = hex(0x32302F),
            container = hex(0x3C3836),
            text = hex(0xEBDBB2),
            muted = hex(0xA89984),
            accent = hex(0xFE8019),
            red = hex(0xFB4934),
            yellow = hex(0xFABD2F),
            green = hex(0xB8BB26),
            blue = hex(0x83A598),
        ) to ThemeSpec(
            canvas = hex(0xFBF1C7),
            raised = hex(0xF2E5BC),
            container = hex(0xEBDBB2),
            text = hex(0x3C3836),
            muted = hex(0x7C6F64),
            accent = hex(0xAF3A03),
            red = hex(0x9D0006),
            yellow = hex(0xB57614),
            green = hex(0x79740E),
            blue = hex(0x076678),
        )
    ),
    ThemeFamily.Nord to (
        ThemeSpec(
            canvas = hex(0x2E3440),
            raised = hex(0x3B4252),
            container = hex(0x434C5E),
            text = hex(0xECEFF4),
            muted = hex(0xD8DEE9),
            accent = hex(0x88C0D0),
            red = hex(0xBF616A),
            yellow = hex(0xEBCB8B),
            green = hex(0xA3BE8C),
            blue = hex(0x81A1C1),
        ) to ThemeSpec(
            canvas = hex(0xECEFF4),
            raised = hex(0xE5E9F0),
            container = hex(0xD8DEE9),
            text = hex(0x2E3440),
            muted = hex(0x4C566A),
            accent = hex(0x5E81AC),
            red = hex(0xBF616A),
            yellow = hex(0xEBCB8B),
            green = hex(0xA3BE8C),
            blue = hex(0x81A1C1),
        )
    ),
    ThemeFamily.Solarized to (
        ThemeSpec(
            canvas = hex(0x002B36),
            raised = hex(0x073642),
            container = hex(0x0B3A46),
            text = hex(0xEEE8D5),
            muted = hex(0x93A1A1),
            accent = hex(0x268BD2),
            red = hex(0xDC322F),
            yellow = hex(0xB58900),
            green = hex(0x859900),
            blue = hex(0x268BD2),
        ) to ThemeSpec(
            canvas = hex(0xFDF6E3),
            raised = hex(0xEEE8D5),
            container = hex(0xE6DFC9),
            text = hex(0x586E75),
            muted = hex(0x93A1A1),
            accent = hex(0x268BD2),
            red = hex(0xDC322F),
            yellow = hex(0xB58900),
            green = hex(0x859900),
            blue = hex(0x268BD2),
        )
    ),
    ThemeFamily.RosePine to (
        ThemeSpec(
            canvas = hex(0x191724),
            raised = hex(0x1F1D2E),
            container = hex(0x26233A),
            text = hex(0xE0DEF4),
            muted = hex(0x908CAA),
            accent = hex(0xC4A7E7),
            red = hex(0xEB6F92),
            yellow = hex(0xF6C177),
            green = hex(0x9CCFD8),
            blue = hex(0x31748F),
        ) to ThemeSpec(
            canvas = hex(0xFAF4ED),
            raised = hex(0xFFFAF3),
            container = hex(0xF2E9E1),
            text = hex(0x464261),
            muted = hex(0x797593),
            accent = hex(0x907AA9),
            red = hex(0xB4637A),
            yellow = hex(0xEA9D34),
            green = hex(0x286983),
            blue = hex(0x56949F),
        )
    ),
    ThemeFamily.Everforest to (
        ThemeSpec(
            canvas = hex(0x2D353B),
            raised = hex(0x343F44),
            container = hex(0x3D484D),
            text = hex(0xD3C6AA),
            muted = hex(0x9DA9A0),
            accent = hex(0xA7C080),
            red = hex(0xE67E80),
            yellow = hex(0xDBBC7F),
            green = hex(0xA7C080),
            blue = hex(0x7FBBB3),
        ) to ThemeSpec(
            canvas = hex(0xFDF6E3),
            raised = hex(0xF4F0D9),
            container = hex(0xEFEBD4),
            text = hex(0x5C6A72),
            muted = hex(0x829181),
            accent = hex(0x8DA101),
            red = hex(0xF85552),
            yellow = hex(0xDFA000),
            green = hex(0x8DA101),
            blue = hex(0x3A94C5),
        )
    ),
    ThemeFamily.One to (
        ThemeSpec(
            canvas = hex(0x282C34),
            raised = hex(0x31353F),
            container = hex(0x393F4A),
            text = hex(0xABB2BF),
            muted = hex(0x848B98),
            accent = hex(0x61AFEF),
            red = hex(0xE86671),
            yellow = hex(0xE5C07B),
            green = hex(0x98C379),
            blue = hex(0x61AFEF),
        ) to ThemeSpec(
            canvas = hex(0xFAFAFA),
            raised = hex(0xF0F0F0),
            container = hex(0xE6E6E6),
            text = hex(0x383A42),
            muted = hex(0x818387),
            accent = hex(0x4078F2),
            red = hex(0xE45649),
            yellow = hex(0x986801),
            green = hex(0x50A14F),
            blue = hex(0x4078F2),
        )
    ),
)

private val PaletteCache: Map<Pair<ThemeFamily, Boolean>, EdgePalette> = buildMap {
    ThemeSpecs.forEach { (family, pair) ->
        put(family to true, buildPalette(family, pair.first, true))
        put(family to false, buildPalette(family, pair.second, false))
    }
}

fun paletteFor(family: ThemeFamily, dark: Boolean): EdgePalette =
    PaletteCache.getValue(family to dark)

private fun buildPalette(family: ThemeFamily, spec: ThemeSpec, dark: Boolean): EdgePalette {
    val canvas = spec.canvas
    val surface = canvas
    val low = spec.raised
    val container = spec.container
    val high = lerp(container, spec.text, if (dark) 0.07f else 0.055f)
    val highest = lerp(container, spec.text, if (dark) 0.12f else 0.10f)

    val variant = ensureContrast(
        lerp(spec.muted, spec.text, 0.28f),
        canvas,
        5.0f,
    )
    var faint = ensureContrast(spec.muted, container, 4.5f)
    if (kotlin.math.abs(faint.luminance() - variant.luminance()) <= 0.02f) {
        faint = ensureContrast(lerp(spec.muted, canvas, 0.18f), container, 4.5f)
    }
    if (kotlin.math.abs(faint.luminance() - variant.luminance()) <= 0.02f) {
        faint = ensureContrast(lerp(faint, if (dark) Color.Black else Color.White, 0.18f), container, 4.5f)
    }

    val primary = ensureContrast(spec.accent, canvas, 4.5f)
    val onPrimary = bestOn(primary)
    val primaryContainer = lerp(canvas, spec.accent, if (dark) 0.18f else 0.10f)
    val error = ensureContrast(spec.red, canvas, 4.5f)
    val warning = ensureContrast(spec.yellow, canvas, 4.5f)
    val success = ensureContrast(spec.green, canvas, 4.5f)
    val info = ensureContrast(spec.blue, canvas, 4.5f)
    val outline = lerp(canvas, spec.muted, if (dark) 0.28f else 0.45f)
    val outlineVariant = lerp(canvas, spec.muted, if (dark) 0.12f else 0.20f)

    return EdgePalette(
        family = family,
        isDark = dark,
        background = canvas,
        surface = surface,
        surfaceLow = low,
        surfaceContainer = container,
        surfaceHigh = high,
        surfaceHighest = highest,
        onSurface = spec.text,
        onSurfaceVariant = variant,
        faint = faint,
        outline = outline,
        outlineVariant = outlineVariant,
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = ensureContrast(spec.text, primaryContainer, 4.5f),
        error = error,
        onError = bestOn(error),
        errorContainer = lerp(canvas, error, if (dark) 0.18f else 0.10f),
        onErrorContainer = ensureContrast(spec.text, lerp(canvas, error, if (dark) 0.18f else 0.10f), 4.5f),
        success = success,
        warning = warning,
        info = info,
        diffAdd = success,
        diffRemove = error,
        diffAddBg = success.copy(alpha = if (dark) 0.16f else 0.12f),
        diffRemoveBg = error.copy(alpha = if (dark) 0.16f else 0.12f),
        subtleFill = container,
    )
}

private fun ensureContrast(fg: Color, bg: Color, minimum: Float): Color {
    if (contrast(fg, bg) >= minimum) return fg
    val target = if (bg.luminance() > 0.45f) Color.Black else Color.White
    var next = fg
    repeat(20) {
        next = lerp(next, target, 0.15f)
        if (contrast(next, bg) >= minimum) return next
    }
    return target
}

private fun bestOn(background: Color): Color {
    val black = contrast(Color.Black, background)
    val white = contrast(Color.White, background)
    return if (black >= white) Color.Black else Color.White
}

private fun contrast(a: Color, b: Color): Float {
    val x = a.luminance()
    val y = b.luminance()
    return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
}
