package dev.edgecode.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.markdown.InlineColors
import dev.edgecode.core.markdown.Markdown
import dev.edgecode.core.markdown.MdBlock
import dev.edgecode.core.markdown.MdItem
import dev.edgecode.core.markdown.renderInline

/**
 * Renders assistant and user markdown.
 *
 * Blocks are laid out as a plain column separated by spacing — no card per
 * section — so a transcript reads as one continuous document rather than a
 * stack of containers.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    muted: Boolean = false,
    codeBlockMaxLines: Int = 22,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    val blocks = remember(text) { Markdown.parse(text) }
    val inlineColors = remember(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, edge) {
        InlineColors(
            text = if (muted) scheme.onSurfaceVariant else scheme.onSurface,
            code = if (muted) scheme.onSurfaceVariant else scheme.onSurface,
            codeBackground = edge.subtleFill,
            link = scheme.primary,
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        blocks.forEachIndexed { index, block ->
            val isFirst = index == 0
            when (block) {
                is MdBlock.Heading -> HeadingBlock(block, muted, isFirst)
                is MdBlock.Paragraph -> InlineText(
                    text = block.text,
                    style = style,
                    colors = inlineColors,
                )
                is MdBlock.Code -> CodeBlock(
                    code = block.code,
                    language = block.language,
                    maxLines = codeBlockMaxLines,
                )
                is MdBlock.Bullets -> ListBlock(
                    items = block.items,
                    marker = { null },
                    style = style,
                    inlineColors = inlineColors,
                )
                is MdBlock.Ordered -> ListBlock(
                    items = block.items,
                    marker = { position -> "${block.start + position}." },
                    style = style,
                    inlineColors = inlineColors,
                )
                is MdBlock.Quote -> QuoteBlock(block.text, style, inlineColors)
                is MdBlock.Table -> TableBlock(block, style, inlineColors)
                MdBlock.Rule -> {
                    Spacer(Modifier.height(Space.xxs))
                    HorizontalDivider(color = scheme.outlineVariant)
                }
            }
        }
    }
}

/**
 * Inline markdown with its parsed [AnnotatedString] cached.
 *
 * During streaming the text grows by one delta at a time, but only the final
 * block changes. Without this cache every block rebuilt its spans on every
 * frame; now unchanged blocks reuse their annotation and only the changed
 * text is re-parsed.
 */
@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    colors: InlineColors,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = Color.Unspecified,
) {
    val annotated = remember(text, colors) { renderInline(text, colors) }
    Text(
        text = annotated,
        style = style,
        modifier = modifier,
        maxLines = maxLines,
        color = color,
    )
}

@Composable
private fun HeadingBlock(block: MdBlock.Heading, muted: Boolean, isFirst: Boolean) {
    val base = MaterialTheme.typography
    val style = when (block.level) {
        1 -> base.headlineSmall
        2 -> base.titleLarge
        3 -> base.titleMedium
        else -> base.titleSmall
    }
    Text(
        text = block.text.trim(),
        style = style,
        color = if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.padding(top = if (isFirst) Space.xs else Space.md),
    )
}

@Composable
private fun ListBlock(
    items: List<MdItem>,
    marker: (Int) -> String?,
    style: TextStyle,
    inlineColors: InlineColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        items.forEachIndexed { position, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.width(Space.xl),
                    contentAlignment = Alignment.TopStart,
                ) {
                    if (item.checked != null) {
                        TaskMark(checked = item.checked)
                    } else {
                        Text(
                            text = marker(position) ?: "•",
                            style = style,
                            color = LocalEdgeColors.current.faint,
                        )
                    }
                }
                InlineText(
                    text = item.text,
                    style = style,
                    colors = inlineColors,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TaskMark(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .size(15.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (checked) scheme.primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = Stroke.thin,
                color = if (checked) scheme.primary else scheme.outline,
                shape = MaterialTheme.shapes.extraSmall,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = EdgeIcons.Check,
                contentDescription = "Done",
                tint = scheme.primary,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun QuoteBlock(text: String, style: TextStyle, inlineColors: InlineColors) {
    val scheme = MaterialTheme.colorScheme
    // The accent bar is drawn behind the text rather than measured with
    // IntrinsicSize, which keeps this safe inside a lazy list.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val barWidth = 2.dp.toPx()
                drawRoundRect(
                    color = scheme.outline,
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
            .padding(start = Space.md),
    ) {
        InlineText(
            text = text,
            style = style,
            colors = inlineColors.copy(text = scheme.onSurfaceVariant),
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TableBlock(block: MdBlock.Table, style: TextStyle, inlineColors: InlineColors) {
    val scheme = MaterialTheme.colorScheme
    val columns = maxOf(block.headers.size, block.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return

    val widths = remember(block) {
        (0 until columns).map { column ->
            val header = block.headers.getOrNull(column)?.length ?: 0
            val body = block.rows.maxOfOrNull { it.getOrNull(column)?.length ?: 0 } ?: 0
            (maxOf(header, body).coerceIn(6, 28) * 7.6f).dp
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            block.headers.forEachIndexed { index, header ->
                InlineText(
                    text = header,
                    style = MaterialTheme.typography.labelLarge,
                    colors = inlineColors,
                    maxLines = 2,
                    modifier = Modifier
                        .widthIn(min = widths[index])
                        .padding(end = Space.md, top = Space.xs, bottom = Space.sm),
                )
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        block.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (0 until columns).forEach { index ->
                    InlineText(
                        text = row.getOrNull(index).orEmpty(),
                        style = style,
                        colors = inlineColors,
                        modifier = Modifier
                            .widthIn(min = widths[index])
                            .padding(end = Space.md, top = Space.sm, bottom = Space.xs),
                    )
                }
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
        }
    }
}
