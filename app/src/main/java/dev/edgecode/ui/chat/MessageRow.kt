package dev.edgecode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.components.ErrorNotice
import dev.edgecode.core.design.components.IconAction
import dev.edgecode.core.design.components.MarkdownText
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.AttachmentKind
import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.Part
import dev.edgecode.core.util.rememberCopyAction

/**
 * One turn.
 *
 * A prompt is a compact tonal bubble against the right edge; the agent's reply
 * is plain full-width text on the canvas with nothing drawn around it. That
 * asymmetry is what keeps a long technical transcript readable — the agent's
 * output is the document, your prompts are the annotations.
 */
@Composable
fun MessageRow(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    showReasoning: Boolean = true,
    expandTools: Boolean = false,
) {
    when (message) {
        is ChatMessage.User -> UserMessageRow(message, modifier)
        is ChatMessage.Assistant -> AssistantMessageRow(
            message = message,
            modifier = modifier,
            showReasoning = showReasoning,
            expandTools = expandTools,
        )
    }
}

@Composable
private fun UserMessageRow(message: ChatMessage.User, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.86f),
            horizontalAlignment = Alignment.End,
        ) {
            if (message.attachments.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    modifier = Modifier.padding(bottom = Space.sm),
                ) {
                    message.attachments.forEach { AttachmentBubble(it) }
                }
            }
            if (message.text.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(scheme.surfaceContainer)
                        .padding(horizontal = Space.lg, vertical = Space.md),
                ) {
                    MarkdownText(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageRow(
    message: ChatMessage.Assistant,
    modifier: Modifier = Modifier,
    showReasoning: Boolean,
    expandTools: Boolean,
) {
    val edge = LocalEdgeColors.current
    val copy = rememberCopyAction()
    // Remembered: this walks every part and builds a string. It only changes when
    // the parts change, which during streaming is once per chunk — not on every
    // unrelated recomposition.
    val plainText = remember(message.parts) {
        message.parts.filterIsInstance<Part.Text>().joinToString("\n\n") { it.text }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        message.parts.forEachIndexed { index, part ->
            PartView(
                part = part,
                streaming = message.streaming,
                showReasoning = showReasoning,
                expandTools = expandTools,
                isLast = index == message.parts.lastIndex,
            )
        }

        if (message.error != null) {
            ErrorNotice(message = message.error)
        }

        // Nothing on a settled turn except the one action worth having. No meta
        // footer: the turn's model and cost are session facts, and repeating
        // them under every reply turns the transcript into a log.
        if (!message.streaming && plainText.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    icon = EdgeIcons.Copy,
                    contentDescription = "Copy reply",
                    onClick = { copy("reply", plainText) },
                    size = Size.touchSm,
                    iconSize = Size.iconSm,
                    tint = edge.faint,
                    // Pulled left by half the difference between the touch
                    // target and the glyph, so the icon optically lines up with
                    // the text it belongs to rather than with its own padding.
                    modifier = Modifier.offset(x = -(Size.touchSm - Size.iconSm) / 2),
                )
            }
        }
    }
}

@Composable
private fun AttachmentBubble(attachment: Attachment) {
    val edge = LocalEdgeColors.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(scheme.surfaceContainer)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(
            imageVector = if (attachment.kind == AttachmentKind.Image) {
                EdgeIcons.Image
            } else {
                EdgeIcons.File
            },
            contentDescription = null,
            tint = edge.faint,
            modifier = Modifier.height(Size.iconSm),
        )
        Text(
            text = attachment.name,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
