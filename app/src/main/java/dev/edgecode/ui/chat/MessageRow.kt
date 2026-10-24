package dev.edgecode.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Motion
import dev.edgecode.core.design.EdgeText
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.design.components.CopyButton
import dev.edgecode.core.design.components.ErrorNotice
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
    onOpenSubagent: ((String) -> Unit)? = null,
) {
    when (message) {
        is ChatMessage.User -> UserMessageRow(message, modifier)
        is ChatMessage.Assistant -> AssistantMessageRow(
            message = message,
            modifier = modifier,
            showReasoning = showReasoning,
            expandTools = expandTools,
            onOpenSubagent = onOpenSubagent,
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
                        .clip(
                            RoundedCornerShape(
                                topStart = Radius.bubble,
                                topEnd = Radius.bubble,
                                bottomStart = Radius.bubble,
                                bottomEnd = Radius.xs,
                            ),
                        )
                        .background(scheme.surfaceContainerHigh)
                        .border(
                            Stroke.hairline,
                            scheme.outlineVariant,
                            RoundedCornerShape(
                                topStart = Radius.bubble,
                                topEnd = Radius.bubble,
                                bottomStart = Radius.bubble,
                                bottomEnd = Radius.xs,
                            ),
                        )
                        .padding(horizontal = Space.md + 2.dp, vertical = Space.sm + 2.dp),
                ) {
                    MarkdownText(
                        text = message.text,
                        style = EdgeText.chat,
                        fillWidth = false,
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
    onOpenSubagent: ((String) -> Unit)? = null,
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
                onOpenSubagent = onOpenSubagent,
            )
        }
        if (message.error != null) {
            ErrorNotice(message = message.error)
        }
        // A settled turn keeps one action and the facts about that reply: which
        // model served it, how much it produced, how fast. Cost stays a session
        // figure, and everything else that describes the session belongs in the
        // menu rather than repeated under every turn.
        if (!message.streaming && plainText.isNotBlank()) {
            CopyButton(
                copied = copy.copied,
                contentDescription = "Copy reply",
                onClick = { copy("reply", plainText) },
                // Pulled left by half the difference between the touch target
                // and the glyph, so the icon optically lines up with the text
                // it belongs to rather than with its own padding.
                modifier = Modifier.offset(x = -(Size.controlSm - Size.iconSm) / 2),
            )
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
/**
 * The agent is working on this turn.
 *
 * Shown from the moment a prompt is sent until the server reports the session
 * idle — the gap between sending and the first token is the longest stretch of
 * silence in the app, because the agent may be loading tools and thinking with
 * nothing yet to show.
 *
 * Three dots breathing in sequence, low contrast and fixed in place. It is the
 * last item in the transcript rather than something floating over it, so it
 * scrolls with the reply and can never cover text.
 */
@Composable
fun WorkingIndicator(
    model: String?,
    modifier: Modifier = Modifier,
) {
    val faint = LocalEdgeColors.current.faint
    val transition = rememberInfiniteTransition(label = "working")

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        repeat(3) { index ->
            // Each dot peaks at a later point in the same cycle, which reads as
            // a wave travelling left to right. The phase lives in the keyframes
            // so all three share one repeating cycle rather than each running
            // its own offset spec.
            val peak = index * 200
            val alpha by transition.animateFloat(
                initialValue = 0.22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.22f at 0
                        0.22f at peak
                        1f at (peak + 180)
                        0.22f at (peak + 420)
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "alpha$index",
            )
            val bob by transition.animateFloat(
                initialValue = 0f,
                targetValue = -3f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0f at 0
                        0f at peak
                        -3f at (peak + 180)
                        0f at (peak + 420)
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "bob$index",
            )
            Box(
                modifier = Modifier
                    .offset(y = bob.dp)
                    .size(Size.statusDot)
                    .alpha(alpha)
                    .background(faint, CircleShape),
            )
        }

        if (!model.isNullOrBlank()) {
            Spacer(Modifier.width(Space.xs))
            Text(
                text = model,
                style = MaterialTheme.typography.labelMedium,
                color = faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}