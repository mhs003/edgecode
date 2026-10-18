package dev.edgecode.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout tokens.
 *
 * Spacing is a 4dp step. Every gap in the app resolves to one of these values so
 * vertical and horizontal rhythm stays consistent across screens rather than
 * being re-guessed per component.
 */
object Space {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    /** Standard screen gutter. */
    val gutter: Dp = 16.dp

    /** Comfortable reading measure for transcript content. */
    val measure: Dp = 720.dp
}

/**
 * Corner radii.
 *
 * Deliberately a small set: a control, a surface, and a container. Nothing
 * becomes a pill unless it genuinely is one (status dot, avatar, chip), which
 * is what keeps the interface from looking like a template.
 */
object Radius {
    val xs: Dp = 6.dp
    val sm: Dp = 10.dp
    val md: Dp = 14.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
    val full: Dp = 1000.dp
}

object Size {
    val topBar: Dp = 56.dp

    /** Standard touch target. Matches the platform's 48dp minimum. */
    val touch: Dp = 48.dp

    /**
     * Dense target for controls inside a list row or a code header, where a full
     * 48dp would visibly bloat the row. Still well above the point where taps
     * start to miss.
     */
    val touchSm: Dp = 44.dp

    val icon: Dp = 20.dp
    val iconSm: Dp = 16.dp
    val iconLg: Dp = 24.dp
    val iconXl: Dp = 32.dp
    val statusDot: Dp = 7.dp
    val attachment: Dp = 72.dp
    val composerMaxLines: Int = 8

    /**
     * Circular control in the floating transcript header. Sized so a full row of
     * them reads as one object rather than three separate buttons.
     */
    val control: Dp = 44.dp

    /** Circular control tucked inside the composer pill. */
    val controlSm: Dp = 38.dp
}

/**
 * Stroke widths. Dividers and component borders are hairlines at every density,
 * so they get named values rather than a literal repeated per component.
 */
object Stroke {
    /** Dividers and resting component borders. */
    val hairline: Dp = 1.dp

    /** Focus rings and inputs that need to read slightly stronger. */
    val thin: Dp = 1.5.dp
}

/**
 * Motion tokens. Durations are short and purposeful; transitions explain state
 * changes rather than decorate them.
 */
object Motion {
    const val fast = 120
    const val base = 200
    const val slow = 320

    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}
