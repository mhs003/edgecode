package dev.edgecode.core.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Motion
import dev.edgecode.core.design.Size

/**
 * Copy action with a short confirmation.
 *
 * The glyph cross-fades into a tick with a small scale change, rather than the
 * two states swapping abruptly. The tick keeps the control's footprint, so the
 * surrounding text does not move while the confirmation is visible.
 */
@Composable
fun CopyButton(
    copied: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Size.controlSm,
    iconSize: Dp = Size.iconSm,
    tint: Color = LocalEdgeColors.current.faint,
    copiedTint: Color = LocalEdgeColors.current.success,
    contentDescription: String = "Copy",
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.extraSmall)
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                (
                    fadeIn(tween(Motion.fast)) +
                        scaleIn(initialScale = 0.72f, animationSpec = tween(Motion.fast))
                    ).togetherWith(
                    fadeOut(tween(Motion.fast)) +
                        scaleOut(targetScale = 0.72f, animationSpec = tween(Motion.fast)),
                )
            },
            label = "copy feedback",
        ) { isCopied ->
            Icon(
                imageVector = if (isCopied) EdgeIcons.Check else EdgeIcons.Copy,
                contentDescription = if (isCopied) "Copied" else contentDescription,
                tint = if (isCopied) copiedTint else tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
