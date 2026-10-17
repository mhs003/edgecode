package dev.edgecode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.EdgeText
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.components.ErrorNotice
import dev.edgecode.core.design.components.IconAction
import dev.edgecode.core.design.components.MarkdownText
import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.util.formatCost
import dev.edgecode.core.util.formatTokens
import dev.edgecode.core.util.rememberCopyAction

/**
 * One turn.
 *
 * User turns sit in a tonal block against the right edge so they are instantly
 * distinguishable when scrolling; assistant turns are plain, full-width text.
 * That asymmetry is what keeps a long technical transcript readable — the agent's
 * output is the document, your prompts are the annotations.
 */
@Composable
fun MessageRow(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    showReasoning: Boolean = true,
    expandTools: Boolean = false,
    modelName: (ModelRef?) -> String? = { null },
) {
    when (message) {
        is ChatMessage.User -> UserMessageRow(message, modifier)
        is ChatMessage.Assistant -> AssistantMessageRow(
            message = message,
            modifier = modifier,
            showReasoning = showReasoning,
            expandTools = expandTools,
            modelName = modelName,
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
            modifier = Modifier.fillMaxWidth(0.88f),
            horizontalAlignment = Alignment.End,
        ) {
            if (message.attachments.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    modifier = Modifier.padding(bottom = Space.sm),
                ) {
                    message.attachments.forEach { attachment ->
                        AttachmentBubble(attachment)
                    }
                }
            }
            if (message.text.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.large)
                        .background(scheme.surfaceContainer)
                        .padding(horizontal = Space.md, vertical = Space.md),
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
    modelName: (ModelRef?) -> String?,
) {
    val edge = LocalEdgeColors.current
    val copy = rememberCopyAction()
    val plainText = message.parts
        .filterIsInstance<dev.edgecode.core.model.Part.Text>()
        .joinToString("\n\n") { it.text }

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

        // Meta only once the turn has settled; while streaming it is just noise.
        if (!message.streaming) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                val label = buildString {
                    message.agent?.let { append(it) }
                    modelName(message.model)?.let { model ->
                        if (isNotEmpty()) append(" · ")
                        append(model)
                    }
                    if (message.tokens.total > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${formatTokens(message.tokens.total)} tok")
                    }
                    if (message.cost > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(formatCost(message.cost))
                    }
                }
                if (label.isNotEmpty()) {
                    Text(text = label, style = EdgeText.meta, color = edge.faint)
                    Spacer(Modifier.width(0.dp))
                }
                Spacer(Modifier.weight(1f))
                if (plainText.isNotBlank()) {
                    IconAction(
                        icon = EdgeIcons.Copy,
                        contentDescription = "Copy message",
                        onClick = { copy("message", plainText) },
                        size = dev.edgecode.core.design.Size.touchSm,
                        iconSize = dev.edgecode.core.design.Size.iconSm,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentBubble(attachment: dev.edgecode.core.model.Attachment) {
    val edge = LocalEdgeColors.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(scheme.surfaceContainer)
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        androidx.compose.material3.Icon(
            imageVector = if (attachment.kind == dev.edgecode.core.model.AttachmentKind.Image) {
                EdgeIcons.Image
            } else {
                EdgeIcons.File
            },
            contentDescription = null,
            tint = edge.faint,
            modifier = Modifier.height(dev.edgecode.core.design.Size.iconSm),
        )
        Text(
            text = attachment.name,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
