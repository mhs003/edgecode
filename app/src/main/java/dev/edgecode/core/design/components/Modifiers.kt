package dev.edgecode.core.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import dev.edgecode.core.design.LocalEdgeColors

/**
 * Standard tap behaviour.
 *
 * There is no ripple: a ripple expands beyond small controls and reads as a
 * second, square surface. Pressing paints a short tonal overlay inside the
 * control's own shape instead — the same idea as a hover/pressed background in
 * a web design system, without the platform splash.
 *
 * The caller's `clip` (before this modifier) determines that shape.
 */
fun Modifier.tappable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dark = LocalEdgeColors.current.isDark
    val strength by animateFloatAsState(
        targetValue = if (enabled && pressed) (if (dark) 0.10f else 0.065f) else 0f,
        label = "pressOverlay",
    )
    val overlay = if (dark) Color.White else Color.Black
    this
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
        .drawWithContent {
            drawContent()
            if (strength > 0f) {
                drawRect(overlay.copy(alpha = strength))
            }
        }
}

/**
 * The screen's top inset. Belongs on the app bar, so list content can scroll
 * beneath the status bar rather than being boxed in by it.
 */
fun Modifier.topSystemInset(): Modifier = statusBarsPadding()

/**
 * The screen's bottom inset — the one place it is applied.
 *
 * Union rather than sum: while the keyboard is open the IME inset already
 * extends past the navigation bar, so padding for both pushed the composer up
 * off the bottom of the window and left it floating mid-screen. Anything that
 * needs to sit above the keyboard uses this and nothing else.
 */
@Composable
fun Modifier.bottomSystemInset(): Modifier =
    windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
