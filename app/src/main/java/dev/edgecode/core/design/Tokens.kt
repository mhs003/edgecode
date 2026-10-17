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
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 22.dp
    val full: Dp = 1000.dp
}

object Size {
    val topBar: Dp = 56.dp
    val touch: Dp = 44.dp
    val touchSm: Dp = 34.dp
    val icon: Dp = 20.dp
    val iconSm: Dp = 16.dp
    val iconLg: Dp = 24.dp
    val iconXl: Dp = 32.dp
    val statusDot: Dp = 7.dp
    val attachment: Dp = 72.dp
    val composerMaxLines: Int = 8
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
