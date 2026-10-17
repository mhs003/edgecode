package dev.edgecode.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.EdgeText
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.components.IconAction
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.AttachmentKind
import dev.edgecode.core.model.SlashCommand
import dev.edgecode.core.util.loadThumbnail

/**
 * Message input.
 *
 * Three things live here that the user needs constantly without leaving the
 * keyboard: what will answer (model), how (agent), and what you can ask for
 * (slash commands). Putting them in the composer rather than in a settings
 * screen is what makes the app feel like a client for a running session rather
 * than a form.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (String) -> Unit,
    onPickAttachment: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    streaming: Boolean,
    modelLabel: String,
    agentLabel: String,
    onModelClick: () -> Unit,
    onAgentClick: () -> Unit,
    commands: List<SlashCommand>,
    onCommandSelected: (SlashCommand) -> Unit,
    sendOnEnter: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val slashQuery = remember(value) {
        val trimmed = value.trimStart()
        if (trimmed.startsWith("/") && !trimmed.contains(' ')) trimmed.drop(1) else null
    }
    val matches = remember(slashQuery, commands) {
        if (slashQuery == null) emptyList()
        else commands.filter { it.name.startsWith(slashQuery, ignoreCase = true) }.take(6)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 1.dp)
                .background(scheme.outlineVariant),
        )

        AnimatedVisibility(
            visible = matches.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SlashCommandList(
                commands = matches,
                onSelect = onCommandSelected,
            )
        }

        if (attachments.isNotEmpty()) {
            AttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp, max = 180.dp)
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(scheme.surfaceContainer)
                    .border(
                        width = 1.dp,
                        color = if (focused) scheme.primary else scheme.outlineVariant,
                        shape = RoundedCornerShape(Radius.lg),
                    ),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    interactionSource = interaction,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Space.md, top = Space.md, bottom = Space.md)
                        .onPreviewKeyEvent { event ->
                            if (sendOnEnter &&
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.Enter &&
                                !event.isShiftPressed
                            ) {
                                onSend()
                                true
                            } else {
                                false
                            }
                        },
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = "Ask for changes, or type / for commands",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = edge.faint,
                                )
                            }
                            inner()
                        }
                    },
                )

                IconAction(
                    icon = EdgeIcons.Attach,
                    contentDescription = "Add attachment",
                    onClick = onPickAttachment,
                    modifier = Modifier.padding(bottom = Space.xxs),
                )
            }

            SendButton(
                streaming = streaming,
                enabled = value.isNotBlank() || attachments.isNotEmpty(),
                onSend = onSend,
                onStop = onStop,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.lg, end = Space.lg, bottom = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            ComposerChip(
                icon = EdgeIcons.Bolt,
                label = modelLabel,
                onClick = onModelClick,
            )
            ComposerChip(
                icon = EdgeIcons.Sparkle,
                label = agentLabel,
                onClick = onAgentClick,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    streaming -> "Tap stop to interrupt"
                    sendOnEnter -> "Enter to send"
                    else -> "Enter for newline"
                },
                style = MaterialTheme.typography.labelSmall,
                color = edge.faint,
            )
        }
    }
}

@Composable
private fun ComposerChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.full))
            .background(scheme.surfaceContainer)
            .tappable(onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = edge.faint,
            modifier = Modifier.size(Size.iconSm),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        Icon(
            imageVector = EdgeIcons.ChevronUp,
            contentDescription = null,
            tint = edge.faint,
            modifier = Modifier.size(10.dp),
        )
    }
}

@Composable
private fun SendButton(
    streaming: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val active = streaming || enabled
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(if (active) scheme.primary else scheme.surfaceContainerHigh)
            .tappable(enabled = active) { if (streaming) onStop() else onSend() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (streaming) EdgeIcons.Stop else EdgeIcons.Send,
            contentDescription = if (streaming) "Stop" else "Send",
            tint = if (active) scheme.onPrimary else LocalEdgeColors.current.faint,
            modifier = Modifier.size(if (streaming) Size.iconSm else Size.icon),
        )
    }
}

@Composable
private fun SlashCommandList(
    commands: List<SlashCommand>,
    onSelect: (SlashCommand) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow)
            .padding(vertical = Space.xs),
    ) {
        Text(
            text = "COMMANDS",
            style = MaterialTheme.typography.labelSmall,
            color = edge.faint,
            modifier = Modifier.padding(start = Space.lg, top = Space.xs, bottom = Space.xs),
        )
        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
            items(commands, key = { it.name }) { command ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tappable { onSelect(command) }
                        .padding(horizontal = Space.lg, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Text(
                        text = "/${command.name}",
                        style = EdgeText.monoButton,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = command.description.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = edge.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<Attachment>,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        attachments.forEach { attachment ->
            AttachmentThumb(attachment = attachment, onRemove = { onRemove(attachment.id) })
        }
    }
}

@Composable
private fun AttachmentThumb(
    attachment: Attachment,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val bitmap by produceState<ImageBitmap?>(initialValue = null, attachment.uri) {
        value = if (attachment.kind == AttachmentKind.Image && attachment.uri != null) {
            loadThumbnail(context, attachment.uri)
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .size(Size.attachment)
            .clip(MaterialTheme.shapes.small)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.small),
    ) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = attachment.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(min = Size.attachment),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = if (attachment.kind == AttachmentKind.Image) {
                        EdgeIcons.Image
                    } else {
                        EdgeIcons.File
                    },
                    contentDescription = null,
                    tint = edge.faint,
                    modifier = Modifier.size(Size.icon),
                )
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Space.xxs)
                .size(20.dp)
                .clip(RoundedCornerShape(Radius.full))
                .background(scheme.surfaceContainerHighest)
                .tappable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = EdgeIcons.Close,
                contentDescription = "Remove ${attachment.name}",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
