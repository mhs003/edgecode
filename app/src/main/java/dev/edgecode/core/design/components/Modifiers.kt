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
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Standard tap behaviour.
 *
 * The interface deliberately has no ripple: a ripple is a platform default that
 * expands beyond small controls and reads as a second, square surface. Press
 * feedback is a short opacity dip inside the control's own bounds instead.
 */
fun Modifier.tappable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.58f else 1f,
        label = "pressAlpha",
    )
    this
        .graphicsLayer { this.alpha = alpha }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
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
