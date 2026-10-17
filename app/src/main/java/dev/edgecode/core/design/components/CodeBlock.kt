package dev.edgecode.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.EdgeText
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.markdown.CodeColors
import dev.edgecode.core.markdown.SyntaxHighlighter
import dev.edgecode.core.util.rememberCopyAction

/**
 * A fenced code block.
 *
 * Chrome is one quiet header line — language left, copy right — because the code
 * is the content and the frame should not compete with it. Long blocks collapse
 * instead of pushing the conversation off screen, and lines never wrap: wrapped
 * code is much harder to read than a horizontal scroll.
 */
@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
    maxLines: Int = 22,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val copy = rememberCopyAction()

    val codeColors = remember(scheme.primary, scheme.onSurface, edge) {
        CodeColors(
            plain = scheme.onSurface.copy(alpha = 0.92f),
            keyword = scheme.primary,
            string = edge.success,
            comment = edge.faint,
            number = edge.warning,
        )
    }

    val lines = remember(code) { code.split("\n") }
    var expanded by remember(code) { mutableStateOf(false) }
    val overflows = lines.size > maxLines
    val visible = if (overflows && !expanded) lines.take(maxLines).joinToString("\n") else code
    val highlighted = remember(visible, codeColors) { SyntaxHighlighter.highlight(visible, codeColors) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(edge.subtleFill)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.small),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.md, end = Space.xxs, top = Space.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language?.lowercase() ?: "code",
                style = EdgeText.meta,
                color = edge.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconAction(
                icon = EdgeIcons.Copy,
                contentDescription = "Copy code",
                onClick = { copy("code", code) },
                size = Size.touchSm,
                iconSize = Size.iconSm,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.md, vertical = Space.xs),
        ) {
            Text(
                text = highlighted,
                style = EdgeText.code,
                softWrap = false,
            )
        }

        if (overflows && !expanded) {
            Text(
                text = "Show all ${lines.size} lines",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
                modifier = Modifier
                    .padding(start = Space.md, end = Space.md, bottom = Space.sm, top = Space.xxs)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .tappable { expanded = true },
            )
        }
    }
}
