package dev.edgecode.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.Size

/**
 * The app mark.
 *
 * A terminal prompt inside a quiet tonal circle. It is deliberately geometric
 * rather than illustrative: the interface is a workspace, not a dashboard.
 */
@Composable
fun EdgeMark(
    size: Dp = Size.mark,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surfaceContainer,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = EdgeIcons.Terminal,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
