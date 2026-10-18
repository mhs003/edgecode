package dev.edgecode.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.EdgeText
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.design.components.CodeBlock
import dev.edgecode.core.design.components.MarkdownText
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.DiffFile
import dev.edgecode.core.model.DiffLineKind
import dev.edgecode.core.model.Part
import dev.edgecode.core.model.ToolState
import dev.edgecode.core.model.ToolStatus
import dev.edgecode.core.util.formatBytes
import dev.edgecode.core.util.formatDuration
import dev.edgecode.core.util.shortenPath

/**
 * Renders one transcript part.
 *
 * Tool calls stay one line until asked to open. That is the single most
 * important decision in the transcript: a session can contain dozens of calls,
 * and giving each a card would bury the conversation they are supporting.
 */
@Composable
fun PartView(
    part: Part,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    showReasoning: Boolean = true,
    expandTools: Boolean = false,
    isLast: Boolean = false,
) {
    when (part) {
        is Part.Text -> Row(modifier = modifier) {
            MarkdownText(text = part.text)
            if (streaming && isLast) {
                Spacer(Modifier.width(Space.xxs))
                StreamingCaret()
            }
        }

        is Part.Reasoning -> if (showReasoning) {
            ReasoningBlock(part, modifier = modifier)
        }

        is Part.Tool -> ToolCallRow(
            part = part,
            modifier = modifier,
            defaultExpanded = expandTools,
        )

        is Part.Attachment -> AttachmentInline(part.attachment, modifier)

        is Part.Step -> StepRow(part, modifier)

        is Part.Diff -> DiffBlock(part.files, modifier)

        is Part.Note -> NoteRow(part.text, modifier)
    }
}

/** Blinking block after the last streamed character. */
@Composable
fun StreamingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(560),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretAlpha",
    )
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .size(width = 7.dp, height = 15.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(Radius.xs))
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun ReasoningBlock(part: Part.Reasoning, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    var toggled by remember(part.id) { mutableStateOf<Boolean?>(null) }
    val expanded = toggled ?: part.streaming

    val label = when {
        part.streaming -> "Thinking"
        part.seconds != null -> "Thought for ${part.seconds}s"
        else -> "Thinking"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .tappable { toggled = !expanded }
                .padding(vertical = Space.xs, horizontal = Space.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Icon(
                imageVector = EdgeIcons.Sparkle,
                contentDescription = null,
                tint = edge.faint,
                modifier = Modifier.size(Size.iconSm),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = edge.faint,
            )
            Icon(
                imageVector = if (expanded) EdgeIcons.ChevronUp else EdgeIcons.ChevronDown,
                contentDescription = null,
                tint = edge.faint,
                modifier = Modifier.size(12.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.xs, top = Space.xs)
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.surfaceContainerLow)
                    .padding(Space.md),
            ) {
                MarkdownText(
                    text = part.text,
                    style = MaterialTheme.typography.bodyMedium,
                    muted = true,
                )
                if (part.streaming) {
                    StreamingCaret()
                }
            }
        }
    }
}

@Composable
private fun ToolCallRow(
    part: Part.Tool,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    var expanded by remember(part.id) { mutableStateOf(defaultExpanded) }
    val state = part.state

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .tappable { expanded = !expanded }
                .padding(vertical = Space.sm, horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            ToolStatusGlyph(state.status)

            Icon(
                imageVector = toolIcon(part.toolId),
                contentDescription = null,
                tint = edge.faint,
                modifier = Modifier.size(Size.iconSm),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title ?: part.name,
                    style = EdgeText.codeSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.subtitle != null) {
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.status == ToolStatus.Failed) scheme.error else edge.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            state.durationMs?.let { duration ->
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                )
            }

            Icon(
                imageVector = if (expanded) EdgeIcons.ChevronUp else EdgeIcons.ChevronDown,
                contentDescription = null,
                tint = edge.faint,
                modifier = Modifier.size(12.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(start = Space.lg, bottom = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (state.meta.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        state.meta.forEach { (key, value) ->
                            Text(
                                text = "$key $value",
                                style = MaterialTheme.typography.labelSmall,
                                color = edge.faint,
                            )
                        }
                    }
                }
                state.input?.let { input ->
                    DetailBlock(label = "Input", body = input)
                }
                state.output?.let { output ->
                    DetailBlock(
                        label = if (state.status == ToolStatus.Failed) "Error" else "Output",
                        body = output,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBlock(label: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = LocalEdgeColors.current.faint,
        )
        CodeBlock(code = body, language = null, maxLines = 14)
    }
}

@Composable
private fun ToolStatusGlyph(status: ToolStatus) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    Box(
        modifier = Modifier.size(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            ToolStatus.Pending -> Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(edge.faint.copy(alpha = 0.5f)),
            )

            ToolStatus.Running -> androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.7.dp,
                color = scheme.primary,
            )

            ToolStatus.Completed -> Icon(
                imageVector = EdgeIcons.Check,
                contentDescription = "Completed",
                tint = edge.success,
                modifier = Modifier.size(14.dp),
            )

            ToolStatus.Failed -> Icon(
                imageVector = EdgeIcons.Warning,
                contentDescription = "Failed",
                tint = scheme.error,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun StepRow(part: Part.Step, modifier: Modifier = Modifier) {
    val edge = LocalEdgeColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            modifier = Modifier
                .height(Stroke.hairline)
                .weight(1f)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        if (part.model != null) {
            Text(
                text = part.model,
                style = MaterialTheme.typography.labelSmall,
                color = edge.faint,
            )
        }
        Box(
            modifier = Modifier
                .height(Stroke.hairline)
                .weight(1f)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
private fun NoteRow(text: String, modifier: Modifier = Modifier) {
    val edge = LocalEdgeColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(
            imageVector = EdgeIcons.Info,
            contentDescription = null,
            tint = edge.faint,
            modifier = Modifier.size(Size.iconSm),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = edge.faint,
        )
    }
}

@Composable
private fun DiffBlock(files: List<DiffFile>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(scheme.surfaceContainerLow)
            .border(Stroke.hairline, scheme.outlineVariant, MaterialTheme.shapes.small),
    ) {
        files.forEachIndexed { fileIndex, file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Icon(
                    imageVector = EdgeIcons.Edit,
                    contentDescription = null,
                    tint = edge.faint,
                    modifier = Modifier.size(Size.iconSm),
                )
                Text(
                    text = shortenPath(file.path, 40),
                    style = EdgeText.codeSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (file.additions > 0) {
                    Text(
                        text = "+${file.additions}",
                        style = MaterialTheme.typography.labelSmall,
                        color = edge.diffAdd,
                    )
                }
                if (file.deletions > 0) {
                    Text(
                        text = "−${file.deletions}",
                        style = MaterialTheme.typography.labelSmall,
                        color = edge.diffRemove,
                    )
                }
            }

            if (fileIndex < files.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Stroke.hairline)
                        .background(scheme.outlineVariant),
                )
            }

            DiffLines(file)
        }
    }
}

@Composable
private fun DiffLines(file: DiffFile) {
    val edge = LocalEdgeColors.current
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = Space.xs),
    ) {
        file.hunks.forEach { line ->
            val background = when (line.kind) {
                DiffLineKind.Added -> edge.diffAddBg
                DiffLineKind.Removed -> edge.diffRemoveBg
                else -> Color.Transparent
            }
            val textColor = when (line.kind) {
                DiffLineKind.Added -> edge.diffAdd
                DiffLineKind.Removed -> edge.diffRemove
                DiffLineKind.Hunk -> edge.faint
                DiffLineKind.Context -> scheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background)
                    .padding(horizontal = Space.sm),
            ) {
                Text(
                    text = when (line.kind) {
                        DiffLineKind.Added -> "+"
                        DiffLineKind.Removed -> "−"
                        else -> " "
                    },
                    style = EdgeText.codeSmall,
                    color = textColor,
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = line.text,
                    style = EdgeText.codeSmall,
                    color = textColor,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun AttachmentInline(attachment: Attachment, modifier: Modifier = Modifier) {
    val edge = LocalEdgeColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(
            imageVector = if (attachment.kind == dev.edgecode.core.model.AttachmentKind.Image) {
                EdgeIcons.Image
            } else {
                EdgeIcons.File
            },
            contentDescription = null,
            tint = edge.faint,
            modifier = Modifier.size(Size.icon),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBytes(attachment.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = edge.faint,
            )
        }
    }
}

/** Tool id to glyph. Kept beside the row that uses it rather than in the icon set. */
fun toolIcon(toolId: String): ImageVector = when {
    toolId.contains("grep") || toolId.contains("glob") ||
        toolId.contains("find") || toolId.contains("search") -> EdgeIcons.Search

    toolId.contains("read") || toolId.contains("cat") ||
        toolId.contains("file") -> EdgeIcons.File

    toolId.contains("edit") || toolId.contains("write") ||
        toolId.contains("patch") -> EdgeIcons.Edit

    toolId.contains("bash") || toolId.contains("shell") ||
        toolId.contains("sh") || toolId.contains("terminal") -> EdgeIcons.Terminal

    toolId.contains("todo") -> EdgeIcons.Check

    else -> EdgeIcons.Bolt
}
