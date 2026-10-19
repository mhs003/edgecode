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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.design.components.CircleButton
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.AttachmentKind
import dev.edgecode.core.model.SlashCommand
import dev.edgecode.core.util.loadThumbnail

/** Test tag for the composer container, used by the inset regression tests. */
const val ComposerTestTag = "composer"

/**
 * Message input.
 *
 * A single rounded pill floating clear of the window edges, with the controls
 * that matter on the inside: attach, what will answer (model), how (agent), and
 * send. Keeping those in the composer rather than in a settings screen is what
 * makes the app feel like a client for a running session rather than a form.
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
            .padding(start = Space.md, end = Space.md, top = Space.sm, bottom = Space.md)
            .testTag(ComposerTestTag),
    ) {
        AnimatedVisibility(
            visible = matches.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SlashCommandList(commands = matches, onSelect = onCommandSelected)
        }

        if (attachments.isNotEmpty()) {
            AttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.xl))
                .background(scheme.surfaceContainer),
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
                // Grows with the prompt up to a fixed number of lines, then
                // scrolls inside the pill. Without a cap a pasted block or a
                // run of blank lines pushes the composer over the transcript.
                maxLines = Size.composerMaxLines,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.lg, end = Space.lg, top = Space.lg, bottom = Space.md)
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
                                text = "Ask, or / for commands",
                                style = MaterialTheme.typography.bodyLarge,
                                color = edge.faint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.sm, end = Space.sm, bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Two rows so the send button is the only unweighted element
                // competing for the trailing edge: inside the left group the
                // model chip is the single weighted child, which means it is
                // the one that ellipsizes instead of crushing its neighbours.
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    CircleButton(
                        icon = EdgeIcons.Attach,
                        contentDescription = "Add attachment",
                        onClick = onPickAttachment,
                        size = Size.controlSm,
                        iconSize = 18.dp,
                        fill = scheme.surfaceContainerHigh,
                    )
                    ComposerChip(
                        label = modelLabel,
                        onClick = onModelClick,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    ComposerChip(
                        label = agentLabel,
                        onClick = onAgentClick,
                    )
                }

                Spacer(Modifier.width(Space.xs))

                CircleButton(
                    icon = if (streaming) EdgeIcons.Stop else EdgeIcons.Send,
                    contentDescription = if (streaming) "Stop" else "Send",
                    onClick = { if (streaming) onStop() else onSend() },
                    size = Size.controlSm,
                    iconSize = if (streaming) Size.iconSm else 18.dp,
                    fill = if (streaming || value.isNotBlank() || attachments.isNotEmpty()) {
                        scheme.primary
                    } else {
                        scheme.surfaceContainerHigh
                    },
                    tint = if (streaming || value.isNotBlank() || attachments.isNotEmpty()) {
                        scheme.onPrimary
                    } else {
                        edge.faint
                    },
                    enabled = streaming || value.isNotBlank() || attachments.isNotEmpty(),
                )
            }
        }
    }
}

/**
 * Text-only pill, matching how current clients present the active model. The
 * two chips share a row with the attach and send controls, so an icon in each
 * cost 48dp that the model name needed more than the decoration did.
 */
@Composable
private fun ComposerChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // The pill is 38dp, which is under the 48dp minimum touch target, so the
    // gesture sits on a taller box and the pill is drawn centred inside it.
    // Otherwise a tap just outside the pill — the most natural place to aim —
    // does nothing. The row is already 48dp tall from the circular controls, so
    // this costs no layout.
    Box(
        modifier = modifier
            .heightIn(min = Size.touch)
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = Size.controlSm)
                .clip(RoundedCornerShape(Radius.full))
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = Space.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 132.dp),
            )
        }
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
            .clip(RoundedCornerShape(Radius.lg))
            .background(scheme.surfaceContainer)
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
            .padding(bottom = Space.sm),
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
    val context = LocalContext.current

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
            .clip(RoundedCornerShape(Radius.sm))
            .background(scheme.surfaceContainer),
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
                .background(scheme.surfaceContainerHigh)
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
