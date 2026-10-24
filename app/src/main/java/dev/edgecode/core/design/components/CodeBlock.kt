package dev.edgecode.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import dev.edgecode.core.design.Radius
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
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
import dev.edgecode.core.design.Stroke
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
    showWrapToggle: Boolean = false,
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
    var wrapped by remember(language) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val overflows = lines.size > maxLines
    val visible = if (overflows && !expanded) lines.take(maxLines).joinToString("\n") else code
    val highlighted = remember(visible, codeColors) { SyntaxHighlighter.highlight(visible, codeColors) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.md))
            .background(scheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.md, end = Space.xxs, top = Space.xxs, bottom = Space.xxs),
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
            if (showWrapToggle) {
                IconAction(
                    icon = EdgeIcons.Wrap,
                    contentDescription = if (wrapped) "Unwrap lines" else "Wrap lines",
                    onClick = { wrapped = !wrapped },
                    size = Size.controlSm,
                    iconSize = Size.iconSm,
                    tint = if (wrapped) scheme.primary else edge.faint,
                )
            }
            IconAction(
                icon = EdgeIcons.Copy,
                contentDescription = "Copy code",
                onClick = { copy("code", code) },
                size = Size.controlSm,
                iconSize = Size.iconSm,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (wrapped) Modifier else Modifier.horizontalScroll(scroll))
                .padding(horizontal = Space.md, vertical = Space.xs),
        ) {
            Text(
                text = highlighted,
                style = EdgeText.code,
                softWrap = wrapped,
            )
        }

        if (overflows) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.sm),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(Size.controlSm)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.full))
                        .background(scheme.surfaceContainerHigh)
                        .tappable { expanded = !expanded },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (expanded) EdgeIcons.ChevronUp else EdgeIcons.ChevronDown,
                        contentDescription = if (expanded) "Collapse code" else "Show all ${lines.size} lines",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(Size.iconSm),
                    )
                }
            }
        }
    }
}
