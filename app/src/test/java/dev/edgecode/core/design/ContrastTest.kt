package dev.edgecode.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The palette has to be legible, not merely tasteful.
 *
 * These assertions pin the WCAG contrast of the text tiers that carry real
 * information — timestamps, paths, token counts, section labels — against the
 * surfaces they actually sit on. The tertiary "faint" tier previously measured
 * 3.1:1 in light mode and 4.0:1 in dark, both below the 4.5:1 AA floor for
 * small text.
 */
class ContrastTest {

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun ratio(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun assertReadable(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double = 4.5,
    ) {
        val measured = ratio(foreground, background)
        assertTrue(
            "%s contrast is %.2f:1, below the %.1f:1 floor".format(label, measured, minimum),
            measured >= minimum,
        )
    }

    private fun assertScheme(name: String, scheme: ColorScheme, colors: EdgeColors) {
        assertReadable("$name onSurface/background", scheme.onSurface, scheme.background)
        assertReadable("$name onSurface/surface", scheme.onSurface, scheme.surface)
        assertReadable(
            "$name onSurfaceVariant/background",
            scheme.onSurfaceVariant,
            scheme.background,
        )
        assertReadable("$name faint/background", colors.faint, scheme.background)
        assertReadable("$name faint/surface", colors.faint, scheme.surface)
        assertReadable("$name onPrimary/primary", scheme.onPrimary, scheme.primary)
        assertReadable("$name warning/surface", colors.warning, scheme.surface)
        assertReadable("$name error/surface", scheme.error, scheme.surface)
    }

    @Test
    fun everyThemeFamilyMeetsAaContrast() {
        ThemeFamily.entries.forEach { family ->
            listOf(false, true).forEach { dark ->
                val palette = paletteFor(family, dark)
                val name = "${family.name}-${if (dark) "dark" else "light"}"
                val scheme = palette.scheme()
                assertScheme(name, scheme, palette.colors())
                assertReadable("$name faint/container", palette.faint, palette.surfaceContainer)
                assertReadable("$name faint/surfaceLow", palette.faint, palette.surfaceLow)
            }
        }
    }

    @Test
    fun darkThemeTextMeetsAaContrast() = assertScheme("dark", DarkScheme, DarkColors)

    @Test
    fun lightThemeTextMeetsAaContrast() = assertScheme("light", LightScheme, LightColors)

    /**
     * The tertiary tier must stay distinguishable from the secondary one, or the
     * hierarchy collapses and everything looks like body text.
     */
    @Test
    fun faintRemainsVisuallyDistinctFromOnSurfaceVariant() {
        listOf(
            "dark" to (DarkScheme to DarkColors),
            "light" to (LightScheme to LightColors),
        ).forEach { (name, pair) ->
            val (scheme, colors) = pair
            val secondary = scheme.onSurfaceVariant
            val tertiary = colors.faint
            val delta = kotlin.math.abs(luminance(secondary) - luminance(tertiary))
            assertTrue(
                "$name secondary and tertiary tiers are too close (luminance delta $delta)",
                delta > 0.02,
            )
        }
    }

    /** Metadata sits on the tonal fill in several places; it must read there too. */
    @Test
    fun faintTextIsReadableOnTheSubtleFill() {
        assertReadable("dark faint/subtleFill", DarkColors.faint, DarkColors.subtleFill)
        assertReadable("light faint/subtleFill", LightColors.faint, LightColors.subtleFill)
    }
}
