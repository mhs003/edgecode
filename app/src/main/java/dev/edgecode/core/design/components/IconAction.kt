package dev.edgecode.core.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Size

/**
 * A compact icon control.
 *
 * Touch target and glyph size are separate on purpose: the tappable area stays
 * comfortable while the glyph stays visually quiet next to text.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Size.control,
    iconSize: Dp = Size.iconSm,
    tint: Color = LocalEdgeColors.current.faint,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.extraSmall)
            .tappable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(iconSize),
        )
    }
}
